package cn.zhida.focussearch

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class TargetAppAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var isConnected: Boolean = false
            private set
    }

    private var shownForPackage: String? = null
    private var overlay: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingExitReset: Runnable? = null
    private var lastEventLog = ""
    private var lastEventLogAt = 0L
    private var lastTargetLogAt = 0L
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        record("无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val isWindowsChange = event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!isStateChange && !isWindowsChange && !isContentChange) return
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()
        recordWindowEvent("${event.eventType}: $pkg · $className")

        if (pkg == packageName) {
            // Only the actual settings Activity means the user intentionally left.
            if (className == MainActivity::class.java.name) resetForLeavingTarget()
            // Ignore window events produced by our accessibility overlay.
            return
        }

        val target = TargetApps.all.firstOrNull { it.packageName == pkg }
        if (target == null) {
            if (isStateChange) scheduleExitReset()
            return
        }
        cancelExitReset()
        recordTargetEvent("${event.eventType}: $pkg · $className")

        val prefs = getSharedPreferences(TargetApps.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(TargetApps.ENABLED_PREFIX + target.id, true)) {
            resetForLeavingTarget()
            return
        }
        if (shownForPackage == target.packageName) return

        removeOverlay()
        shownForPackage = target.packageName
        showDecision(target)
    }

    private fun showDecision(target: TargetApp) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(22), dp(24), dp(20))
            background = rounded(Color.WHITE, 24)
            elevation = dp(12).toFloat()
        }
        panel.addView(TextView(this).apply {
            text = "这次打开${target.label}，\n你想做什么？"
            textSize = 20f
            setTextColor(Color.rgb(35, 43, 67))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        panel.addView(actionButton("娱乐", Color.rgb(91, 101, 230), Color.WHITE) {
            record("${target.label}：选择娱乐")
            removeOverlay()
            Toast.makeText(this, "已继续使用${target.label}", Toast.LENGTH_SHORT).show()
        })
        panel.addView(actionButton("搜索", Color.rgb(238, 241, 250), Color.rgb(51, 59, 83)) {
            record("${target.label}：选择搜索，显示小猫提醒")
            showCatReminder()
        })

        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            screenWidth - dp(40),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        addOverlay(panel, params)
    }

    /** A lightweight reminder at the screen edge that leaves the app usable. */
    private fun showCatReminder() {
        removeOverlay()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(6), dp(8))
            elevation = dp(10).toFloat()
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(6), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(223, 226, 241))
            }
        }
        bubble.addView(TextView(this).apply {
            text = "想想你是来搜什么的吧~"
            textSize = 14f
            setTextColor(Color.rgb(48, 56, 87))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        })
        bubble.addView(TextView(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(Color.rgb(130, 136, 158))
            gravity = Gravity.CENTER
            contentDescription = "关闭提醒"
            setPadding(dp(8), 0, dp(8), dp(2))
            setOnClickListener {
                record("已关闭小猫搜索提醒")
                removeOverlay()
            }
        })
        panel.addView(bubble, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(54)))
        panel.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_reminder_cat)
            contentDescription = "小猫提醒"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            leftMargin = dp(4)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        addOverlay(panel, params)
    }

    private fun actionButton(label: String, backgroundColor: Int, textColor: Int, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(textColor)
        textSize = 16f
        background = rounded(backgroundColor, 16)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
            topMargin = dp(12)
        }
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun addOverlay(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.addView(view, params)
            overlay = view
            record("覆盖层已显示")
        } catch (error: Exception) {
            overlay = null
            shownForPackage = null
            record("覆盖层失败：${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun removeOverlay() {
        overlay?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) { }
        }
        overlay = null
    }

    private fun resetForLeavingTarget() {
        cancelExitReset()
        removeOverlay()
        shownForPackage = null
    }

    private fun scheduleExitReset() {
        cancelExitReset()
        val reset = Runnable {
            pendingExitReset = null
            resetForLeavingTarget()
        }
        pendingExitReset = reset
        // Search flows can briefly surface an intermediate window. Wait for the
        // target app to disappear from the foreground before opening a new session.
        handler.postDelayed(reset, 2_500L)
    }

    private fun cancelExitReset() {
        pendingExitReset?.let(handler::removeCallbacks)
        pendingExitReset = null
    }

    private fun record(value: String) {
        getSharedPreferences(TargetApps.PREFS, Context.MODE_PRIVATE)
            .edit().putString("last_event", value).apply()
    }

    private fun recordWindowEvent(value: String) {
        val now = SystemClock.elapsedRealtime()
        if (value != lastEventLog || now - lastEventLogAt >= 1_000L) {
            lastEventLog = value
            lastEventLogAt = now
            record(value)
        }
    }

    private fun recordTargetEvent(value: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTargetLogAt >= 1_000L) {
            lastTargetLogAt = now
            getSharedPreferences(TargetApps.PREFS, Context.MODE_PRIVATE)
                .edit().putString("last_target_event", value).apply()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { removeOverlay() }
    override fun onTaskRemoved(rootIntent: Intent?) {
        record("直达任务已从最近任务移除；等待系统维持无障碍服务")
        super.onTaskRemoved(rootIntent)
    }
    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        record("无障碍服务已断开")
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        isConnected = false
        cancelExitReset()
        removeOverlay()
        record("无障碍服务进程已结束")
        super.onDestroy()
    }
}
