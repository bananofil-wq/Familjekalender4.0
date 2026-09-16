package se.familjekalender.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class HealthConnectSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
        val familyId = prefs.getString("family_id", null)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val familyCode = prefs.getString("family_code", null)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val familyName = prefs.getString("family_name", "Min familj") ?: "Min familj"

        if (!HealthConnectSync.hasPermissions(context)) return Result.success()
        if (!HealthConnectSync.hasBackgroundPermission(context)) return Result.success()

        val session = FamilySession(familyId, familyName, familyCode)
        return runCatching {
            val imported = HealthConnectSync.syncToCalendar(context, session)
            if (imported > 0) FamilyCalendarWidget.enqueueRefresh(context)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

object HealthConnectSyncScheduler {
    private const val UNIQUE_WORK = "health_connect_running_sync"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
