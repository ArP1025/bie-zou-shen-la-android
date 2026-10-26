package cn.zhida.focussearch

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(TargetApps.PREFS, Context.MODE_PRIVATE) }
    private lateinit var serviceStatus: TextView
    private lateinit var eventStatus: TextView
    private val statusHandler = Handler(Looper.getMainLooper())
    private var connectionChecks = 0
    private val connectionCheck = object : Runnable {
        override fun run() {
            if (!::serviceStatus.isInitialized) return
            updateServiceStatus()
            connectionChecks++
            if (!TargetAppAccessibilityService.isConnected && connectionChecks < 10) {
                statusHandler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::serviceStatus.isInitialized) updateServiceStatus()
        if (::eventStatus.isInitialized) updateEventStatus()
        connectionChecks = 0
        statusHandler.removeCallbacks(connectionCheck)
        statusHandler.postDelayed(connectionCheck, 500L)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(connectionCheck)
        super.onPause()
    }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(247, 248, 252))
        }
        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)

        page.addView(text("别走神啦", 32, true, Color.rgb(34, 44, 74)))
        page.addView(text("打开内容平台前，先决定这次要做什么。", 16, false, Color.DKGRAY, 8))
        page.addView(text("服务状态", 20, true, Color.rgb(34, 44, 74), 28))

        serviceStatus = text("正在检查无障碍服务…", 15, false, Color.DKGRAY)
        page.addView(serviceStatus)
        page.addView(button("前往开启无障碍服务") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        page.addView(text("最近识别记录", 20, true, Color.rgb(34, 44, 74), 24))
        eventStatus = text("尚未收到应用切换事件", 14, false, Color.DKGRAY)
        page.addView(eventStatus)

        page.addView(text("监测哪些应用", 20, true, Color.rgb(34, 44, 74), 28))
        page.addView(text("只在你启用的应用进入前台时尝试显示选择层。应用识别在本机完成。", 14, false, Color.DKGRAY))
        TargetApps.all.forEach { target ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            val label = text(target.label, 17, true, Color.rgb(45, 52, 72))
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val toggle = Switch(this).apply {
                text = "启用"
                isChecked = prefs.getBoolean(TargetApps.ENABLED_PREFIX + target.id, true)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(TargetApps.ENABLED_PREFIX + target.id, checked).apply()
                }
            }
            row.addView(toggle)
            page.addView(row)
            page.addView(divider())
        }

        page.addView(text("首版说明", 20, true, Color.rgb(34, 44, 74), 28))
        page.addView(text("“娱乐”会收起选择层并继续使用当前应用。“搜索”会收起选择层，并在屏幕侧边显示小猫提醒你想想要搜索什么；不会遮挡内容，也不会自动跳转到平台搜索页。点击气泡上的 × 可关闭提醒。", 14, false, Color.DKGRAY))
        page.addView(text("若系统没有弹出选择层，请检查无障碍服务是否保持开启，并允许本应用在后台运行。", 14, false, Color.DKGRAY, 10))
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        val enabled = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name == TargetAppAccessibilityService::class.java.name }
        val connected = TargetAppAccessibilityService.isConnected
        serviceStatus.text = when {
            enabled && connected -> "已开启并已连接：正在识别目标应用"
            enabled -> "系统开关已开启，但服务当前未连接。请重新进入无障碍设置，关闭后再开启。"
            else -> "未开启：需要你在系统设置中手动授权"
        }
        serviceStatus.setTextColor(if (enabled && connected) Color.rgb(24, 128, 82) else Color.rgb(177, 92, 38))
    }

    private fun updateEventStatus() {
        val latest = prefs.getString("last_event", "尚未收到应用切换事件")
        val target = prefs.getString("last_target_event", "尚未收到目标应用事件")
        eventStatus.text = "最近窗口：$latest\n最近目标应用事件：$target"
    }

    private fun text(value: String, size: Int, bold: Boolean, color: Int, top: Int = 0) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (top > 0) setPadding(0, dp(top), 0, 0)
    }

    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        setOnClickListener { action() }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(Color.rgb(224, 227, 235))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
