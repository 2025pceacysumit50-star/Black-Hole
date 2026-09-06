package com.yourcompany.shieldcheck

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourcompany.shieldcheck.network.ScamCheckApiClient
import com.yourcompany.shieldcheck.network.ScamCheckRequest
import java.io.ByteArrayOutputStream
import java.util.UUID

class ShareReceiverActivity : ComponentActivity() {
    private val apiClient = ScamCheckApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (type, content) = extractSharedContent()

        setContent {
            var loading by remember { mutableStateOf(true) }
            var verdict by remember { mutableStateOf<String?>(null) }
            var reasons by remember { mutableStateOf<List<String>>(emptyList()) }

            LaunchedEffect(Unit) {
                val result =
                    apiClient.checkContent(
                        ScamCheckRequest(
                            device_id = getOrCreateDeviceId(),
                            type = type,
                            content = content,
                        ),
                    )
                loading = false
                result
                    .onSuccess {
                        verdict = it.verdict
                        reasons = it.reasons
                    }.onFailure {
                        verdict = "error"
                    }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Scam Check", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        if (loading) {
                            CircularProgressIndicator()
                        } else {
                            Text(verdictLabel(verdict))
                            reasons.forEach { Text("• $it") }
                        }
                    }
                }
            }
        }
    }

    /** Pulls text or an image out of whatever intent triggered this activity. */
    private fun extractSharedContent(): Pair<String, String> =
        when {
            intent.type?.startsWith("image/") == true -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                "image" to (uri?.let { encodeImageToBase64(it) } ?: "")
            }
            else -> {
                "text" to (intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
            }
        }

    private fun encodeImageToBase64(uri: Uri): String {
        val bitmap: Bitmap =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /** Anonymous per-install ID — not tied to a name, phone number, or account. */
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
