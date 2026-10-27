package com.venice.spy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager

object Actions {
    /** নম্বরে কল দিয়ে কয়েক সেকেন্ড পরে কেটে দেয় → ওপাশে "মিস কল" হিসেবে জমা হয় */
    fun missCall(ctx: Context, number: String, hangupMs: Long = 6000) {
        if (number.isBlank()) return
        try {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            Handler(Looper.getMainLooper()).postDelayed({
                if (Build.VERSION.SDK_INT >= 28) {
                    try {
                        (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall()
                    } catch (_: Exception) {}
                }
            }, hangupMs)
        } catch (_: Exception) {}
    }
}