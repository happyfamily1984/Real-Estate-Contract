package com.happyfamily.contract.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.happyfamily.contract.MainActivity
import com.happyfamily.contract.R
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** 일정 알림을 언제 받을지 (휴대폰 안에 저장) */
object ReminderSettings {
    private const val PREFS = "reminders"

    /** (개월 수, 표시 이름) */
    val OPTIONS = listOf(6 to "6개월 전", 3 to "3개월 전", 1 to "1개월 전")

    fun enabled(context: Context, months: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("m$months", months == 6 || months == 3)

    fun set(context: Context, months: Int, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("m$months", on).apply()

    fun notified(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("sent:$key", false)

    fun markNotified(context: Context, key: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("sent:$key", true).apply()
}

/**
 * 하루 한 번 진행중 계약의 일정을 보고, 정해 둔 시점(6개월·3개월·1개월 전)이 되면 휴대폰 알림을 보낸다.
 * 앱을 열지 않아도 동작한다.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (AuthRepository.currentUser == null) return Result.success()
        runCatching { check(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "schedule-reminders"
        private const val CHANNEL = "schedule"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 알림 보낼 일정 찾기. 이미 더 가까운 시점이 지났으면 먼 시점 알림은 건너뛰고 가까운 것 하나만 보낸다. */
        suspend fun check(context: Context) {
            val events = ScheduleRepository.events(FolderRepository.allFolders(), ContractRepository.allContracts())
            val today = LocalDate.now()
            for (e in events) {
                val date = LocalDate.parse(e.date)
                val due = ReminderSettings.OPTIONS.map { it.first }
                    .filter { ReminderSettings.enabled(context, it) && !today.isBefore(date.minusMonths(it.toLong())) }
                if (due.isEmpty()) continue
                val closest = due.min()
                val key = "${e.key}|$closest"
                if (ReminderSettings.notified(context, key)) continue
                due.forEach { ReminderSettings.markNotified(context, "${e.key}|$it") }
                notify(context, e)
            }
        }

        private fun notify(context: Context, e: ScheduleEvent) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "계약 일정", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${e.folderName} · ${e.type} D-${e.daysLeft}")
                .setContentText("${formatShortDate(e.date)} · ${e.detail}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("${formatShortDate(e.date)} ${e.type}\n${e.detail}\n미리 계획을 세워 두세요 (재계약 제안, 월세 인상, 새 세입자 찾기 등)."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(e.key.hashCode(), notification) }
        }
    }
}
