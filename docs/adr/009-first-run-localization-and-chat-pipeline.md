# ADR 009: 首次初始化与本地化数据闭环（含操作过程与判断）

- Status: Accepted
- Date: 2026-03-28

## Context
当前版本存在三类核心问题：
1. 首次进入应用没有初始化流程，用户称呼/头像、主题、AI 管家设定依赖运行时内存变量。
2. 聊天与账单存在展示数据和内存态数据，应用重启后无法稳定恢复。
3. AI 接口已有契约与 DTO，但从 local.properties 到真实流式网关的链路未形成完整闭环。

## Decision
1. 首次初始化信息（用户、主题、AI 管家、分类）采用 DataStore 持久化。
2. 聊天记录采用 Room 持久化，聊天发送/删除与 AI 回包统一收敛到 `ChatRepository`。
3. AI 网关从 `SiliconFlowAiGatewayStub` 迁移到真实 `SiliconFlowAiGateway`，接入 SSE 文本流与 `<DATA>` 回执解析。
4. 回执解析出的账单直接落库到 `TransactionEntity`，并通过 `ChatMessageEntity.transactionId` 建立关联。

## 操作过程（Implementation Steps）
1. 新增首次初始化页面 `InitialSetupScreen`，并新增路由 `INITIAL_SETUP`。
2. 在 `MainActivity` 中：
   - 启动时读取 `UserSettingsRepository.settingsFlow`。
   - 若 `initialized=false`，先进入初始化页面。
   - 初始化完成后保存 DataStore，并跳转主 Tab。
3. 新增 `UserSettingsRepository`：
   - 持久化 `initialized/userName/userAvatarUri/theme/aiConfig/manualCategories`。
   - 主题、个人设置、AI 设置、分类管理的改动写回 DataStore。
4. 新增 `ChatRepository` + `ChatViewModel`：
   - 聊天列表改为 Room `Flow` 驱动。
   - `sendMessage` 写入 user 消息 -> 调用网关流式回复 -> 解析 `<DATA>` -> 可选入账 -> 写入 assistant 消息。
   - 删除消息时同步删除关联交易（若存在 `transactionId`）。
5. 改造 `ChatScreen`：
   - 从“列表整体回写”切换为“事件回调发送/删除”。
   - 使用 `isSending` 控制打字中状态。
6. 改造账本页：
   - 背景改为基于主题色插值的渐变。
   - 排行榜切换取消左右平移动画，改为直接切换。
   - 明细列表移除随机 mock，直接使用真实交易数据分页。
7. 清理展示数据：
   - 移除交易自动 seed。
   - 移除首页进入对话时注入的硬编码示例文本。

## 判断与取舍（Why）
### A. DataStore vs Room（初始化配置）
- 选择 DataStore：
  - 配置字段天然是 Key-Value，写入频率低，读取频率高。
  - 避免为纯配置引入额外实体迁移复杂度。
- 未选 Room：
  - 对此场景过重，不利于快速迭代首次初始化体验。

### B. 聊天持久化走 Room 而非 MainActivity 内存态
- 选择 Room：
  - 可冷启动恢复、可审计、可被 AI 管理页按日历检索。
  - 与交易实体可做外键关联，支持“回执删改联动”。
- 未选内存态：
  - 状态易丢失，且跨页面同步成本高。

### C. AI 解析位置放在 Repository 层
- 选择 Repository：
  - 保持 Screen 仅关心 UI 展示，避免网络协议和解析逻辑渗入页面。
  - 便于后续扩展多模型策略与离线 fallback。
- 未选 Screen 侧解析：
  - 导致页面耦合网络协议，不利于测试与替换。

## Consequences
- 正向：
  - 首次使用流程与主配置具备可恢复性。
  - 聊天/账单/AI 回执形成本地化闭环。
  - UI 层与数据层职责边界更清晰。
- 代价：
  - 代码量和链路复杂度上升。
  - AI 回执字段目前仍以“可用优先”，后续可继续增强规则与异常处理。

## Next
1. 增加 `ChatMessageEntity` 的回执冗余字段（category/amount/desc/date）以提升回执卡片展示稳定性。
2. 增加 AI 网关错误重试与超时分层策略。
3. 为初始化流程与聊天入账链路补充集成测试。
