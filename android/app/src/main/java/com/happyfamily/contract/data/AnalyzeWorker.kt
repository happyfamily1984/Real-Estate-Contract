package com.happyfamily.contract.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.happyfamily.contract.data.ai.AllAiUnavailableException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 계약서 분석을 백그라운드에서 실행한다. AI 를 쓸 수 없으면 휴대폰 안 글자 인식으로 기본 분석을 하고,
 * 나중에 [UpgradeWorker] 가 AI 로 다시 분석한다. 앱 화면을 벗어나도 계속 진행되고,
 * 네트워크가 끊기면 연결될 때까지 기다렸다가 다시 시도한다.
 */
class AnalyzeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        // 백그라운드에서 막 깨어나 로그인 정보가 아직 없을 수 있다 → 잠시 뒤 다시
        if (AuthRepository.currentUser == null) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }

        val upgrade = inputData.getBoolean(KEY_UPGRADE, false)
        return try {
            val contract = ContractRepository.get(id) ?: return Result.success() // 지워진 계약서
            if (upgrade) {
                // 기본 분석(휴대폰)으로 끝난 계약서만 AI 로 다시 분석한다.
                if (contract.status != ContractStatus.DONE || contract.analysis?.isLocal != true) return Result.success()
            } else {
                if (contract.status == ContractStatus.DONE) return Result.success() // 이미 끝남 (중복 예약)
                ContractRepository.setStatus(id, "processing")
            }

            val pages = ContractRepository.allPageImages(id, contract.pageCount)
            val folders = runCatching { FolderRepository.allFolders() }.getOrDefault(emptyList())

            // 1) AI (Firebase 모델들 → 내 API 키들)  2) 모두 안 되면 휴대폰 안 글자 인식
            val result = try {
                withTimeoutOrNull(AI_TIMEOUT_MS) { GeminiAnalyzer.analyze(applicationContext, pages, folders) }
                    ?: throw AllAiUnavailableException(listOf("AI 응답이 ${AI_TIMEOUT_MS / 60_000}분 넘게 오지 않았습니다."))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 한도 소진·서버 오류·메모리 부족 등 AI 쪽 어떤 문제든 → 휴대폰 안 분석으로 대신
                if (upgrade) return Result.success() // 아직 AI 를 못 씀 → 기본 분석 결과 유지
                LocalAnalyzer.analyze(pages)
            }
            val matchFolderId = result["matchFolderId"] as? String
            ContractRepository.saveAnalysis(id, result - "matchFolderId")

            // 같은 부동산 폴더에 넣고(없으면 새로 만들고) 그 폴더의 계약내용정리를 예약한다.
            // 이 단계가 실패해도 분석 결과는 이미 저장돼 있으므로 계약서를 오류로 만들지 않는다.
            runCatching {
                val saved = ContractRepository.get(id) ?: return Result.success()
                if (!saved.confirmed && saved.folderId == null) {
                    // 새로 올린 계약서: 폴더를 바로 만들지 않고 '제안'만 해 둔다.
                    // 사용자가 '새로 올린 계약서 정리'에서 넣기를 누를 때 폴더에 넣는다 (빈 폴더·중복 폴더 방지).
                    ContractRepository.setSuggestion(id, FolderRepository.suggestFolder(saved, matchFolderId, folders))
                } else {
                    val folderId = FolderRepository.assignFolder(saved, matchFolderId, folders)
                    // 여러 장을 한꺼번에 올리면 같은 폴더 정리가 여러 번 예약되는데, 마지막 한 번만 실행된다.
                    SummaryWorker.enqueue(applicationContext, folderId, force = false, delaySeconds = 20)
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 시스템이 작업을 멈춤 (네트워크 끊김 등) → WorkManager 가 나중에 다시 실행한다.
            if (!upgrade) {
                withContext(NonCancellable) { runCatching { ContractRepository.setStatus(id, "pending", "잠시 멈췄다가 다시 분석합니다.") } }
            }
            throw e
        } catch (e: Throwable) {
            // 메모리 부족 같은 Error 까지 모두 잡는다. 여기서 작업이 실패로 끝나면
            // 대기열 뒤의 계약서들이 전부 멈추기 때문에, 오류는 계약서에 적고 항상 다음으로 넘어간다.
            if (upgrade) return Result.success() // 업그레이드 실패는 조용히 넘어간다 (기본 분석 결과 유지)
            val retryable = e is java.io.IOException || e is com.google.firebase.firestore.FirebaseFirestoreException
            if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                val reason = "다시 시도 예정 (${runAttemptCount + 1}/$MAX_ATTEMPTS): ${GeminiAnalyzer.friendlyMessage(e)}"
                runCatching { ContractRepository.setStatus(id, "pending", reason) }
                Result.retry()
            } else {
                val message = if (e is OutOfMemoryError) {
                    "사진이 너무 커서 분석하지 못했습니다. 페이지 수를 줄여 다시 올려 주세요."
                } else {
                    GeminiAnalyzer.friendlyMessage(e)
                }
                runCatching { ContractRepository.setStatus(id, "error", message) }
                Result.success()
            }
        }
    }

    companion object {
        private const val KEY_ID = "contractId"
        private const val KEY_UPGRADE = "upgrade"
        private const val MAX_ATTEMPTS = 6
        private const val QUEUE = "analyze-queue"
        private const val AI_TIMEOUT_MS = 5 * 60_000L

        /**
         * 분석 대기열 맨 뒤에 넣는다. 한 번에 하나씩 순서대로 분석하므로
         * 100개를 한꺼번에 올려도 무료 AI 사용량 제한(분당 요청 수)에 덜 걸리고,
         * 같은 부동산 폴더가 중복으로 만들어지지 않는다.
         */
        fun enqueue(context: Context, contractId: String, upgrade: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<AnalyzeWorker>()
                .setInputData(workDataOf(KEY_ID to contractId, KEY_UPGRADE to upgrade))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** 대기열에 남은(아직 안 끝난) 작업이 있는지 */
        suspend fun queueActive(context: Context): Boolean =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(QUEUE).first()
                .any { !it.state.isFinished }

        /**
         * 대기열이 비었는데 '대기 중'·'분석 중'으로 남은 계약서가 있으면 (앱이 강제로 꺼졌거나
         * 예전 버전에서 대기열이 멈춘 경우) 다시 대기열에 넣는다. 앱을 열 때마다 확인한다.
         */
        suspend fun recoverStuck(context: Context, force: Boolean = false): Int {
            if (!force && queueActive(context)) return 0
            val stuck = ContractRepository.allContracts()
                .filter { it.status == ContractStatus.PENDING || it.status == ContractStatus.PROCESSING }
                .sortedBy { it.createdAt }
            if (force) WorkManager.getInstance(context).cancelUniqueWork(QUEUE)
            stuck.forEach { enqueue(context, it.id) }
            return stuck.size
        }
    }
}
