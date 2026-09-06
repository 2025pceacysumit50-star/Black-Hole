package com.yourcompany.shieldcheck.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yourcompany.shieldcheck.DeviceId
import com.yourcompany.shieldcheck.network.MalwareReportRequest
import com.yourcompany.shieldcheck.network.ScamCheckApiClient
import java.util.concurrent.TimeUnit

/**
 * Safety net for the install-time receiver: catches permission changes
 * from app updates, and anything installed before ShieldCheck itself
 * was. Tracks what it's already notified about so a still-flagged app
 * doesn't re-notify every single day.
 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val apiClient = ScamCheckApiClient()

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("shieldcheck_notified", Context.MODE_PRIVATE)
        val findings = MalwareScanner(applicationContext).scan()
        for (finding in findings) {
            if (!prefs.getBoolean(finding.packageName, false)) {
                Notifier.notify(applicationContext, finding)
                prefs.edit().putBoolean(finding.packageName, true).apply()

                // Best-effort — see PackageAddedReceiver for why failures
                // here are silently ignored rather than surfaced.
                apiClient.reportMalwareFinding(
                    MalwareReportRequest(
                        device_id = DeviceId.get(applicationContext),
                        package_name = finding.packageName,
                        reason = finding.reason,
                    ),
                )
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScanWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "shieldcheck_periodic_scan",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
