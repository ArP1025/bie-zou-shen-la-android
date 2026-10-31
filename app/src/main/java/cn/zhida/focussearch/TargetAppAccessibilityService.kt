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
import android.view.accessibility.AccessibilityWindowInfo
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

    private enum class OverlayKind { NONE, POPUP, CAT, BOTTOM }

    private var overlay: View? = null
    private var overlayKind = OverlayKind.NONE
    private var activeTarget: TargetApp? = null
    private val states = mutableMapOf<String, AppReminderState>()
    private val handler = Handler(Looper.getMainLooper())
    private var exitSessionRunnable: Runnable? = null
    private var reminderCheckRunnable: Runnable? = null
    private var pollRunnable: Runnable? = null
    private var lastEventLog = ""
    private var lastEventLogAt = 0L
    private var lastTargetLogAt = 0L
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val prefs by lazy { getSharedPreferences(TargetApps.PREFS, Context.MODE_PRIVATE) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        record("无障碍服务已连接")
        startPolling()
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
            if (isStateChange && className == MainActivity::class.java.name) endSession()
            // Ignore window events produced by our accessibility overlay.
            return
        }

        val target = TargetApps.all.firstOrNull { it.packageName == pkg }
        if (target == null) {
            if (isStateChange) scheduleExitSession()
            return
        }

        if (!prefs.getBoolean(TargetApps.ENABLED_PREFIX + target.id, true)) {
            if (activeTarget?.packageName == target.packageName) endSession()
            return
        }

        val state = stateFor(target.packageName)
        state.lastForegroundAt = SystemClock.elapsedRealtime()
        recordTargetEvent("${event.eventType}: $pkg · $className")
        cancelExitSession()

        if (activeTarget?.packageName == target.packageName && state.sessionActive) return
        startSession(target)
    }

    private fun startSession(target: TargetApp) {
        cancelExitSession()
        cancelReminderCheck()
        removeOverlay()
        activeTarget?.let { old ->
            if (old.packageName != target.packageName) {
                stateFor(old.packageName).apply {
                    sessionActive = false
                    popupShowing = false
                    catShowing = false
                    guideShowing = false
                    lastReminderAt = 0L
                }
            }
        }
        activeTarget = target
        val state = stateFor(target.packageName)
        state.sessionActive = true
        state.lastForegroundAt = SystemClock.elapsedRealtime()
        state.lastReminderAt = 0L
        state.popupShowing = false
        state.catShowing = false
        state.guideShowing = false
        maybeShowReminder(target)
    }

    private fun endSession() {
        cancelExitSession()
        cancelReminderCheck()
        activeTarget?.let { old ->
            stateFor(old.packageName).apply {
                sessionActive = false
                popupShowing = false
                catShowing = false
                guideShowing = false
                lastReminderAt = 0L
            }
        }
        activeTarget = null
        removeOverlay()
    }

    private fun maybeShowReminder(target: TargetApp) {
        val state = stateFor(target.packageName)
        if (!state.sessionActive || state.popupShowing || state.catShowing || state.guideShowing) return
        val now = SystemClock.elapsedRealtime()
        val first = state.lastReminderAt == 0L
        val frequency = currentFrequency()
        val interval = frequencyInterval(frequency)
        val due = when (frequency) {
            ReminderFrequency.FIRST_ENTRY -> first
            else -> first || now - state.lastReminderAt >= interval
        }
        if (!due) {
            if (frequency != ReminderFrequency.FIRST_ENTRY) {
                scheduleReminderCheck(interval - (now - state.lastReminderAt))
            }
            return
        }
        state.lastReminderAt = now
        state.popupShowing = true
        state.catShowing = false
        state.guideShowing = false
        showDecision(target, timedHint = !first)
    }

    private fun scheduleReminderCheck(delayMs: Long) {
        cancelReminderCheck()
        if (delayMs <= 0L) return
        val runnable = Runnable {
            reminderCheckRunnable = null
            activeTarget?.let { maybeShowReminder(it) }
        }
        reminderCheckRunnable = runnable
        handler.postDelayed(runnable, delayMs.coerceAtLeast(250L))
    }

    private fun scheduleNextAfterDismiss(target: TargetApp) {
        val state = stateFor(target.packageName)
        val frequency = currentFrequency()
        if (frequency == ReminderFrequency.FIRST_ENTRY || !state.sessionActive) return
        val now = SystemClock.elapsedRealtime()
        val remaining = frequencyInterval(frequency) - (now - state.lastReminderAt)
        scheduleReminderCheck(remaining.coerceAtLeast(0L))
    }

    private fun startPolling() {
        stopPolling()
        checkForegroundWindow()
        schedulePolling()
    }

    private fun schedulePolling() {
        if (!isConnected) return
        stopPolling()
        val runnable = Runnable {
            pollRunnable = null
            if (isConnected) {
                checkForegroundWindow()
                schedulePolling()
            }
        }
        pollRunnable = runnable
        handler.postDelayed(runnable, 1_000L)
    }

    private fun stopPolling() {
        pollRunnable?.let(handler::removeCallbacks)
        pollRunnable = null
    }

    /** Fallback for cold start: if the service missed the initial state-change
     *  event, poll the foreground application package to open the session. */
    private fun checkForegroundWindow() {
        val pkg = foregroundPackage() ?: return
        val target = TargetApps.all.firstOrNull { it.packageName == pkg } ?: return
        if (!prefs.getBoolean(TargetApps.ENABLED_PREFIX + target.id, true)) return
        val state = stateFor(pkg)
        state.lastForegroundAt = SystemClock.elapsedRealtime()
        if (activeTarget?.packageName == pkg && state.sessionActive) return
        cancelExitSession()
        startSession(target)
    }

    private fun foregroundPackage(): String? = try {
        val appWindow = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        appWindow?.root?.packageName?.toString()
    } catch (_: Exception) {
        null
    }

    private fun chooseEntertain(target: TargetApp) {
        record("${target.label}：选择娱乐")
        stateFor(target.packageName).apply {
            popupShowing = false
            catShowing = false
            guideShowing = false
        }
        removeOverlay()
        Toast.makeText(this, "已继续使用${target.label}", Toast.LENGTH_SHORT).show()
        scheduleNextAfterDismiss(target)
    }

    private fun closePopup(target: TargetApp) {
        record("${target.label}：直接关闭选择层")
        stateFor(target.packageName).apply {
            popupShowing = false
            catShowing = false
            guideShowing = false
        }
        removeOverlay()
        scheduleNextAfterDismiss(target)
    }

    private fun chooseSearch(target: TargetApp) {
        val useBottomGuide = prefs.getBoolean(TargetApps.BOTTOM_GUIDE_ENABLED, false)
        record(if (useBottomGuide) "${target.label}：选择搜索，显示下半屏引导" else "${target.label}：选择搜索，显示小猫提醒")
        stateFor(target.packageName).apply {
            popupShowing = false
            catShowing = !useBottomGuide
            guideShowing = useBottomGuide
        }
        if (useBottomGuide) showBottomGuide() else showCatReminder()
    }

    private fun closeCat(target: TargetApp) {
        record("已关闭小猫搜索提醒")
        stateFor(target.packageName).catShowing = false
        removeOverlay()
        scheduleNextAfterDismiss(target)
    }

    private fun closeGuide(target: TargetApp) {
        record("已关闭下半屏引导")
        stateFor(target.packageName).guideShowing = false
        removeOverlay()
        scheduleNextAfterDismiss(target)
    }

    private fun currentFrequency(): ReminderFrequency {
        val stored = prefs.getString(TargetApps.FREQ_KEY, null)
        return ReminderFrequency.entries.firstOrNull { it.name == stored }
            ?: ReminderFrequency.FIRST_ENTRY
    }

    private fun customMinutes(): Int {
        val stored = prefs.getString(TargetApps.FREQ_CUSTOM_MINUTES, null)?.toIntOrNull()
        return (stored ?: TargetApps.CUSTOM_MINUTES_DEFAULT)
            .coerceIn(TargetApps.CUSTOM_MINUTES_MIN, TargetApps.CUSTOM_MINUTES_MAX)
    }

    private fun frequencyInterval(frequency: ReminderFrequency): Long = when (frequency) {
        ReminderFrequency.FIRST_ENTRY -> 0L
        ReminderFrequency.FIVE_MINUTES, ReminderFrequency.TEN_MINUTES -> frequency.intervalMillis
        ReminderFrequency.CUSTOM -> customMinutes().toLong() * 60_000L
    }

    private fun settingsText(key: String, fallback: String): String {
        val raw = prefs.getString(key, null)?.trim()
        return raw?.takeIf { it.isNotEmpty() } ?: fallback
    }

    private fun showDecision(target: TargetApp, timedHint: Boolean) {
        removeOverlay()
        val title = settingsText(TargetApps.TEXT_TITLE, ReminderTexts.TITLE).replace("{app}", target.label)
        val entertain = settingsText(TargetApps.TEXT_ENTERTAIN, ReminderTexts.ENTERTAIN)
        val search = settingsText(TargetApps.TEXT_SEARCH, ReminderTexts.SEARCH)
        val hint = settingsText(TargetApps.TEXT_HINT, ReminderTexts.HINT)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(16), dp(20))
            background = rounded(Color.WHITE, 24)
            elevation = dp(12).toFloat()
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.rgb(35, 43, 67))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(TextView(this).apply {
            text = "×"
            textSize = 24f
            setTextColor(Color.rgb(130, 136, 158))
            gravity = Gravity.CENTER
            contentDescription = "关闭选择层"
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { closePopup(target) }
        })
        panel.addView(titleRow)
        if (timedHint && hint.isNotEmpty()) {
            panel.addView(TextView(this).apply {
                text = hint
                textSize = 14f
                setTextColor(Color.rgb(177, 92, 38))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }
        panel.addView(actionButton(entertain, Color.rgb(91, 101, 230), Color.WHITE) {
            chooseEntertain(target)
        })
        panel.addView(actionButton(search, Color.rgb(238, 241, 250), Color.rgb(51, 59, 83)) {
            chooseSearch(target)
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
        overlayKind = OverlayKind.POPUP
        addOverlay(panel, params)
    }

    /** A lightweight reminder at the screen edge that leaves the app usable. */
    private fun showCatReminder() {
        removeOverlay()
        val bubbleText = settingsText(TargetApps.TEXT_BUBBLE, ReminderTexts.BUBBLE)
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
            text = bubbleText
            textSize = 14f
            setTextColor(Color.rgb(48, 56, 87))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 3
        })
        bubble.addView(TextView(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(Color.rgb(130, 136, 158))
            gravity = Gravity.CENTER
            contentDescription = "关闭提醒"
            setPadding(dp(8), 0, dp(8), dp(2))
            setOnClickListener {
                val target = activeTarget
                if (target != null) closeCat(target) else removeOverlay()
            }
        })
        panel.addView(bubble, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
        overlayKind = OverlayKind.CAT
        addOverlay(panel, params)
    }

    /** Covers the lower ~4/5 of the screen, leaving the top ~1/5 usable so the
     *  user is nudged toward the app's own top search bar. */
    private fun showBottomGuide() {
        removeOverlay()
        val screenHeight = resources.displayMetrics.heightPixels
        val shieldHeight = screenHeight * 4 / 5
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 17, 22))
            isClickable = true
        }
        val closeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), dp(10), dp(6))
        }
        closeRow.addView(TextView(this).apply {
            text = "×"
            textSize = 26f
            setTextColor(Color.rgb(200, 202, 210))
            gravity = Gravity.CENTER
            contentDescription = "关闭下半屏引导"
            setPadding(dp(12), dp(2), dp(12), dp(2))
            setOnClickListener {
                val target = activeTarget
                if (target != null) closeGuide(target) else removeOverlay()
            }
        })
        panel.addView(closeRow)
        panel.addView(TextView(this).apply {
            text = "先到顶部搜索栏找到你要的内容吧"
            textSize = 15f
            setTextColor(Color.rgb(200, 202, 210))
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(8), dp(24), dp(24))
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            shieldHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        overlayKind = OverlayKind.BOTTOM
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
            overlayKind = OverlayKind.NONE
            activeTarget?.let { stateFor(it.packageName) }?.apply {
                popupShowing = false
                catShowing = false
                guideShowing = false
                lastReminderAt = 0L
            }
            record("覆盖层失败：${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun removeOverlay() {
        overlay?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) { }
        }
        overlay = null
        overlayKind = OverlayKind.NONE
    }

    private fun scheduleExitSession() {
        cancelExitSession()
        val runnable = Runnable {
            exitSessionRunnable = null
            endSession()
        }
        exitSessionRunnable = runnable
        // Search flows can briefly surface an intermediate window. Wait for the
        // target app to disappear from the foreground before ending the session.
        handler.postDelayed(runnable, 2_500L)
    }

    private fun cancelExitSession() {
        exitSessionRunnable?.let(handler::removeCallbacks)
        exitSessionRunnable = null
    }

    private fun cancelReminderCheck() {
        reminderCheckRunnable?.let(handler::removeCallbacks)
        reminderCheckRunnable = null
    }

    private fun stateFor(pkg: String) = states.getOrPut(pkg) { AppReminderState(pkg) }

    private fun record(value: String) {
        prefs.edit().putString("last_event", value).apply()
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
            prefs.edit().putString("last_target_event", value).apply()
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
        stopPolling()
        record("无障碍服务已断开")
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        isConnected = false
        stopPolling()
        cancelExitSession()
        cancelReminderCheck()
        removeOverlay()
        record("无障碍服务进程已结束")
        super.onDestroy()
    }
}