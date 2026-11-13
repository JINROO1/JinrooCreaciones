package com.jinroo.agent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var rootState = "sin comprobar"
    private val refresh = object : Runnable {
        override fun run() {
            status.text = "Root: $rootState\nModelo: ${if (assets.list("")?.contains("model.onnx") == true) "presente" else "no instalado"}\nServicio: ${ScreenCaptureService.status}"
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32); gravity = Gravity.CENTER_HORIZONTAL }
        status = TextView(this)
        layout.addView(status)
        layout.addView(Button(this).apply { text = "Comprobar acceso root"; setOnClickListener {
            rootState = "solicitando…"
            scope.launch { rootState = if (RootManager.check()) "concedido" else "denegado o no disponible" }
        } })
        layout.addView(Button(this).apply { text = "Iniciar captura y agente"; setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            val manager = getSystemService(MediaProjectionManager::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), 1)
        } })
        layout.addView(Button(this).apply { text = "Detener"; setOnClickListener { stopService(Intent(this@MainActivity, ScreenCaptureService::class.java)) } })
        setContentView(layout)
        handler.post(refresh)
    }
    @Deprecated("MediaProjection consent result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).putExtra("resultCode", resultCode).putExtra("data", data)
            startForegroundService(intent)
        }
    }
    override fun onDestroy() { handler.removeCallbacks(refresh); scope.cancel(); super.onDestroy() }
}
