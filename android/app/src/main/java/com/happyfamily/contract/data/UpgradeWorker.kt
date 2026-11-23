package com.happyfamily.contract.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.happyfamily.contract.data.ai.Cooldowns
import java.util.concurrent.TimeUnit

/**
 * AI 를 못 써서 기본 분석(휴대폰 글자 인식)으로 끝난 계약서를, AI 를 다시 쓸 수 있게 되면 자동으로 AI 로 다시 분석한다.
 * 3시간마다, 그리고 앱을 열 때 확인한다.
 */
class UpgradeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (AuthRepository.currentUser == null) return Result.success()
        runCatching { upgradeNow(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "ai-upgrade"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpgradeWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 기본 분석 결과인 계약서 수 */
        suspend fun localCount(): Int =
            ContractRepository.allContracts().count { !it.isRelated && it.status == ContractStatus.DONE && it.analysis?.isLocal == true }

        /** 쓸 수 있는 AI 가 있으면 기본 분석 결과들을 분석 대기열에 넣는다. 넣은 건수를 돌려준다. */
        suspend fun upgradeNow(context: Context, ignoreCooldown: Boolean = false): Int {
            if (!ignoreCooldown && !Cooldowns.anyAvailable(context)) return 0
            // 분석 대기열이 바쁘면 다음 기회에 (같은 계약서를 여러 번 넣지 않도록)
            if (!ignoreCooldown && AnalyzeWorker.queueActive(context)) return 0
            val local = ContractRepository.allContracts()
                .filter { !it.isRelated && it.status == ContractStatus.DONE && it.analysis?.isLocal == true }
                .sortedBy { it.createdAt }
            local.forEach { AnalyzeWorker.enqueue(context, it.id, upgrade = true) }
            return local.size
        }
    }
}
