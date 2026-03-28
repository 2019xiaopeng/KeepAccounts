# ADR 010: 账本基础设置改为可编辑并持久化

- Status: Accepted
- Date: 2026-03-28

## Context
当前“账本基础设置”仅为静态展示文案：默认币种、默认账本、提醒时间不能编辑，应用重启后也不存在可恢复配置。

## Decision
1. 将账本基础设置纳入 DataStore（Preferences）持久化。
2. 在设置页提供可编辑表单并进行输入校验后保存。
3. 通过 `MainActivity` 统一读取 `settingsFlow` 并向 `AppSettingsScreen` 下发当前值。

## 操作过程
1. 在 `UserSettingsRepository` 中新增：
   - `ledger_currency`
   - `default_ledger_name`
   - `reminder_time`
2. 扩展 `UserSettingsState`：增加 `ledgerCurrency/defaultLedgerName/reminderTime`，并设置默认值。
3. 新增 `saveLedgerSettings(...)`，统一保存三个字段。
4. 修改 `MainActivity`：
   - 订阅并缓存以上三个字段。
   - 在设置页 `SETTINGS_TYPE_LEDGER` 路由中传入字段与保存回调。
5. 修改 `AppSettingsScreen`：
   - 将账本设置从静态卡片改为可编辑输入框。
   - 增加时间格式校验 `HH:mm`。
   - 保存后调用回调并提示结果。

## 判断与取舍
### 为什么用 DataStore
- 这三项都是轻量级用户偏好，Key-Value 模型更直接。
- 不需要为配置项引入 Room 表结构和迁移负担。

### 为什么通过 MainActivity 统一回写
- 现有用户设置（主题、头像、AI 配置、分类）已在同一路径处理。
- 复用既有架构可减少分散写入与状态不一致风险。

## Consequences
- 正向：账本基础设置可编辑、可恢复，重启后状态一致。
- 代价：设置项数量增加，后续若扩展复杂策略需考虑拆分配置模型。

## Next
1. 在首页/账本页接入 `reminderTime` 的真实提醒调度。
2. 将 `ledgerCurrency` 贯通金额展示格式化。
3. 支持多账本实体化（若进入多账本场景，再评估迁移至 Room）。
