package com.example.mediremind.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mediremind.alarm.AndroidAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            AndroidAlarmScheduler(context).rescheduleAll()
            pendingResult.finish()
        }
    }
}
