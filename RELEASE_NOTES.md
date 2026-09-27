# Codex Usage 1.0.0（测试版）

首个以 Codex Usage 名称发布的 Android 测试版。

- 支持英文、简体中文及跟随系统语言。
- 显示多个账号的 7 天、5 小时和 GPT Reserve 额度及接口提供的重置时间。
- 支持确认后使用 Reset，并在操作后刷新额度。
- 支持手动激活 5 小时窗口，以及按账号开启自动激活。
- 新增 5 小时重置前约 5 分钟提醒；保留额度提醒、后台同步和桌面小组件。
- 更新应用名称与图标。

**安装说明：** 下载 `Codex-Usage-v1.0.0-debug.apk`。需要 Android 8.0 或更新版本。这是调试签名测试包（`com.codex.quota.debug`）；只有相同签名和包名的旧测试版可以直接覆盖安装。卸载旧版会清除本地数据。

**已知限制：** Android 可能延迟后台提醒或自动激活；相关操作需要网络。界面只显示服务端实际提供的数据。重置前 5 分钟提醒尚待实机验证。

源码和构建说明见 [README](https://github.com/masterli0312/Codex-Usage#readme)。本项目基于 boudywho/codex-quota-android，采用 MIT 许可。
