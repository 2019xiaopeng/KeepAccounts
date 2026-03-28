# ADR 014: 使用官方 SplashScreen 替代自定义 SplashActivity，并修正聊天输入框行为

- Status: Accepted
- Date: 2026-03-28

## Context
出现两个明确问题：
1. 启动阶段出现两次闪屏（系统启动动画 + 自定义 SplashActivity）。
2. 聊天输入框存在额外半透明外层包裹，且文本超过一行时不换行，发送按钮对齐不稳定。

## Decision
1. 移除自定义 `SplashActivity`，采用 Jetpack 官方 `androidx.core:core-splashscreen`。
2. 启动主题改为继承 `Theme.SplashScreen`，通过 `windowSplashScreenAnimatedIcon`、`windowSplashScreenBackground` 和 `postSplashScreenTheme` 配置。
3. 在 `MainActivity.onCreate` 中于 `setContent` 前调用 `installSplashScreen()`。
4. 聊天输入区移除最外层半透明容器，输入框改为 1~4 行自动换行，发送按钮固定顶部对齐。
5. 更新应用图标前景为用户提供素材，保证桌面图标与系统启动动画图标一致。

## 操作过程
1. `app/build.gradle.kts`：新增 `implementation("androidx.core:core-splashscreen:1.0.1")`。
2. `AndroidManifest.xml`：Launcher 入口回归 `MainActivity`，并绑定启动主题。
3. `themes.xml`：新增 `Theme.KeepAccounts.Starting`（`Theme.SplashScreen`）。
4. `MainActivity.kt`：调用 `installSplashScreen()`。
5. 删除 `SplashActivity.kt` 与旧 `splash_window_background.xml`。
6. 图标资源改造：`ic_launcher_foreground.xml`、`ic_launcher_background.xml`。
7. `ChatScreen.kt`：输入框多行、去外层背景、发送按钮顶部对齐。

## 判断与取舍
### A. 为什么不用延时跳转 SplashActivity
- Android 12+ 系统已强制启动动画，额外 SplashActivity 会造成“双重启动视觉”。
- 官方库方案兼容且更符合现代 Android 启动规范。

### B. 为什么把启动图标与应用图标统一
- 启动动画默认展示应用图标，若两者不一致会造成品牌割裂。
- 统一资源可减少维护成本。

### C. 为什么输入框限制到最多 4 行
- 兼顾可读性与输入效率，避免底部输入区过高挤压对话内容。

## Consequences
- 正向：
  - 消除二次闪屏，启动体验更顺滑。
  - 聊天输入行为符合常见 IM 预期。
  - 启动动画图标与桌面图标一致。
- 代价：
  - 需维护一套 SplashScreen 主题配置。
  - 如需复杂启动逻辑，应通过 `setKeepOnScreenCondition` 管理而非延时页。

## Next
1. 若后续有冷启动耗时任务，使用 `setKeepOnScreenCondition` 绑定真实初始化状态。
2. 可根据品牌规范补充单色 `monochrome` 图标以支持系统主题图标。
