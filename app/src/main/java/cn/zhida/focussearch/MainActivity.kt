package cn.zhida.focussearch

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(TargetApps.PREFS, Context.MODE_PRIVATE) }

    private val brand = Color.rgb(255, 138, 61)
    private val brandDark = Color.rgb(214, 102, 26)
    private val ink = Color.rgb(34, 44, 74)
    private val inkSoft = Color.rgb(45, 52, 72)
    private val muted = Color.rgb(120, 127, 145)
    private val green = Color.rgb(24, 128, 82)
    private val orange = Color.rgb(214, 102, 26)
    private val surface = Color.rgb(247, 248, 252)
    private val typeface = Typeface.DEFAULT

    private var homePanel: View? = null
    private var settingsPanel: View? = null
    private lateinit var homeTabLabel: TextView
    private lateinit var settingsTabLabel: TextView
    private lateinit var homeTabDot: View
    private lateinit var settingsTabDot: View
    private var currentTabHome = true

    private lateinit var serviceStatus: TextView
    private lateinit var serviceDot: View
    private lateinit var eventStatus: TextView
    private lateinit var previewTitle: TextView
    private lateinit var previewHint: TextView
    private lateinit var previewEntertain: TextView
    private lateinit var previewSearch: TextView
    private lateinit var previewBubble: TextView
    private var customMinutesInput: EditText? = null
    private var frequencyOptions: LinearLayout? = null

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
        if (::previewTitle.isInitialized) updatePreview()
        connectionChecks = 0
        statusHandler.removeCallbacks(connectionCheck)
        statusHandler.postDelayed(connectionCheck, 500L)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(connectionCheck)
        super.onPause()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        setContentView(root)

        val content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        homePanel = buildHomePanel()
        settingsPanel = buildSettingsPanel().apply { visibility = View.GONE }
        content.addView(homePanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        content.addView(settingsPanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        root.addView(buildBottomNav())

        selectTab(true)
        updateServiceStatus()
        updatePreview()
    }

    private fun buildHomePanel(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(list) }

        list.addView(text("别走神啦", 30, true, ink))
        list.addView(text("打开内容平台前，先决定这次要做什么。", 15, false, muted, 6))

        card(list) { c ->
            c.addView(text("服务状态", 16, true, ink))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, 0)
            }
            serviceDot = dot(muted, 10)
            row.addView(serviceDot)
            serviceStatus = text("正在检查无障碍服务…", 15, false, inkSoft)
            row.addView(serviceStatus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10) })
            c.addView(row)
            c.addView(primaryButton("前往开启无障碍服务") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(14) })
        }

        card(list) { c ->
            c.addView(text("它能做什么", 16, true, ink))
            c.addView(text("当你打开小红书、抖音、哔哩哔哩或知乎时，会在顶部弹出一个小窗，问你这次是“娱乐”还是“搜索”。", 14, false, inkSoft, 6))
            c.addView(text("选“娱乐”就继续用；选“搜索”会引导你去顶部的搜索栏，而不是被内容吸走。所有判断都在本机完成，不会上传任何内容。", 14, false, inkSoft, 6))
        }

        card(list) { c ->
            c.addView(text("怎么开始", 16, true, ink))
            c.addView(text("1. 点击上方按钮，去系统设置里开启“别走神啦应用识别”。", 14, false, inkSoft, 6))
            c.addView(text("2. 允许本应用在后台运行（在系统的应用管理中关闭省电限制）。", 14, false, inkSoft, 6))
            c.addView(text("3. 打开你想专注的目标应用，就会看到提醒了。", 14, false, inkSoft, 6))
        }

        return scroll
    }

    private fun buildSettingsPanel(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(list) }

        list.addView(text("设置", 26, true, ink))
        list.addView(text("按你的习惯调整提醒方式。", 14, false, muted, 4))

        card(list) { c ->
            c.addView(text("监测哪些应用", 16, true, ink))
            c.addView(text("只在你启用的应用进入前台时显示提醒。", 13, false, muted, 4))
            TargetApps.all.forEach { target ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(14), 0, dp(14))
                }
                row.addView(text(target.label, 16, true, inkSoft), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Switch(this).apply {
                    isChecked = prefs.getBoolean(TargetApps.ENABLED_PREFIX + target.id, true)
                    setOnCheckedChangeListener { _, checked ->
                        prefs.edit().putBoolean(TargetApps.ENABLED_PREFIX + target.id, checked).apply()
                    }
                })
                c.addView(row)
                if (target != TargetApps.all.last()) c.addView(divider())
            }
        }

        card(list) { c ->
            c.addView(text("提醒频率", 16, true, ink))
            c.addView(text("“仅首次”以你离开目标应用再回来算作一次新会话。", 13, false, muted, 4))
            val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            frequencyOptions = options
            c.addView(options, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

            val minutes = prefs.getString(TargetApps.FREQ_CUSTOM_MINUTES, null)?.toIntOrNull()
                ?: TargetApps.CUSTOM_MINUTES_DEFAULT
            val minutesRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            minutesRow.addView(text("自定义间隔（分钟）", 14, false, muted), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val minutesInput = EditText(this).apply {
                setText(minutes.toString())
                setSelection(minutes.toString().length)
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(3))
                setTextColor(ink)
                textSize = 15f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = rounded(Color.WHITE, 12)
            }
            minutesInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString()?.toIntOrNull()
                        ?.coerceIn(TargetApps.CUSTOM_MINUTES_MIN, TargetApps.CUSTOM_MINUTES_MAX)
                        ?: TargetApps.CUSTOM_MINUTES_DEFAULT
                    prefs.edit().putString(TargetApps.FREQ_CUSTOM_MINUTES, value.toString()).apply()
                }
            })
            minutesRow.addView(minutesInput, LinearLayout.LayoutParams(dp(88), LinearLayout.LayoutParams.WRAP_CONTENT))
            c.addView(minutesRow)
            customMinutesInput = minutesInput
            rebuildFrequencyOptions()
            updateCustomMinutesEnabled()
        }

        card(list) { c ->
            c.addView(text("下半屏引导", 16, true, ink))
            c.addView(text("开启后，点“搜索”会用黑色遮住屏幕下方约 4/5，只留顶部约 1/5，引导你直接使用顶部搜索栏；关闭则回到侧边小猫提醒。", 13, false, muted, 4))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, 0)
            }
            row.addView(text("搜索后遮住下半屏", 15, true, inkSoft), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply {
                isChecked = prefs.getBoolean(TargetApps.BOTTOM_GUIDE_ENABLED, false)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(TargetApps.BOTTOM_GUIDE_ENABLED, checked).apply()
                }
            })
            c.addView(row)
        }

        card(list) { c ->
            c.addView(text("自定义文案", 16, true, ink))
            c.addView(text("留空自动恢复默认；标题支持换行。", 13, false, muted, 4))
            c.addView(settingsText("标题（{app} 替换成应用名）", TargetApps.TEXT_TITLE, ReminderTexts.TITLE, TargetApps.TITLE_MAX, true))
            c.addView(settingsText("“娱乐”按钮", TargetApps.TEXT_ENTERTAIN, ReminderTexts.ENTERTAIN, TargetApps.BUTTON_MAX))
            c.addView(settingsText("“搜索”按钮", TargetApps.TEXT_SEARCH, ReminderTexts.SEARCH, TargetApps.BUTTON_MAX))
            c.addView(settingsText("小猫气泡", TargetApps.TEXT_BUBBLE, ReminderTexts.BUBBLE, TargetApps.BUBBLE_MAX))
            c.addView(settingsText("定时提醒提示（非首次提醒时显示）", TargetApps.TEXT_HINT, ReminderTexts.HINT, TargetApps.HINT_MAX))
        }

        card(list) { c ->
            c.addView(text("弹窗预览", 16, true, ink))
            c.addView(text("以小红书为例，随填写实时更新。", 13, false, muted, 4))
            c.addView(buildPreview(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        card(list) { c ->
            c.addView(text("最近识别记录", 16, true, ink))
            eventStatus = text("尚未收到应用切换事件", 13, false, muted)
            c.addView(eventStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }

        return scroll
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        nav.addView(navItem("使用") { item, label, dot ->
            homeTabLabel = label
            homeTabDot = dot
            item.setOnClickListener { selectTab(true) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        nav.addView(navItem("设置") { item, label, dot ->
            settingsTabLabel = label
            settingsTabDot = dot
            item.setOnClickListener { selectTab(false) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        return nav
    }

    private fun navItem(title: String, init: (LinearLayout, TextView, View) -> Unit): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
        }
        val dot = View(this).apply {
            background = dotColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(3))
        }
        item.addView(dot)
        val label = text(title, 13, false, muted)
        item.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        init(item, label, dot)
        return item
    }

    private fun selectTab(home: Boolean) {
        currentTabHome = home
        homePanel?.visibility = if (home) View.VISIBLE else View.GONE
        settingsPanel?.visibility = if (home) View.GONE else View.VISIBLE
        homeTabLabel.setTextColor(if (home) brand else muted)
        homeTabLabel.setTypeface(typeface, if (home) Typeface.BOLD else Typeface.NORMAL)
        homeTabDot.background = dotColor(if (home) brand else Color.TRANSPARENT)
        settingsTabLabel.setTextColor(if (!home) brand else muted)
        settingsTabLabel.setTypeface(typeface, if (!home) Typeface.BOLD else Typeface.NORMAL)
        settingsTabDot.background = dotColor(if (!home) brand else Color.TRANSPARENT)
    }

    private fun card(host: LinearLayout, block: (LinearLayout) -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.WHITE, 18)
            elevation = dp(3).toFloat()
        }
        block(c)
        host.addView(c, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
    }

    private fun rebuildFrequencyOptions() {
        val options = frequencyOptions ?: return
        options.removeAllViews()
        val current = currentFrequency()
        ReminderFrequency.entries.forEach { frequency ->
            val selected = frequency == current
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(if (selected) Color.rgb(255, 245, 235) else Color.TRANSPARENT)
                    cornerRadius = dp(12).toFloat()
                    if (selected) setStroke(dp(1), brand)
                }
                isClickable = true
                setOnClickListener {
                    prefs.edit().putString(TargetApps.FREQ_KEY, frequency.name).apply()
                    updateCustomMinutesEnabled()
                    rebuildFrequencyOptions()
                }
            }
            row.addView(text(ReminderTexts.frequencyLabel[frequency] ?: frequency.name, 15, selected, if (selected) brandDark else inkSoft),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (selected) row.addView(dot(brand, 10))
            options.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }

    private fun updateCustomMinutesEnabled() {
        val input = customMinutesInput ?: return
        val enabled = currentFrequency() == ReminderFrequency.CUSTOM
        input.isEnabled = enabled
        input.alpha = if (enabled) 1f else 0.4f
    }

    private fun settingsText(label: String, key: String, fallback: String, maxLength: Int, multiline: Boolean = false): View {
        val current = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: fallback
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        container.addView(text(label, 14, true, inkSoft))
        val input = EditText(this).apply {
            setText(current)
            setSelection(current.length)
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            setTextColor(ink)
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 249, 252))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(229, 231, 239))
            }
            if (multiline) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                gravity = Gravity.TOP or Gravity.START
                minLines = 1
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine(true)
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString(key, s?.toString()?.trim().orEmpty()).apply()
                if (::previewTitle.isInitialized) updatePreview()
            }
        })
        container.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        val resetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        resetRow.addView(secondaryButton("恢复默认") {
            prefs.edit().remove(key).apply()
            input.setText(fallback)
            input.setSelection(fallback.length)
            updatePreview()
        })
        container.addView(resetRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        return container
    }

    private fun buildPreview(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(18), dp(16), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(223, 226, 241))
            }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previewTitle = TextView(this).apply {
            textSize = 20f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        titleRow.addView(previewTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(TextView(this).apply {
            text = "×"
            textSize = 24f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(4), 0)
        })
        card.addView(titleRow)

        previewHint = TextView(this).apply {
            textSize = 14f
            setTextColor(orange)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        card.addView(previewHint)

        previewEntertain = previewChip("", Color.rgb(91, 101, 230), Color.WHITE)
        card.addView(previewEntertain, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(12) })
        previewSearch = previewChip("", Color.rgb(238, 241, 250), Color.rgb(51, 59, 83))
        card.addView(previewSearch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(12) })

        container.addView(card)

        val bubbleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            setPadding(0, dp(10), 0, 0)
        }
        previewBubble = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(48, 56, 87))
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(223, 226, 241))
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }
        bubbleRow.addView(previewBubble)
        container.addView(bubbleRow)
        return container
    }

    private fun previewChip(text: String, bg: Int, fg: Int) = TextView(this).apply {
        setText(text)
        textSize = 16f
        setTextColor(fg)
        gravity = Gravity.CENTER
        background = rounded(bg, 16)
    }

    private fun updatePreview() {
        if (!::previewTitle.isInitialized) return
        val title = readText(TargetApps.TEXT_TITLE, ReminderTexts.TITLE).replace("{app}", "小红书")
        previewTitle.text = title
        val hint = readText(TargetApps.TEXT_HINT, ReminderTexts.HINT)
        previewHint.visibility = if (hint.isEmpty()) View.GONE else View.VISIBLE
        if (hint.isNotEmpty()) previewHint.text = hint
        previewEntertain.text = readText(TargetApps.TEXT_ENTERTAIN, ReminderTexts.ENTERTAIN)
        previewSearch.text = readText(TargetApps.TEXT_SEARCH, ReminderTexts.SEARCH)
        previewBubble.text = readText(TargetApps.TEXT_BUBBLE, ReminderTexts.BUBBLE)
    }

    private fun readText(key: String, fallback: String): String {
        val raw = prefs.getString(key, null)?.trim()
        return raw?.takeIf { it.isNotEmpty() } ?: fallback
    }

    private fun currentFrequency(): ReminderFrequency {
        val stored = prefs.getString(TargetApps.FREQ_KEY, null)
        return ReminderFrequency.entries.firstOrNull { it.name == stored }
            ?: ReminderFrequency.FIRST_ENTRY
    }

    private fun updateServiceStatus() {
        val enabled = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name == TargetAppAccessibilityService::class.java.name }
        val connected = TargetAppAccessibilityService.isConnected
        serviceStatus.text = when {
            enabled && connected -> "已开启并已连接：正在识别目标应用"
            enabled -> "系统开关已开启，但服务未连接。请关闭后再开启一次。"
            else -> "未开启：需要你在系统设置中手动授权"
        }
        serviceStatus.setTextColor(if (enabled && connected) green else orange)
        serviceDot.background = dotColor(if (enabled && connected) green else orange)
    }

    private fun updateEventStatus() {
        val latest = prefs.getString("last_event", "尚未收到应用切换事件")
        val target = prefs.getString("last_target_event", "尚未收到目标应用事件")
        eventStatus.text = "窗口：$latest\n目标：$target"
    }

    private fun text(value: String, size: Int, bold: Boolean, color: Int, top: Int = 0) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (top > 0) setPadding(0, dp(top), 0, 0)
    }

    private fun primaryButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(brand, 14)
        setOnClickListener { action() }
    }

    private fun secondaryButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        textSize = 13f
        setTextColor(muted)
        background = rounded(Color.rgb(241, 242, 247), 12)
        setOnClickListener { action() }
    }

    private fun dot(color: Int, sizeDp: Int) = View(this).apply {
        background = dotColor(color)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun dotColor(color: Int) = GradientDrawable().apply {
        setColor(color)
        shape = GradientDrawable.OVAL
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(Color.rgb(232, 234, 241))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}