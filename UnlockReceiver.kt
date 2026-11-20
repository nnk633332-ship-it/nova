package com.nova.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val memory = MemoryManager(context)
        if (!memory.isAlwaysOn()) return

        when (intent.action) {
            Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON, Intent.ACTION_BOOT_COMPLETED -> {
                val serviceIntent = Intent(context, NovaForegroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    putExtra("from_unlock", true)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
