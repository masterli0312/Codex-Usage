# Codex 任务完成提醒

这项功能把电脑上的 Codex 完成事件转发到 Codex Usage。每次安装 App 都会生成一个独立的随机 ntfy 主题；账号额度、OAuth 登录和后台额度同步不参与通知转发。

**首次使用必须在运行 Codex 的电脑上配对一次。仅安装手机 App 或登录账号不会收到电脑任务事件。**

**中国大陆用户使用任务提醒本身不要求开启 VPN。** 通知经独立的 ntfy 通道传输，不需要打开 ChatGPT 网页。电脑和手机必须能直连通知服务；网络或运营商限制仍会影响接收。账号登录、额度刷新、Reset 与激活功能需要访问 OpenAI，请分别检查相应服务的连通性。

## 使用方法

1. 在 App 的「设置 → 通知与提醒 → Codex 任务完成提醒」打开开关。
2. 点击「分享电脑安装包」，将 ZIP 传到运行 Codex 的 Windows 电脑，解压后双击 `setup.cmd`。
3. 安装程序会保留原有 Codex `notify` 回调，串接转发程序，并发送一条配对测试消息。
4. 重启 Codex。随后每轮任务结束时，手机会收到提醒。
5. 在手机上允许 App 后台运行、打开通知权限，并按需取消电池优化限制。

电脑端需要 Node.js 18 或更新版本。安装过 Codex CLI 的电脑通常已有 Node.js。安装程序也会查找 Codex 的本地 Node.js 运行时；若仍未找到，请从 [nodejs.org](https://nodejs.org/) 安装 LTS 版本后重试。

电脑只需在执行 Codex 任务时保持运行，不需要为了已经发送的通知持续开机。手机和电脑都需要能够访问通知服务。手机强行停止 App 后不会接收；重新打开 App 会恢复连接。通知缓存受 ntfy 服务保留期限限制，不能保证无限期补取。

## 提醒含义

官方 `agent-turn-complete` 表示一轮任务运行结束，并不保证所有要求都已成功完成。因此默认通知为「Codex 本轮任务已结束」，不会把错误或未完成的工作虚构成成功。

CLI、桌面端或其他客户端是否产生这个回调，取决于运行的 Codex 版本和配置。安装程序不会修改模型、网络、OAuth 或安全设置；现有 `notify` 语法不能安全识别时会退出，保留原配置。配置只有在 Codex 重新加载后才生效。

## 后台与断线处理

- 手机使用带常驻通知的前台服务接收 ntfy JSON 流，不依赖 Google Play Services。
- 网络中断时按 2–60 秒退避重连，从保存的服务端时间游标补取仍在缓存中的消息。
- 手机按事件身份去重，并在显示之前持久保存去重记录；重新连接不会反复通知同一事件。
- 电脑在发送前保存仅包含事件元数据的本地 outbox。失败消息会由后台计划任务约每分钟重试，或在下一轮 Codex 结束时重试。
- 禁用开关会停止连接；清除所有本地数据会清掉配对设置并停止服务。
- Android 和手机厂商的省电管理可能延迟或终止接收，常驻服务不能保证绝对实时。

## 隐私

默认使用公共 `ntfy.sh`。发送的是事件类型、用于去重的哈希和通知所需元数据，不包含 OpenAI access/refresh token、提示词、助手回复、文件内容或账号邮箱。服务运营方仍能看到连接 IP 和发送时间。

随机主题地址是访问凭据的一部分，不是服务端访问控制。请保管好配对地址和带 `pairing.json` 的 ZIP，不要提交到仓库或公开分享。手机配置不参加系统备份。可在 App 中更换为自己的 HTTPS ntfy 主题地址；本版不支持需要额外 Authorization Header 的服务。

卸载电脑端通知组件：在解压的安装包目录运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\Install.ps1 -Uninstall`。它会在当前回调仍属于本组件时恢复原回调，并移除重试任务；本地备份和 outbox 会保留供检查。随后重启 Codex。

## 开源参考与许可

- [ntfy](https://github.com/binwiederhier/ntfy)：发布与订阅协议，Apache-2.0。
- [ntfy-android](https://github.com/binwiederhier/ntfy-android)：前台服务接收、重连及 Android 后台限制的实现参考，Apache-2.0。
- [agent-notifications](https://github.com/777genius/agent-notifications)：Codex 完成事件和 webhook 的设计参考；本项目未内嵌或自动安装该项目。
- [OpenAI Codex 官方回调](https://github.com/openai/codex/blob/main/codex-rs/hooks/src/legacy_notify.rs)：实际事件格式与原回调调用方式。

本项目的 Windows 转发脚本和 Android 接入代码为独立实现，复用现有 OkHttp、协程和系统通知组件，无新增 Android 依赖。

## 开发验证

```powershell
node --test tools/task-notifications/notify.test.cjs
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

实机验证应覆盖：配对测试、真实 Codex 回调、锁屏接收、重复事件、断线补取、关闭开关及切换独立主题。

### 1.0.4 本地验证（2026-09-30）

- Android 单元测试 94 项、Windows 转发测试 5 项通过；Debug APK 构建成功。
- Android lint：0 个错误、187 个警告。英文与中文资源 key 对应，格式参数按各复数形式检查一致。
- Android 16 实机：配对测试、锁屏接收、关闭接收期间的缓存补取、重复事件去重通过。
- Codex 0.159.0 CLI 的真实任务结束回调已收到手机通知。桌面 UI 在重启加载新配置后仍需再验证一次，不能把 CLI 验证等同于所有客户端版本都已验证。
- 最后补充的“周额度耗尽时隐藏并停用 5 小时额度”逻辑已通过回归测试；该次构建时手机未连接，尚未覆盖安装到实机。
