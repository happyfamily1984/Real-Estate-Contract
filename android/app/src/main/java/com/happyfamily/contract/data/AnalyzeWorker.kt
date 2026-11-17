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
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ServerException
import java.util.concurrent.TimeUnit

/**
 * 계약서 분석을 백그라운드에서 실행한다. 앱 화면을 벗어나도 계속 진행되고,
 * 네트워크가 끊기면 연결될 때까지 기다렸다가 다시 시도한다.
 */
class AnalyzeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        if (AuthRepository.currentUser == null) return Result.success()

        return try {
            ContractRepository.setStatus(id, "processing")
            val pageCount = ContractRepository.contractPageCount(id)
            val pages = ContractRepository.allPageImages(id, pageCount)
            val folders = runCatching { FolderRepository.allFolders() }.getOrDefault(emptyList())
            val result = GeminiAnalyzer.analyze(applicationContext, pages, folders)
            val matchFolderId = result["matchFolderId"] as? String
            ContractRepository.saveAnalysis(id, result - "matchFolderId")

            // 같은 부동산 폴더에 넣고(없으면 새로 만들고) 그 폴더의 계약내용정리를 예약한다.
            // 이 단계가 실패해도 분석 결과는 이미 저장돼 있으므로 계약서를 오류로 만들지 않는다.
            runCatching {
                val contract = ContractRepository.get(id) ?: return Result.success()
                val folderId = FolderRepository.assignFolder(contract, matchFolderId, folders)
                // 여러 장을 한꺼번에 올리면 같은 폴더 정리가 여러 번 예약되는데, 마지막 한 번만 실행된다.
                SummaryWorker.enqueue(applicationContext, folderId, force = false, delaySeconds = 20)
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 사용량 제한(분당 요청 수)·서버 오류·네트워크 오류는 잠시 뒤 다시 시도한다.
            val retryable = e is QuotaExceededException || e is ServerException || e is java.io.IOException
            if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                runCatching { ContractRepository.setStatus(id, "pending") }
                Result.retry()
            } else {
                runCatching { ContractRepository.setStatus(id, "error", GeminiAnalyzer.friendlyMessage(e)) }
                // 실패도 success 로 끝내야 대기열의 다음 계약서가 이어서 분석된다 (오류는 계약서에 기록됨).
                Result.success()
            }
        }
    }

    companion object {
        private const val KEY_ID = "contractId"
        private const val MAX_ATTEMPTS = 6
        private const val QUEUE = "analyze-queue"

        /**
         * 분석 대기열 맨 뒤에 넣는다. 한 번에 하나씩 순서대로 분석하므로
         * 100개를 한꺼번에 올려도 무료 AI 사용량 제한(분당 요청 수)에 덜 걸리고,
         * 같은 부동산 폴더가 중복으로 만들어지지 않는다.
         */
        fun enqueue(context: Context, contractId: String) {
            val request = OneTimeWorkRequestBuilder<AnalyzeWorker>()
                .setInputData(workDataOf(KEY_ID to contractId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
