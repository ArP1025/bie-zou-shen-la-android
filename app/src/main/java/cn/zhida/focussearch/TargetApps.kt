package cn.zhida.focussearch

data class TargetApp(val id: String, val label: String, val packageName: String)

enum class ReminderFrequency(val intervalMillis: Long) {
    FIRST_ENTRY(0L),
    FIVE_MINUTES(5 * 60_000L),
    TEN_MINUTES(10 * 60_000L),
    CUSTOM(0L)
}

data class AppReminderState(
    val packageName: String,
    var sessionActive: Boolean = false,
    var lastReminderAt: Long = 0L,
    var popupShowing: Boolean = false,
    var catShowing: Boolean = false,
    var guideShowing: Boolean = false,
    var lastForegroundAt: Long = 0L
)

object TargetApps {
    val all = listOf(
        TargetApp("xiaohongshu", "小红书", "com.xingin.xhs"),
        TargetApp("douyin", "抖音", "com.ss.android.ugc.aweme"),
        TargetApp("bilibili", "哔哩哔哩", "tv.danmaku.bili"),
        TargetApp("zhihu", "知乎", "com.zhihu.android"),
    )

    const val PREFS = "zhida_settings"
    const val ENABLED_PREFIX = "enabled_"
    const val SEARCH_PREFIX = "search_mode_"

    const val FREQ_KEY = "reminder_frequency"
    const val FREQ_CUSTOM_MINUTES = "reminder_custom_minutes"
    const val BOTTOM_GUIDE_ENABLED = "bottom_guide_enabled"
    const val TEXT_TITLE = "text_title"
    const val TEXT_ENTERTAIN = "text_entertain"
    const val TEXT_SEARCH = "text_search"
    const val TEXT_BUBBLE = "text_bubble"
    const val TEXT_HINT = "text_hint"

    const val TITLE_MAX = 60
    const val BUTTON_MAX = 12
    const val BUBBLE_MAX = 80
    const val HINT_MAX = 60
    const val CUSTOM_MINUTES_DEFAULT = 15
    const val CUSTOM_MINUTES_MIN = 1
    const val CUSTOM_MINUTES_MAX = 120
}

object ReminderTexts {
    val frequencyLabel = mapOf(
        ReminderFrequency.FIRST_ENTRY to "仅首次进入",
        ReminderFrequency.FIVE_MINUTES to "每 5 分钟",
        ReminderFrequency.TEN_MINUTES to "每 10 分钟",
        ReminderFrequency.CUSTOM to "自定义间隔",
    )
    const val TITLE = "这次打开{app}，你想做什么？"
    const val ENTERTAIN = "娱乐"
    const val SEARCH = "搜索"
    const val BUBBLE = "想想你是来搜什么的吧~"
    const val HINT = ""
}