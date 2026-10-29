package com.teinzz.floatingapps

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable,
        val launchable: Boolean
    )

    private lateinit var appList: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var appCount: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var overlayButton: Button
    private var apps: List<AppEntry> = emptyList()
    private var pendingPackage: String? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 14, 25)
        window.navigationBarColor = Color.rgb(9, 14, 25)
        setContentView(R.layout.activity_main)

        appList = findViewById(R.id.appList)
        searchInput = findViewById(R.id.searchInput)
        appCount = findViewById(R.id.appCount)
        permissionStatus = findViewById(R.id.permissionStatus)
        overlayButton = findViewById(R.id.overlayButton)

        overlayButton.setOnClickListener { openOverlaySettings() }
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        loadInstalledApps()
        updatePermissionUi()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        val packageName = pendingPackage
        if (packageName != null && Settings.canDrawOverlays(this)) {
            val app = apps.firstOrNull { it.packageName == packageName }
            if (app != null) {
                pendingPackage = null
                openFloating(app)
            }
        }
    }

    private fun updatePermissionUi() {
        val allowed = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        permissionStatus.text = if (allowed) {
            "● Izin overlay aktif"
        } else {
            "● Izin overlay belum aktif"
        }
        permissionStatus.setTextColor(if (allowed) Color.rgb(116, 222, 171) else Color.rgb(255, 190, 108))
        overlayButton.text = if (allowed) "Izin overlay sudah aktif" else getString(R.string.overlay_permission)
        overlayButton.isEnabled = !allowed
        overlayButton.alpha = if (allowed) 0.65f else 1f
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
            updatePermissionUi()
            return
        }
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
        } catch (_: Exception) {
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)),
                    REQUEST_OVERLAY
                )
            } catch (_: Exception) {
                Toast.makeText(this, "Pengaturan izin overlay tidak dapat dibuka.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadInstalledApps() {
        if (loading) return
        loading = true
        appCount.text = "Memuat daftar aplikasi…"
        thread(name = "installed-app-loader") {
            val loaded = try {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    .filter { it.packageName != packageName }
                    .map { info ->
                        val label = try {
                            info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: info.packageName
                        } catch (_: Exception) {
                            info.packageName
                        }
                        val icon = try {
                            info.loadIcon(packageManager)
                        } catch (_: Exception) {
                            getDrawable(android.R.drawable.sym_def_app_icon)!!
                        }
                        val launchable = try {
                            packageManager.getLaunchIntentForPackage(info.packageName) != null
                        } catch (_: Exception) {
                            false
                        }
                        AppEntry(info.packageName, label, icon, launchable)
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                apps = loaded
                loading = false
                renderApps(searchInput.text?.toString().orEmpty())
                openPendingIfReady()
            }
        }
    }

    private fun openPendingIfReady() {
        val packageName = pendingPackage ?: return
        if (!Settings.canDrawOverlays(this)) return
        val app = apps.firstOrNull { it.packageName == packageName } ?: return
        pendingPackage = null
        openFloating(app)
    }

    private fun renderApps(query: String) {
        if (!::appList.isInitialized) return
        appList.removeAllViews()
        val filtered = apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        appCount.text = if (apps.isEmpty() && loading) {
            "Memuat daftar aplikasi…"
        } else {
            filtered.size.toString() + " aplikasi"
        }

        if (filtered.isEmpty() && !loading) {
            val empty = TextView(this).apply {
                text = "Tidak ada aplikasi yang cocok."
                setTextColor(Color.rgb(155, 174, 204))
                textSize = 13f
                setPadding(dp(10), dp(18), dp(10), dp(18))
            }
            appList.addView(empty)
            return
        }

        filtered.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = getDrawable(R.drawable.row_bg)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            }
            row.addView(icon)

            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(8)
                }
            }
            val name = TextView(this).apply {
                text = app.label
                setTextColor(Color.rgb(244, 247, 255))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val packageLabel = TextView(this).apply {
                text = if (app.launchable) app.packageName else app.packageName + " • tidak punya launcher"
                setTextColor(Color.rgb(132, 153, 185))
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            texts.addView(name)
            texts.addView(packageLabel)
            row.addView(texts)

            val arrow = TextView(this).apply {
                text = if (app.launchable) "›" else "·"
                setTextColor(Color.rgb(92, 151, 255))
                textSize = 25f
                gravity = Gravity.CENTER
            }
            row.addView(arrow)
            row.setOnClickListener {
                if (!app.launchable) {
                    Toast.makeText(this, "Aplikasi ini tidak menyediakan Activity launcher.", Toast.LENGTH_SHORT).show()
                } else {
                    openFloating(app)
                }
            }
            appList.addView(row)
        }
    }

    private fun openFloating(app: AppEntry) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            pendingPackage = app.packageName
            openOverlaySettings()
            return
        }

        val serviceIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_OPEN_APP
            putExtra(FloatingWindowService.EXTRA_PACKAGE, app.packageName)
            putExtra(FloatingWindowService.EXTRA_NAME, app.label)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Service overlay gagal dimulai: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
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
                val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
                startActivity(launchIntent, options.toBundle())
            } else {
                startActivity(launchIntent)
                Toast.makeText(
                    this,
                    "Perangkat tidak melaporkan dukungan Freeform. Aplikasi dibuka normal; kontrol overlay tetap tersedia.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Activity aplikasi tidak ditemukan.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka aplikasi: " + (e.message ?: "unsupported"), Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_OVERLAY = 901
    }
}
