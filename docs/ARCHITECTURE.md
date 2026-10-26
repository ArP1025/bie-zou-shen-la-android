# 架构说明

## 当前模块边界

当前 MVP 使用单个 Android 应用模块和 Kotlin 原生组件，保持实现简单，便于个人维护与真机迭代。

### `MainActivity`

- 渲染设置页、四个平台开关和无障碍服务状态。
- 引导用户打开系统无障碍设置。
- 从 SharedPreferences 读取最近窗口事件摘要，帮助排查设备兼容性问题。

### `TargetAppAccessibilityService`

- 处理 Android 提供的窗口状态/窗口变化/窗口内容变化事件。
- 根据事件包名判断是否属于用户启用的目标平台。
- 显示“娱乐 / 搜索”无障碍覆盖层，并在搜索路径显示猫咪提醒。
- 维护本次目标应用会话状态，延迟处理短暂离开目标应用的事件，避免切换页面时重复弹窗。
- 只保存用于设置页排错的简短最近事件，不读取窗口文本，不发送网络请求。

### `TargetApps`

- 集中保存平台 ID、展示名称、Android 包名及 SharedPreferences 键。
- 当前平台清单：小红书 `com.xingin.xhs`、抖音 `com.ss.android.ugc.aweme`、哔哩哔哩 `tv.danmaku.bili`、知乎 `com.zhihu.android`。

### Android 资源与声明

- `AndroidManifest.xml` 声明主 Activity 和受系统绑定保护的 AccessibilityService。
- `res/xml/accessibility_service_config.xml` 配置监听事件类型与服务元数据。
- `res/drawable`、`res/mipmap`、`res/values` 保存猫咪绘图、应用图标、颜色和文本。

## 事件流程

```text
Android AccessibilityEvent
        │
        ▼
TargetAppAccessibilityService
        │ 包名是否属于目标平台？开关是否启用？本次会话是否已弹过？
        ├── 否 ──► 忽略或等待离开目标应用后重置会话
        └── 是 ──► 显示无障碍覆盖层
                     ├── 娱乐 ──► 移除选择层，继续使用目标应用
                     └── 搜索 ──► 移除选择层，显示可关闭猫咪提醒
```

## 权限与信任边界

- 用户必须自行在系统设置中开启无障碍服务；应用不能静默授权。
- 无障碍服务收到的窗口事件并不保证完整或准时，厂商电池管理可能停止服务。
- 当前只使用无障碍专用 overlay，不申请普通悬浮窗权限；最终行为依赖 Android 和厂商系统实际实现。
- 目标应用包名是静态配置。包名变化、分身应用、系统多用户和应用克隆尚未覆盖。
- 应用无网络权限/上传流程。若以后增加联网功能、AI 服务或遥测，必须先更新隐私说明、权限和用户告知。

## 适合后续迭代的拆分

当功能和测试范围增长后，可在不改变用户流程的前提下逐步抽离：

1. `TargetApp` 数据模型与目标平台仓库；
2. 前台识别/会话状态管理，便于单元测试重复触发与退出重置；
3. 选择层与猫咪提醒的独立 View/Overlay 管理器；
4. 设置存储接口，避免 Activity 与服务直接依赖同一组字符串键；
5. 设备兼容性测试矩阵和分平台行为适配层。

目前不引入这些抽象，避免小型原型过早复杂化。
