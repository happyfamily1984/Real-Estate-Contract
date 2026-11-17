package com.happyfamily.contract.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * 폴더의 계약내용정리를 백그라운드에서 다시 만든다. 사진 없이 저장해 둔 핵심 텍스트만 쓰므로 빠르다.
 * "계약서 내용적용" 버튼(force) 이나 계약서 이동·삭제 뒤(자동) 에 실행된다.
 */
class SummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val folderId = inputData.getString(KEY_FOLDER) ?: return Result.failure()
        if (AuthRepository.currentUser == null) return Result.failure()
        FolderRepository.refreshFolderSummary(applicationContext, folderId, inputData.getBoolean(KEY_FORCE, false))
        return Result.success()
    }

    companion object {
        private const val KEY_FOLDER = "folderId"
        private const val KEY_FORCE = "force"

        fun enqueue(context: Context, folderId: String, force: Boolean, delaySeconds: Long = 0) {
            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(workDataOf(KEY_FOLDER to folderId, KEY_FORCE to force))
                .setInitialDelay(delaySeconds, java.util.concurrent.TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("summary-$folderId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
