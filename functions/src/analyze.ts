import Anthropic from "@anthropic-ai/sdk";
import sharp from "sharp";
import { contractSchema, type ContractAnalysis } from "./schema";

const MODEL = "claude-opus-5-5";

// Claude 비전 입력에 적합한 크기(긴 변 1568px)로 줄여 토큰과 용량을 아낀다.
const MAX_EDGE = 1568;

const SYSTEM_PROMPT = `당신은 한국 부동산 계약서(매매·전세·월세 등)를 읽고 정리하는 전문가입니다.
사용자가 촬영한 계약서 사진(여러 페이지일 수 있음)을 보고 계약 내용을 정확히 추출합니다.

규칙:
- 금액은 모두 원 단위 정수로 변환합니다. (예: "금 삼억원정", "3억", "300,000,000" → 300000000)
- 한글 금액과 숫자 금액이 함께 있으면 둘을 대조하고, 서로 다르면 cautions 에 그 사실을 적습니다.
- 날짜는 YYYY-MM-DD 형식으로 씁니다. 연도가 생략됐으면 문맥상 가장 합리적인 연도를 쓰고 cautions 에 추정했다고 적습니다.
- 사진에서 읽을 수 없거나 계약서에 없는 문자열 항목은 "" 로, 금액은 0 으로 둡니다. 추측해서 채우지 않습니다.
- 손글씨나 흐릿한 글자처럼 확신이 낮은 값은 cautions 에 "확인 필요"로 표시합니다.
- 특약사항은 항목별로 나눠 원문에 가깝게 옮깁니다.
- summary 는 계약 종류, 대상 주소, 핵심 금액, 기간, 잔금일을 담아 3~5문장으로 씁니다.
- cautions 에는 임차인/매수인 입장에서 확인해야 할 점(누락된 서명·날인, 비어 있는 칸, 불리할 수 있는 특약, 금액 불일치, 잔금일과 입주일 차이 등)을 적습니다.`;

async function toJpegBase64(image: Buffer): Promise<string> {
  const resized = await sharp(image)
    .rotate() // EXIF 회전 정보 반영
    .resize({ width: MAX_EDGE, height: MAX_EDGE, fit: "inside", withoutEnlargement: true })
    .jpeg({ quality: 85 })
    .toBuffer();
  return resized.toString("base64");
}

export async function analyzeContractImages(
  images: Buffer[],
  apiKey: string,
): Promise<ContractAnalysis> {
  const client = new Anthropic({ apiKey });

  const content: Anthropic.Beta.BetaContentBlockParam[] = [];
  for (const [i, image] of images.entries()) {
    content.push({ type: "text", text: `[${i + 1} / ${images.length} 페이지]` });
    content.push({
      type: "image",
      source: { type: "base64", media_type: "image/jpeg", data: await toJpegBase64(image) },
    });
  }
  content.push({ type: "text", text: "위 계약서 사진의 내용을 스키마에 맞춰 정리해 주세요." });

  const stream = client.beta.messages.stream({
    model: MODEL,
    max_tokens: 32000,
    betas: ["server-side-fallback-2026-07-01"],
    fallbacks: "default",
    output_config: {
      effort: "high",
      format: { type: "json_schema", schema: contractSchema },
    },
    system: SYSTEM_PROMPT,
    messages: [{ role: "user", content }],
  });
  const message = await stream.finalMessage();

  if (message.stop_reason === "refusal") {
    throw new Error("AI가 이 이미지의 분석을 거절했습니다. 계약서 사진이 맞는지 확인해 주세요.");
  }
  if (message.stop_reason === "max_tokens") {
    throw new Error("분석 결과가 너무 길어 중간에 끊겼습니다. 다시 시도해 주세요.");
  }

  const text = message.content
    .filter((block) => block.type === "text")
    .map((block) => block.text)
    .join("");
  return JSON.parse(text) as ContractAnalysis;
}
