package cn.zhida.focussearch

data class TargetApp(val id: String, val label: String, val packageName: String)

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
}
