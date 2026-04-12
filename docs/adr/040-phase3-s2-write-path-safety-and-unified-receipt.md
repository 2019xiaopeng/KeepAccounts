# ADR-040: Phase3-S2 写路径安全与统一回执策略

- 状态：Accepted
- 日期：2026-04-08
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step2 (P3-S2)
- 前置 ADR：ADR-038、ADR-039

## 背景

P3-S2 需要在 S1 工具编排底座上完整支持 create/update/delete，并满足以下约束：

1. 写操作必须先 preview，再执行真实工具。
2. 批量写入需要 partial_success，不因单条失败中断全量。
3. delete 需要影响范围预览和显式确认。
4. 回执结构需统一为 successCount/failureCount/items/errors，供聊天页、账本页、编辑页共享消费。

## 决策

### 1) 统一写路径：preview-first + tool execute

- 所有写草稿统一进入 `LedgerAgentOrchestrator`。
- 每条草稿先调用 `PREVIEW_ACTIONS`，通过校验后再调用真实工具。
- create/update/delete 均通过同一适配接口 `WriteToolAdapter` 执行，禁止绕过 orchestrator 直写 DAO。

### 2) delete 两阶段安全协议

- 第一步：生成删除计划并返回预览，不落库。
- 第二步：仅在用户显式确认后执行删除。

确认触发规则：

- 用户输入包含“确认删除”时视为确认。
- 高风险删除默认需要确认，判定条件：
  - 命中条数 > 3，或
  - 删除金额绝对值合计 >= 2000。

### 3) 批量 partial_success 作为默认语义

- 单条执行失败只影响该条，其他条继续执行。
- 聚合回执输出 successCount/failureCount/items/errors。
- `items` 保留每条状态与 failureReason；`errors` 汇总顶层失败原因，便于页面统一展示。

### 4) 回执统一结构

- AI 网关草稿与仓储解析支持 `transactionId` 透传。
- UI 统一消费 `AiChatReceiptSummary`，并新增顶层 `errors` 展示。
- delete 成功回执支持受影响交易列表，确保批量删除可正确统计 successCount 与交易绑定。

## 影响

正向影响：

- 写路径行为一致，可追踪且可回放。
- delete 风险显式可控，降低误删概率。
- 多页面共享同一回执语义，减少解析分叉。

成本与权衡：

- delete 增加一步确认流程，交互链路变长。
- 顶层 `errors` 与 item 级 failureReason 同时保留，短期会有字段冗余，但可保持向后兼容。

## 备选方案与否决原因

1. 允许低风险删除直接执行，不做预览。
- 否决原因：违背 S2 的 preview-first 约束，且误删不可逆。

2. 仅保留 item.failureReason，不引入顶层 errors。
- 否决原因：跨页面展示和统计成本高，且难以统一总览错误提示。

3. delete 仅支持单条 id 精确删除。
- 否决原因：无法满足批量自然语言删除场景（如“删除最近两条餐饮记录”）。

## 验证与门禁

- 构建：`:app:compileDebugKotlin` 通过。
- 单测：`:app:testDebugUnitTest` 通过。
- 集成覆盖：新增 delete 预览与确认两阶段测试，验证预览拦截、确认后真实删除及回执计数。

## 后续

1. 在后续阶段补充可配置的风险阈值（按用户偏好或账本规模动态调整）。
2. 将 delete 计划摘要与确认 token 的展示策略进一步产品化。
3. 若未来回执字段稳定，可评估收敛 item.failureReason 与 errors 的重复信息。
