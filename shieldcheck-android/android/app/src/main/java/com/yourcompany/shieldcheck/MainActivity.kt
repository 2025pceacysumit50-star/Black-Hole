package com.yourcompany.shieldcheck

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourcompany.shieldcheck.network.MalwareReportRequest
import com.yourcompany.shieldcheck.network.ScamCheckApiClient
import com.yourcompany.shieldcheck.network.ScamCheckRequest
import com.yourcompany.shieldcheck.scan.MalwareScanner
import com.yourcompany.shieldcheck.scan.ScanFinding
import com.yourcompany.shieldcheck.scan.ScanWorker
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val apiClient = ScamCheckApiClient()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ requires this at runtime, not just in the manifest —
        // without it, flagged-app notifications silently don't show.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // The install-time receiver (manifest-registered) catches new
        // installs immediately. This periodic sweep is the safety net —
        // catches permission changes from updates, and anything already
        // installed before ShieldCheck was.
        ScanWorker.schedule(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        var scanning by remember { mutableStateOf(false) }
        var findings by remember { mutableStateOf<List<ScanFinding>?>(null) }
        var pasteText by remember { mutableStateOf("") }
        var checking by remember { mutableStateOf(false) }
        var verdict by remember { mutableStateOf<String?>(null) }
        var reasons by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
        ) {
            Text("ShieldCheck", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Malware scan and scam check for your phone",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scanning = true
                    scope.launch {
                        val results = MalwareScanner(this@MainActivity).scan()
                        findings = results
                        scanning = false

                        // Best-effort — same reasoning as the automatic
                        // scan paths: this makes a manual scan's findings
                        // count toward cross-device correlation too, not
                        // just whatever the background triggers catch.
                        val deviceId = getOrCreateDeviceId()
                        results.forEach { finding ->
                            apiClient.reportMalwareFinding(
                                MalwareReportRequest(
                                    device_id = deviceId,
                                    package_name = finding.packageName,
                                    reason = finding.reason,
                                ),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (scanning) "Scanning…" else "Scan This Phone")
            }

            findings?.let { list ->
                Spacer(Modifier.height(12.dp))
                if (list.isEmpty()) {
                    Text("✅ No suspicious apps found.")
                } else {
                    Text("⚠️ ${list.size} app(s) worth a closer look:")
                    Spacer(Modifier.height(4.dp))
                    list.forEach { f ->
                        Text(
                            "• ${f.appName}: ${f.reason}",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Paste a suspicious message to check it:")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                placeholder = { Text("Paste text here…") },
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    checking = true
                    scope.launch {
                        val result =
                            apiClient.checkContent(
                                ScamCheckRequest(
                                    device_id = getOrCreateDeviceId(),
                                    type = "text",
                                    content = pasteText,
                                ),
                            )
                        checking = false
                        result
                            .onSuccess {
                                verdict = it.verdict
                                reasons = it.reasons
                            }.onFailure { verdict = "error" }
                    }
                },
                enabled = pasteText.isNotBlank() && !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checking) "Checking…" else "Check for Scam")
            }

            verdict?.let {
                Spacer(Modifier.height(12.dp))
                Text(verdictLabel(it))
                reasons.forEach { r -> Text("• $r") }
            }
        }
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = getSharedPreferences("shieldcheck", MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    private fun verdictLabel(verdict: String?): String =
        when (verdict) {
            "likely_scam" -> "⚠️ Likely a scam"
            "suspicious" -> "⚠️ Suspicious — be careful"
            "likely_safe" -> "✅ Looks safe"
            else -> "Couldn't check right now — try again"
        }
}
