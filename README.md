# Codex Usage

Android app for viewing the quota information available from your own ChatGPT/Codex accounts. This repository is based on [boudywho/codex-quota-android](https://github.com/boudywho/codex-quota-android) and retains its MIT license.

## 功能

- 同时查看多个账号的 7 天、5 小时和 GPT Reserve 额度；显示官方返回的重置时间和最近更新时间。某项数据未由接口提供时，界面会显示不可用，不会编造数值。
- 查看套餐、订阅到期日期，以及接口提供的 Reset 机会；使用 Reset 前需确认，完成后重新读取额度。
- 手动激活下一个 5 小时窗口，或按账号选择自动激活。激活会发出一次小型 Codex 请求并消耗少量额度；自动任务受 Android 后台调度和网络状态影响，可能延迟。
- 可选的 5 小时重置前 5 分钟提醒、低额度提醒、后台同步和桌面小组件。
- 英文及简体中文界面，可跟随系统或手动选择语言。

应用直接连接 OpenAI 服务，账号凭据保存在设备本地的加密存储中。使用额度和 Reset 数据取自服务端实际响应；本应用不提供 Token 统计、美元估算或服务器中转功能。

## 安装测试版

在 [最新 1.0.1 测试版](https://github.com/masterli0312/Codex-Usage/releases/tag/apk-1.0.1-debug-r2) 下载 **Codex-Usage-v1.0.1-debug.apk**。需要 Android 8.0（API 26）或更高版本。

当前发布的是**调试签名测试包**，应用包名为 `com.codex.quota.debug`。它可以升级同一台构建电脑签名、包名相同且内部版本号更低的测试包。其他签名的 APK 不能直接覆盖安装；卸载旧版会清除该应用的本地账号和凭据，请先确认是否需要保留。正式发布前应配置独立的发布签名并提供正式签名 APK。

登录、额度刷新和激活需要手机能够访问相应的 OpenAI 服务。系统可能延迟后台任务和通知，因此提醒与自动激活无法保证精确到分钟。

## 构建与检查

使用 JDK 17 或 21、Android SDK 35；在仓库根目录运行：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows 使用 `gradlew.bat`。生成的测试包位于 `app/build/outputs/apk/debug/app-debug.apk`。无需发布签名即可运行上述命令。

发布签名由根目录下忽略提交的 `signing.properties` 或环境变量 `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 提供。没有这些凭据时，`assembleRelease` 会拒绝生成包。请勿提交密钥或令牌。

## English

Codex Usage displays server-reported 7-day, 5-hour and GPT Reserve quota status for multiple accounts, with reset countdowns when the API provides reset times. It supports confirmed Reset use, manual or per-account automatic 5-hour activation, background sync, notifications, widgets, and English/Simplified Chinese localization.

Activation sends a small Codex request and consumes quota. Background work can be delayed by Android or network conditions. The app does not estimate dollar balances or collect token-usage logs. The APK linked above is a **debug-signed test build**, not a production-signed release.

## Attribution and license

Based on [Codex Quota Monitor for Android](https://github.com/boudywho/codex-quota-android). Distributed under the [MIT License](LICENSE). OpenAI, ChatGPT, Codex and GPT are trademarks of OpenAI; this project is independent and is not affiliated with or endorsed by OpenAI.
