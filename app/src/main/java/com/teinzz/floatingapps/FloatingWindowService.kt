package com.teinzz.floatingapps

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class FloatingWindowService : Service() {

    private data class FloatingItem(
        val id: String,
        val packageName: String,
        val appName: String,
        val appIcon: Drawable,
        val root: View,
        val params: WindowManager.LayoutParams,
        var bubble: View? = null,
        var bubbleParams: WindowManager.LayoutParams? = null,
        var maximized: Boolean = false,
        var savedWidth: Int = 0,
        var savedHeight: Int = 0,
        var savedX: Int = 0,
        var savedY: Int = 0
    )

    private lateinit var windowManager: WindowManager
    private val windows = linkedMapOf<String, FloatingItem>()
    private var sequence = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_OPEN_APP -> {
                if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Izin overlay belum diberikan.", Toast.LENGTH_SHORT).show()
                    stopSelf()
                    return START_NOT_STICKY
                }
                val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return START_NOT_STICKY
                val appName = intent.getStringExtra(EXTRA_NAME) ?: packageName
                createFloatingWindow(packageName, appName)
            }
            ACTION_STOP_ALL -> {
                removeAllWindows()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createFloatingWindow(packageName: String, appName: String) {
        val appIcon = try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            getDrawable(android.R.drawable.sym_def_app_icon)!!
        }
        val root = LayoutInflater.from(this).inflate(R.layout.floating_window, null, false)
        root.findViewById<TextView>(R.id.windowAppName).text = appName
        root.findViewById<TextView>(R.id.windowMessage).text =
            "Kontrol overlay untuk aplikasi ini. Konten aplikasi lain tidak dapat ditanam langsung ke View milik aplikasi ini. Jika Android menyediakan Freeform, aplikasi dibuka dalam jendela native terpisah."

        val params = WindowManager.LayoutParams(
            dp(300),
            dp(340),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(100 + (windows.size % 5) * 34)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "Floating Apps: " + appName
        }

        val id = packageName + "_" + System.currentTimeMillis() + "_" + sequence++
        val item = FloatingItem(
            id = id,
            packageName = packageName,
            appName = appName,
            appIcon = appIcon,
            root = root,
            params = params,
            savedWidth = params.width,
            savedHeight = params.height,
            savedX = params.x,
            savedY = params.y
        )
        windows[id] = item

        root.findViewById<TextView>(R.id.btnClose).setOnClickListener { closeWindow(item) }
        root.findViewById<TextView>(R.id.btnMinimize).setOnClickListener { minimizeWindow(item) }
        root.findViewById<TextView>(R.id.btnMaximize).setOnClickListener { toggleMaximize(item) }
        root.findViewById<Button>(R.id.launchAppButton).setOnClickListener { launchTarget(item) }
        attachDragHandler(item)
        attachResizeHandler(item)

        try {
            windowManager.addView(root, params)
        } catch (e: Exception) {
            windows.remove(id)
            Toast.makeText(this, "Tidak dapat menampilkan overlay: " + (e.message ?: "izin ditolak"), Toast.LENGTH_LONG).show()
            if (windows.isEmpty()) {
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun attachDragHandler(item: FloatingItem) {
        val dragArea = item.root.findViewById<TextView>(R.id.windowAppName)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        dragArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = item.params.x
                    initialY = item.params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (resources.displayMetrics.widthPixels - item.params.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - dp(70)).coerceAtLeast(0)
                    item.params.x = (initialX + event.rawX - touchX).roundToInt().coerceIn(0, maxX)
                    item.params.y = (initialY + event.rawY - touchY).roundToInt().coerceIn(0, maxY)
                    updateView(item.root, item.params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun attachResizeHandler(item: FloatingItem) {
        val handle = item.root.findViewById<View>(R.id.resizeHandle)
        var startWidth = 0
        var startHeight = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startWidth = item.params.width
                    startHeight = item.params.height
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxWidth = (resources.displayMetrics.widthPixels - dp(12)).coerceAtLeast(dp(220))
                    val maxHeight = (resources.displayMetrics.heightPixels - dp(60)).coerceAtLeast(dp(180))
                    item.params.width = (startWidth + event.rawX - touchX).roundToInt().coerceIn(dp(240), maxWidth)
                    item.params.height = (startHeight + event.rawY - touchY).roundToInt().coerceIn(dp(200), maxHeight)
                    item.maximized = false
                    updateView(item.root, item.params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun toggleMaximize(item: FloatingItem) {
        if (!item.maximized) {
            item.savedWidth = item.params.width
            item.savedHeight = item.params.height
            item.savedX = item.params.x
            item.savedY = item.params.y
            item.params.width = (resources.displayMetrics.widthPixels - dp(16)).coerceAtLeast(dp(240))
            item.params.height = (resources.displayMetrics.heightPixels - dp(72)).coerceAtLeast(dp(200))
            item.params.x = dp(8)
            item.params.y = dp(36)
            item.maximized = true
        } else {
            item.params.width = item.savedWidth
            item.params.height = item.savedHeight
            item.params.x = item.savedX
            item.params.y = item.savedY
            item.maximized = false
        }
        updateView(item.root, item.params)
    }

    private fun minimizeWindow(item: FloatingItem) {
        try {
            if (item.root.parent != null) windowManager.removeView(item.root)
        } catch (_: Exception) {
            // The view may already have been detached by Android.
        }

        val bubble = LayoutInflater.from(this).inflate(R.layout.bubble, null, false)
        bubble.findViewById<ImageView>(R.id.bubbleIcon).setImageDrawable(item.appIcon)
        val bubbleParams = WindowManager.LayoutParams(
            dp(58),
            dp(58),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = item.params.x
            y = item.params.y
            title = "Floating Apps bubble: " + item.appName
        }
        item.bubble = bubble
        item.bubbleParams = bubbleParams
        attachBubbleDragHandler(item)
        try {
            windowManager.addView(bubble, bubbleParams)
        } catch (e: Exception) {
            item.bubble = null
            item.bubbleParams = null
            closeWindow(item)
        }
    }

    private fun attachBubbleDragHandler(item: FloatingItem) {
        val bubble = item.bubble ?: return
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { view, event ->
            val params = item.bubbleParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                    val maxX = (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - params.height).coerceAtLeast(0)
                    params.x = (initialX + dx).roundToInt().coerceIn(0, maxX)
                    params.y = (initialY + dy).roundToInt().coerceIn(0, maxY)
                    updateView(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) restoreWindow(item)
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun restoreWindow(item: FloatingItem) {
        val bubble = item.bubble
        if (bubble != null) {
            try {
                if (bubble.parent != null) windowManager.removeView(bubble)
            } catch (_: Exception) {
            }
        }
        item.bubble = null
        item.bubbleParams = null
        try {
            if (item.root.parent == null) windowManager.addView(item.root, item.params)
        } catch (e: Exception) {
            closeWindow(item)
        }
    }

    private fun launchTarget(item: FloatingItem) {
        val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
        if (launchIntent == null) {
            updateMessage(item, "Aplikasi ini tidak memiliki Activity launcher yang dapat dibuka.")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            if (Build.VERSION.SDK_INT >= 24 &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
            ) {
                val metrics = resources.displayMetrics
                val bounds = Rect(
                    dp(12),
                    dp(64),
                    metrics.widthPixels - dp(12),
                    (metrics.heightPixels * 0.72f).roundToInt()
                )
                startActivity(launchIntent, ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle())
                updateMessage(item, "Permintaan freeform dikirim ke Android. Konten aplikasi tampil sebagai jendela native terpisah jika OS mengizinkannya.")
            } else {
                startActivity(launchIntent)
                updateMessage(item, "Freeform tidak tersedia menurut fitur sistem. Aplikasi dibuka normal; panel ini adalah kontrol overlay.")
            }
        } catch (e: Exception) {
            updateMessage(item, "Tidak dapat meluncurkan aplikasi: " + (e.message ?: "fitur tidak didukung"))
        }
    }

    private fun updateMessage(item: FloatingItem, message: String) {
        item.root.findViewById<TextView>(R.id.windowMessage).text = message
    }

    private fun closeWindow(item: FloatingItem) {
        try {
            if (item.root.parent != null) windowManager.removeView(item.root)
        } catch (_: Exception) {
        }
        try {
            if (item.bubble?.parent != null) windowManager.removeView(item.bubble)
        } catch (_: Exception) {
        }
        windows.remove(item.id)
        item.bubble = null
        item.bubbleParams = null
        if (windows.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun removeAllWindows() {
        windows.values.toList().forEach { item ->
            try {
                if (item.root.parent != null) windowManager.removeView(item.root)
            } catch (_: Exception) {
            }
            try {
                if (item.bubble?.parent != null) windowManager.removeView(item.bubble)
            } catch (_: Exception) {
            }
        }
        windows.clear()
    }

    private fun updateView(view: View, params: WindowManager.LayoutParams) {
        try {
            if (view.parent != null) windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Floating windows", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) pendingFlags = pendingFlags or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Floating Apps aktif")
            .setContentText("Kontrol jendela dan bubble siap digunakan")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        removeAllWindows()
        stopForeground(true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_APP = "com.teinzz.floatingapps.action.OPEN_APP"
        const val ACTION_STOP_ALL = "com.teinzz.floatingapps.action.STOP_ALL"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_NAME = "extra_name"
        private const val CHANNEL_ID = "floating_apps_service"
        private const val NOTIFICATION_ID = 2101
    }
}
