# 别走神啦（Android）

**别走神啦** 是一个 Android 原生原型：当你打开小红书、抖音、哔哩哔哩或知乎时，先选择“娱乐”或“搜索”。选择搜索后，屏幕侧边会出现一只小猫，提醒你想清楚要搜什么。

> 当前版本为个人开发与真机验证中的 MVP。它不会自动打开平台搜索页，也不保证每次都能拦截应用首次启动。无障碍服务依赖用户明确授权，并受 Android 与手机厂商后台管理策略影响。

## 功能

- 监测用户启用的四个目标应用：小红书、抖音、哔哩哔哩、知乎。
- 目标应用进入前台时尝试显示“娱乐 / 搜索”选择层。
- “娱乐”关闭选择层，继续使用当前应用。
- “搜索”显示可手动关闭的小猫侧边提醒，不遮挡应用内容。
- 应用选择和窗口事件识别在设备本机完成；应用不联网，也不上传搜索内容或页面内容。
- 设置页显示无障碍服务状态、最近识别事件，并允许逐个平台开关。

## 截图与演示

目前仓库不包含真机截图或录屏。欢迎后续补充经过本人确认可公开的演示素材。

## 设备与兼容性

已知测试基线：Redmi Note 13 Pro、Android 14、系统版本 `1.0.16.0.UNRCNXM`。不同 MIUI/HyperOS 版本、目标应用版本和省电策略可能造成行为差异。无障碍服务只接收系统提供的部分窗口状态事件，不是系统级应用启动拦截器。

## 获取与安装

1. 前往 GitHub 项目 **Releases** 下载最新 `app-release.apk`。
2. 在 Android 设备上安装 APK；系统可能要求允许从当前来源安装应用。
3. 打开“别走神啦”，点击“前往开启无障碍服务”，在系统设置中手动启用该服务。
4. 回到应用，按需要开启四个平台的监测开关。
5. 若服务被系统回收，请检查系统的后台运行/电池限制设置，并重新进入无障碍设置确认服务状态。

只从你信任的发布来源安装 APK。首次启用无障碍服务前，请阅读应用说明并确认你理解其用途。

## 从源码构建

### 环境

- Android Studio
- JDK 17（Gradle JDK）
- Android SDK Platform 35
- Gradle Wrapper 8.9（项目内置）
- Android Gradle Plugin 8.7.3
- Kotlin Android Plugin 2.0.21

用 Android Studio 打开仓库根目录，等待 Gradle Sync 完成，然后运行 `app` 配置。也可以在 Windows PowerShell 中执行：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

Release APK 需要发布者使用自己的签名密钥生成。不要把签名密钥、密钥密码或 `local.properties` 提交到 GitHub。维护者应妥善备份签名密钥；后续版本必须继续使用同一签名身份。发布者可在 Android Studio 选择 **Build > Generate Signed App Bundle or APK...**，生成后将 APK 附加到 GitHub Release，而不是提交进源码仓库。

## 项目结构

```text
.
├── app/
│   ├── src/main/
│   │   ├── java/cn/zhida/focussearch/
│   │   │   ├── MainActivity.kt                    # 设置、权限引导和状态
│   │   │   ├── TargetAppAccessibilityService.kt   # 前台识别与无障碍覆盖层
│   │   │   └── TargetApps.kt                     # 目标平台与偏好设置键
│   │   ├── res/                                   # 图标、猫咪图、文案和服务配置
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── docs/ARCHITECTURE.md
├── PROJECT_BRIEF.md
├── gradle/                                        # Gradle Wrapper
├── LICENSE
└── README.md
```

更多实现边界和后续拆分建议见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 隐私与权限

应用通过 Android `AccessibilityService` 观察部分窗口状态变化，以判断目标应用是否进入前台，并使用无障碍专用覆盖层显示提示。该服务必须由用户在系统设置中手动启用。代码不会读取窗口文本，也没有网络权限或遥测实现。系统可能限制后台服务，因此触发不保证百分之百稳定。

## 已知限制与路线

- 仅支持当前列出的四个平台开关；包名和平台行为可能随应用更新变化。
- “搜索”只是提醒，不会代替用户搜索，也不会自动跳转到平台搜索界面。
- 无障碍事件可能在首次进入、系统切换动画或厂商后台限制场景下漏发。
- 后续应在目标手机记录系统版本、平台版本、授权状态，并按测试清单复测；只有真机验证成功的平台搜索直达才可以作为功能宣称。

## 许可证

本项目代码以 MIT License 开源，详见 [`LICENSE`](LICENSE)。平台名称、商标及 Android/Gradle 等第三方组件仍归其各自权利人所有，并受其各自许可约束。
