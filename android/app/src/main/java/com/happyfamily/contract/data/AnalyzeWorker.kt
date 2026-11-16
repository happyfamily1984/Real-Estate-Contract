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
import com.google.firebase.ai.type.ServerException
import java.util.concurrent.TimeUnit

/**
 * 계약서 분석을 백그라운드에서 실행한다. 앱 화면을 벗어나도 계속 진행되고,
 * 네트워크가 끊기면 연결될 때까지 기다렸다가 다시 시도한다.
 */
class AnalyzeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        if (AuthRepository.currentUser == null) return Result.failure()

        return try {
            ContractRepository.setStatus(id, "processing")
            val pageCount = ContractRepository.contractPageCount(id)
            val pages = ContractRepository.allPageImages(id, pageCount)
            val analysis = GeminiAnalyzer.analyze(applicationContext, pages)
            ContractRepository.saveAnalysis(id, analysis)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 재분석으로 교체되거나 취소됨
        } catch (e: Exception) {
            val retryable = e is ServerException || e is java.io.IOException
            if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                runCatching { ContractRepository.setStatus(id, "pending") }
                Result.retry()
            } else {
                runCatching { ContractRepository.setStatus(id, "error", GeminiAnalyzer.friendlyMessage(e)) }
                Result.failure()
            }
        }
    }

    companion object {
        private const val KEY_ID = "contractId"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context, contractId: String) {
            val request = OneTimeWorkRequestBuilder<AnalyzeWorker>()
                .setInputData(workDataOf(KEY_ID to contractId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("analyze-$contractId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
