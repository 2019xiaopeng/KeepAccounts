# ADR 034: 批量回执协议归一化

- Status: Accepted
- Date: 2026-04-06

## Context
P1 目标要求把“一条自然语言输入 -> 多条独立交易”打通，但现有 AI 回执链路长期按单条 `<DATA>` 处理，导致批量场景存在两个协议入口：
1. 模型连续输出多个 `<DATA>...</DATA>`。
2. 模型输出单个 `<DATA>{"items":[...]}</DATA>`。

如果应用层分别为两种形态写两套执行逻辑，会把解析、落库、结果展示和失败处理都分叉，后续很难保证部分成功语义和 UI 一致性。

## Decision
统一把批量回执归一化为 `List<AiReceiptDraft>`，后续执行链路只消费一种内存结构。

1. 流式网关在闭合一个 `<DATA>` 后立即解析：
   - 普通单条 payload 产出 1 个 draft。
   - 含 `items` 的 payload 产出 N 个 draft，并继承父级默认字段。
2. 仓库层在流结束后再次做文本兜底解析，兼容多个 `<DATA>` 与单个 `items` payload。
3. 应用执行层统一顺序处理 draft 列表，逐笔记录成功/失败，保留部分成功结果。
4. 回执展示统一回写为单个 `<RECEIPT>` 元数据，内含 `successCount`、`failureCount`、`items[]`，供聊天 UI 渲染批量结果卡片。

## Why A over B

### A: 先归一化为 draft 列表，再走单一路径
- 解析协议差异被收敛在边界层，仓库和 UI 不再关心原始回执长什么样。
- 可以自然复用“逐笔执行 + 部分成功 + 失败原因”的同一套模型。
- 更利于后续接入 Agent 工具执行，因为工具层本身也更接近 action list。

### B: 对两种协议分别写执行分支
- 仓库层和 UI 都要区分“多个标签”与“items 数组”，维护成本更高。
- 部分成功统计、删除绑定、回执卡片字段容易出现双份逻辑漂移。
- 每次新增字段都要同时改两条链路，回归风险更大。

## Consequences
- 正向：
  - 批量记账协议入口统一，新增协议字段时改动面更小。
  - 同一条 assistant 回执可以稳定挂接多条交易绑定与批量结果摘要。
  - T1 的“正常批量”与“异常注入后部分成功”都能走同一执行模型。
- 代价：
  - 聊天消息表需要额外保存多交易绑定信息。
  - 回执卡片从单条模型扩展为摘要 + 明细列表，UI 状态更复杂。

## References
- [ChatRepository.kt](file:///f:/code/Android/KeepAccounts/app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt)
- [SiliconFlowAiGateway.kt](file:///f:/code/Android/KeepAccounts/app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowAiGateway.kt)
- [ChatScreen.kt](file:///f:/code/Android/KeepAccounts/app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt)
- [development_plan_v1.2-v1.4.md](file:///f:/code/Android/KeepAccounts/docs/development_plan_v1.2-v1.4.md)
