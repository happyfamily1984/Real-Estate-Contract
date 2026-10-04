import Anthropic from "@anthropic-ai/sdk";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions";
import { defineSecret } from "firebase-functions/params";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { analyzeContractImages } from "./analyze";

initializeApp();

const anthropicApiKey = defineSecret("ANTHROPIC_API_KEY");

// Firestore 데이터베이스와 같은 리전이어야 한다. (서울: asia-northeast3)
const REGION = "asia-northeast3";

/**
 * 앱이 계약서 사진을 Storage 에 올리고 users/{uid}/contracts/{id} 문서를
 * status = "pending" 으로 쓰면 실행된다. 앱이 꺼져 있어도 서버에서 끝까지 처리한다.
 * 재분석은 앱에서 status 를 다시 "pending" 으로 바꾸면 된다.
 */
export const analyzeContract = onDocumentWritten(
  {
    document: "users/{uid}/contracts/{contractId}",
    region: REGION,
    secrets: [anthropicApiKey],
    timeoutSeconds: 540,
    memory: "1GiB",
  },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists || after.get("status") !== "pending") return;

    const ref = after.ref;
    const uid = event.params.uid;

    // 중복 실행 방지: pending → processing 으로 바꾼 쪽만 처리한다.
    const claimed = await getFirestore().runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (snap.get("status") !== "pending") return false;
      tx.update(ref, {
        status: "processing",
        error: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      return true;
    });
    if (!claimed) return;

    try {
      const paths = (after.get("imagePaths") ?? []) as string[];
      if (paths.length === 0) throw new Error("계약서 사진이 없습니다.");
      if (paths.length > 20) throw new Error("사진은 최대 20장까지 분석할 수 있습니다.");
      if (paths.some((p) => !p.startsWith(`users/${uid}/`))) {
        throw new Error("잘못된 사진 경로입니다.");
      }

      const bucket = getStorage().bucket();
      const images = await Promise.all(
        paths.map(async (p) => (await bucket.file(p).download())[0]),
      );

      const analysis = await analyzeContractImages(images, anthropicApiKey.value());

      await ref.update({
        status: "done",
        analysis,
        title: analysis.propertyAddress || "주소 미확인 계약서",
        contractType: analysis.contractType,
        analyzedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    } catch (err) {
      logger.error("계약서 분석 실패", { uid, contractId: event.params.contractId, err });
      await ref.update({
        status: "error",
        error: errorMessage(err),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  },
);

function errorMessage(err: unknown): string {
  if (err instanceof Anthropic.AuthenticationError) {
    return "서버의 AI API 키가 올바르지 않습니다. 관리자에게 문의하세요.";
  }
  if (err instanceof Anthropic.RateLimitError) {
    return "AI 사용량이 많아 잠시 후 다시 시도해 주세요.";
  }
  if (err instanceof Anthropic.APIError) {
    return `AI 분석 중 오류가 발생했습니다 (${err.status}).`;
  }
  if (err instanceof SyntaxError) {
    return "AI 분석 결과를 읽지 못했습니다. 다시 시도해 주세요.";
  }
  return err instanceof Error ? err.message : String(err);
}
