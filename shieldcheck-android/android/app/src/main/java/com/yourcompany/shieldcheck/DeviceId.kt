package com.yourcompany.shieldcheck

import android.content.Context
import java.util.UUID

/**
 * One anonymous ID per install, not tied to a name, phone number, or
 * account. MainActivity and ShareReceiverActivity each already had their
 * own copy of this logic before this file existed — left those two as-is
 * rather than touching working code for a pure style cleanup. New call
 * sites (PackageAddedReceiver, ScanWorker) use this shared version.
 */
object DeviceId {
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences("shieldcheck", Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }
}
