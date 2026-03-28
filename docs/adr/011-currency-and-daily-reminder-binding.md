# ADR 011: 币种贯通与每日提醒调度落地

- Status: Accepted
- Date: 2026-03-28

## Context
在完成账本基础设置持久化后，仍有两个体验断点：
1. `ledgerCurrency` 仅保存未贯通，首页/账本/搜索/手动记账仍存在硬编码货币符号。
2. `reminderTime` 仅保存未执行，没有实际提醒行为。

## Decision
1. 增加统一金额格式工具，所有核心账务页面使用设置币种渲染金额。
2. 引入本地每日提醒调度：
   - `AlarmManager` 负责每日触发。
   - `BroadcastReceiver` 负责通知展示。
   - 开机/应用更新后自动恢复调度。
3. 在主流程中统一绑定：
   - 启动后按当前 `reminderTime` 自动重设提醒。
   - 用户修改提醒时间后立即重设提醒。

## 操作过程
1. 新增 `ui/format/CurrencyText.kt`：
   - `formatCurrency`
   - `formatSignedCurrency`
   - `primaryCurrencySymbol`
2. 将 `ledgerCurrency` 透传并应用到：
   - 首页 `HomeScreen`
   - 账本 `LedgerScreen`
   - 搜索 `SearchScreen`
   - 手动记账 `ManualEntryScreen`
3. 新增提醒组件：
   - `LedgerReminderScheduler`
   - `LedgerReminderReceiver`
   - `BootCompletedReceiver`
4. 在 `AndroidManifest.xml` 增加：
   - `POST_NOTIFICATIONS`
   - `RECEIVE_BOOT_COMPLETED`
   - 两个 Receiver 声明与开机广播过滤。
5. 在 `MainActivity` 中：
   - 首次初始化后根据 `settings.reminderTime` 调度提醒。
   - 对 Android 13+ 申请通知权限（一次性请求）。
   - 设置页保存提醒时间后立即重设调度。

## 判断与取舍
### A. 为什么用 AlarmManager 而不是 WorkManager
- 场景是固定本地时点提醒（每天 HH:mm），AlarmManager 更直接。
- WorkManager 适合可延迟、约束驱动的后台任务，不是本场景首选。

### B. 为什么加开机恢复 Receiver
- 仅在运行期调度会在重启后丢失。
- 开机恢复可保证提醒连续性，避免“设置成功但重启失效”。

### C. 为什么保留币种为自由文本
- 现阶段追求可配置快速落地，先满足“用户可见一致”。
- 后续若接汇率/本地化，可升级为标准币种代码模型。

## Consequences
- 正向：
  - 币种设置真正生效，账务展示一致。
  - 每日提醒形成可执行闭环。
- 代价：
  - 提醒依赖系统通知权限，拒绝授权时不会弹出通知。
  - 目前提醒内容为统一文案，后续可个性化。

## Next
1. 提醒内容与 `defaultLedgerName`、当日未记账状态联动。
2. 增加提醒开关（启用/禁用）并持久化。
3. 币种输入从自由文本升级为受控选项（如 CNY/USD/JPY）。
