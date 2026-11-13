package com.jinroo.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

class ScreenCaptureService : Service() {
    companion object { @Volatile var status: String = "detenido" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val latest = AtomicReference<Bitmap?>(null)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var started = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true
        val channel = "capture"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Captura activa", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Jinroo Agent activo").setContentText("Captura e inferencia local en ejecución").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(1, notification)
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { status = "falta consentimiento"; stopSelf(); return START_NOT_STICKY }
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
            val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("resultCode", 0), data)
            projection = p
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { status = "captura revocada"; stopSelf() } }, Handler(mainLooper))
            val thread = HandlerThread("capture").also { it.start() }; captureThread = thread
            val imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 3); reader = imageReader
            imageReader.setOnImageAvailableListener({ r ->
                try {
                    val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    image.use {
                        val plane = it.planes[0]
                        val paddedWidth = it.width + (plane.rowStride - plane.pixelStride * it.width) / plane.pixelStride
                        val padded = Bitmap.createBitmap(paddedWidth, it.height, Bitmap.Config.ARGB_8888)
                        try {
                            padded.copyPixelsFromBuffer(plane.buffer)
                            val frame = Bitmap.createBitmap(padded, 0, 0, it.width, it.height)
                            if (frame !== padded) padded.recycle()
                            latest.getAndSet(frame)?.recycle()
                        } catch (e: Exception) { if (!padded.isRecycled) padded.recycle(); throw e }
                    }
                } catch (e: Exception) { status = "error de captura: ${e.message}"; stopSelf() }
            }, Handler(thread.looper))
            display = p.createVirtualDisplay("JinrooCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null)
        } catch (e: Exception) { status = "error al iniciar captura: ${e.message}"; stopSelf(); return START_NOT_STICKY }
        scope.launch {
            try {
                status = "comprobando root"
                check(RootManager.check()) { "root no concedido" }
                InferenceEngine(this@ScreenCaptureService).use { engine ->
                    val executor = ActionExecutor()
                    status = "activo"
                    while (isActive) {
                        val frame = latest.getAndSet(null)
                        if (frame != null) {
                            try {
                                val action = engine.infer(frame)
                                if (action != null) check(executor.execute(action)) { "falló la inyección" }
                            } finally { frame.recycle() }
                        }
                        delay(500) // Safety rate limit; not a 30–60 FPS inference claim.
                    }
                }
            } catch (_: CancellationException) { throw CancellationException() }
            catch (e: Exception) { status = "error: ${e.message}"; stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        scope.cancel()
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop()
        captureThread?.quitSafely()
        latest.getAndSet(null)?.recycle()
        if (status == "activo") status = "detenido"
        super.onDestroy()
    }
}
