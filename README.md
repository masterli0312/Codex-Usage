# Codex Usage

在 Android 手机上查看多个 ChatGPT/Codex 账号的额度状态、重置时间和订阅信息。支持简体中文与英文。

[下载 1.0.1 测试版](https://github.com/masterli0312/Codex-Usage/releases/tag/apk-1.0.1-debug-r2) · [查看全部版本](https://github.com/masterli0312/Codex-Usage/releases) · [上游项目](https://github.com/boudywho/codex-quota-android)

> 当前公开的 APK 是 **1.0.1 调试签名测试版**，需要 Android 8.0 或更高版本。安装前请阅读[安装与升级](#安装与升级)。

本项目基于 [boudywho/codex-quota-android](https://github.com/boudywho/codex-quota-android)，采用 MIT 许可。以下说明结合上游文档与本仓库 **1.0.1** 的实际代码和页面整理。

## 目录

- [主要功能](#主要功能)
- [额度数据的含义](#额度数据的含义)
- [添加账号与日常使用](#添加账号与日常使用)
- [5 小时额度激活](#5-小时额度激活)
- [设置与提醒](#设置与提醒)
- [安装与升级](#安装与升级)
- [隐私与安全](#隐私与安全)
- [从源码构建](#从源码构建)
- [项目结构](#项目结构)
- [已知限制](#已知限制)
- [English](#english)
- [许可与声明](#许可与声明)

## 主要功能

| 页面或功能 | 1.0.1 的行为 |
| --- | --- |
| 首页 | 以卡片列出多个账号的昵称、套餐、状态、7 天额度、5 小时额度、GPT Reserve、官方 Credit、Reset 机会、订阅日期和更新时间。 |
| 账号详情 | 查看各额度及重置倒计时、手动刷新、订阅与续费信息；在适用时手动激活 5 小时额度或使用 Reset。 |
| 登录 | 支持 ChatGPT Device Code 授权、API Key 账号和仅供演示的模拟账号。 |
| 设置 | 语言、外观与主题、后台同步、通知与提醒、隐私与本地存储。 |
| 桌面小组件 | 提供单账号和多账号的额度展示。 |

界面上的数据因账号和接口返回内容而异。例如，某个账号可能有 GPT Reserve，另一个账号没有；没有返回的数据会显示“不可用”，不会生成虚构的百分比或金额。

## 额度数据的含义

- **7 天额度 / 5 小时额度**：ChatGPT/Codex 使用额度响应中的剩余百分比。页面倒计时根据该响应的重置时间计算。重置后仍需再次获取数据，才会显示服务端的新状态。
- **GPT Reserve**：仅从可识别的 `gpt-reserve` 附加额度周窗口读取；不把普通周额度当作 GPT Reserve。
- **官方 Credit**：服务端返回的 Credit 余额（如有），与额度百分比、Reset 机会分别显示。它不等于估算的周额度。
- **Reset 机会**：服务端提供可用次数时才显示数值。账号详情中的“使用 Reset”需要确认，提交后重新读取服务端数据；无机会时按钮禁用。
- **订阅信息**：显示套餐、接口提供的订阅日期和自动续费状态；缺失时可使用本机保存的自定义日期或显示不可用。
- **API Key 账号**：使用开发者 API Key 的验证与速率限制数据。订阅账号的 7 天/5 小时窗口和 Reset 信息不能从普通 API Key 推算。

应用**不提供**历史 Token 用量统计、美元等价额度估算、可信度评分、云端账号探测或服务器中转。额度百分比也不是 OpenAI 账单余额。

## 添加账号与日常使用

1. 在首页点击 **+**，选择 **ChatGPT Device Code**、**API Key** 或演示账号。
2. 使用 Device Code 时，按界面提示在授权网页输入代码；完成后返回应用。API Key 账号按界面提示添加密钥。
3. 首页查看各账号概况；点击卡片进入详情，使用刷新按钮读取最新状态。
4. 在详情页查看订阅到期信息，按需使用 Reset 或移除账号。

登录、额度刷新、Reset 和主动激活均要求手机能够访问相应的 OpenAI 服务。新登录账号会保存 OAuth 续期所需凭据；较早添加且缺少续期凭据的账号可能需要重新授权。演示账号的数据只用于预览。

## 5 小时额度激活

**刷新**会重新读取服务端额度；**激活**会在旧的 5 小时窗口到期后发送一次小型 Codex 请求，以启动新窗口。激活会消耗少量额度。

- **手动激活**：在账号详情页点击“立即激活 5 小时额度”并确认。按钮只在旧窗口到期且新窗口尚未开始时可用。新窗口即使显示 **100%**，只要服务端返回未来的重置时间，按钮也会禁用。
- **自动激活**：在“设置 → 后台同步”开启定期同步，再逐个账号选择。默认没有账号开启自动激活。
- **防重复**：请求发送前会重新读取额度；若自动激活已使新窗口开始，手动操作不会再发送请求。对同一账号、同一窗口还会在本地记录一次请求，网络结果不确定时不会自动重复消费。

Android 的后台任务可能延迟，自动激活不能保证在重置瞬间执行。激活结果以服务端返回的额度和重置时间为准。

## 设置与提醒

| 设置页 | 可用选项 |
| --- | --- |
| 语言 | 跟随系统（默认）、简体中文、English。 |
| 外观与主题 | 跟随系统、浅色、深色；Material You 动态颜色或纯色主题。 |
| 后台同步 | 定期同步、按账号选择自动激活、15 分钟 / 30 分钟 / 1 小时 / 3 小时同步间隔。 |
| 通知与提醒 | 低额度、5 小时、7 天、GPT Reserve、5 小时重置前提醒等；没有可靠数据源的提醒项会标为不可用。 |
| 隐私与本地存储 | 本地加密和直连说明；清除全部本地数据与密钥。 |

低额度提醒根据真实额度数据判断，默认阈值为剩余 **25%、10%、5%**，同一窗口的重复提醒会去重。5 小时重置前提醒的目标时间为约 **5 分钟前**。Android 13 及以上需要通知权限；通知声音、震动由系统通知渠道管理。省电策略、网络与后台调度可能延迟通知。

账号详情仍有“订阅与续费”信息展示。设置里的“续费保障”提醒页已移除，因为当前数据源不支持那些提醒操作。

## 安装与升级

1. 从 [当前 1.0.1 测试版页面](https://github.com/masterli0312/Codex-Usage/releases/tag/apk-1.0.1-debug-r2) 下载 `Codex-Usage-v1.0.1-debug.apk`。
2. 在 Android 8.0（API 26）或更新系统上打开 APK，按提示允许该来源安装。
3. 首次打开后添加账号，并按需授予通知权限。

当前 APK 为**调试签名测试包**，包名 `com.codex.quota.debug`，显示版本 **1.0.1**，内部版本号 **23**。只有包名和签名均相同、且内部版本号更低的测试包可以直接覆盖安装。其他签名的 APK 不能覆盖；卸载旧版会清除该应用在本机保存的账号、登录凭据和设置。请以 GitHub Release 中的 APK 作为本测试版的安装来源。

## 隐私与安全

| 项目 | 实现 |
| --- | --- |
| 凭据存储 | OAuth Token 与 API Key 保存在设备本地的加密存储中，使用 Android Keystore 管理密钥。 |
| 网络请求 | 应用通过 HTTPS 直接访问 OpenAI 的认证、额度及相关接口，不经过本项目自建的中转服务器。 |
| 备份保护 | Android 备份与设备迁移规则排除敏感凭据文件。 |
| 遥测 | 项目不集成广告、分析或用户追踪 SDK。 |
| 数据清理 | “隐私与本地存储”页提供清除全部本地数据和密钥的操作。 |

请勿把 Token、密码、密钥库或其口令提交到仓库和 Issue。

## 从源码构建

需要 **JDK 17 或 21**、**Android SDK 35**。项目附带 Gradle Wrapper，可用 Android Studio 打开。

```bash
git clone https://github.com/masterli0312/Codex-Usage.git
cd Codex-Usage
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows 使用 `gradlew.bat`。调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，无需发布签名。

发布构建需要在 Git 忽略的 `signing.properties` 中配置：

```properties
storeFile=path/to/release.keystore
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

也可以使用 `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 环境变量。之后运行 `./gradlew assembleRelease`；缺少凭据时构建会失败。不要提交签名文件或密码。

推送到 `main` 或提交 Pull Request 时，GitHub Actions 会执行单元测试、Android lint 和 Debug 构建。仓库还保留按 `v*` 标签构建正式签名包的工作流，需要另行配置发布签名 Secrets；目前公开提供的 1.0.1 APK 是调试签名测试版。

## 项目结构

```text
app/src/main/java/com/codex/quota/
├── auth/           Device Code、OAuth 与令牌处理
├── data/           本地数据库、偏好设置与远端数据源
├── domain/         账号/额度模型与业务用例
├── notifications/  额度及登录状态通知
├── security/       Android Keystore 和凭据加密
├── ui/             Compose 页面、组件、导航与主题
├── widget/         桌面小组件
└── worker/         WorkManager 同步、提醒与窗口任务
```

主要技术：Kotlin、Jetpack Compose / Material 3、Room、DataStore、WorkManager、AndroidX AppCompat Locale API、OkHttp。

## 已知限制

- OpenAI 接口并非对所有账号返回 GPT Reserve、官方 Credit、Reset 机会或订阅字段；缺失时显示不可用。
- 后台同步、自动激活和提醒受 Android 调度、网络与账号状态影响，可能延迟或失败。5 小时重置前提醒仍需更多设备实测。
- 登录失效或旧账号缺少 OAuth 续期凭据时，可能需要重新授权。
- 当前没有 Token 用量日志、美元余额估算或服务器独立探测；请勿把额度百分比当作账单金额。
- 目前公开 APK 是调试签名测试版，尚未提供正式签名包。

## English

Codex Usage is an Android app for viewing server-reported quota status across multiple ChatGPT/Codex accounts. It shows 7-day and 5-hour limits, GPT Reserve when available, reset countdowns, official Credit and Reset opportunities when returned by the service, subscription dates, notifications and widgets. The UI supports English and Simplified Chinese.

Manual activation sends a small Codex request after a 5-hour window expires. Per-account automatic activation is opt-in and depends on Android background scheduling. The app stores credentials locally with Android Keystore-backed encryption and connects directly to OpenAI over HTTPS. It does **not** collect Token usage logs, estimate dollar balances or use a relay server.

Download the [1.0.1 debug test APK](https://github.com/masterli0312/Codex-Usage/releases/tag/apk-1.0.1-debug-r2). It requires Android 8.0+ and uses package `com.codex.quota.debug`. The Chinese sections above include installation, upgrade, build and signing instructions.

## 许可与声明

本项目基于 [boudywho/codex-quota-android](https://github.com/boudywho/codex-quota-android)，按照 [MIT License](LICENSE) 分发。OpenAI、ChatGPT、Codex、GPT 等名称归其权利人所有。本项目与 OpenAI 没有隶属、授权或背书关系。
