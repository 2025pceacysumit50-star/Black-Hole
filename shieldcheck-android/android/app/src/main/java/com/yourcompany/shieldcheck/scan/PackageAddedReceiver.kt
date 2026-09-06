package com.yourcompany.shieldcheck.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yourcompany.shieldcheck.DeviceId
import com.yourcompany.shieldcheck.network.MalwareReportRequest
import com.yourcompany.shieldcheck.network.ScamCheckApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires the moment a new app is installed. This, plus ScanWorker's
 * periodic sweep, is what "automatic" means for a permission-based
 * check: nothing changes continuously between install/update events,
 * so there's nothing meaningful to watch in real time — reacting to
 * the event is the correct design, not a lesser substitute for it.
 *
 * PACKAGE_ADDED for other apps is one of the broadcasts Android still
 * allows manifest-registered (not just runtime-registered) receivers
 * for, even under the API 26+ implicit-broadcast restrictions — there's
 * no other reliable way to learn about an install if your app wasn't
 * already running.
 */
class PackageAddedReceiver : BroadcastReceiver() {
    private val apiClient = ScamCheckApiClient()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val packageName = intent.data?.schemeSpecificPart ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val finding = MalwareScanner(context).scan().find { it.packageName == packageName }
                if (finding != null) {
                    Notifier.notify(context, finding)
                    context.getSharedPreferences("shieldcheck_notified", Context.MODE_PRIVATE)
                        .edit().putBoolean(finding.packageName, true).apply()

                    // Best-effort report to the backend so this finding
                    // counts toward cross-device correlation, not just a
                    // local notification. Silently ignored on failure —
                    // the user's alert above already fired regardless of
                    // whether the network call succeeds.
                    apiClient.reportMalwareFinding(
                        MalwareReportRequest(
                            device_id = DeviceId.get(context),
                            package_name = finding.packageName,
                            reason = finding.reason,
                        ),
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
