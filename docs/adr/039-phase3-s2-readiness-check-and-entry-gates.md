# ADR-039: Phase3-S2 Readiness Check and Entry Gates

- 状态：Accepted (Ready with gated gaps)
- 日期：2026-04-08
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step2 (P3-S2)
- 前置基线：feat/p3-agent-tools @ be018a7（S1 已落地）

## 背景

P3-S2 目标是在 S1 底座上落地 create/update/delete 的完整工具执行，并与 P1 批量语义、P2 时间语义对齐。

在进入 S2 编码前，需要确认当前框架是否满足进入条件，并识别必须在 S2 首批实现中完成的缺口。

## 自检范围

对照 Prompt 3-2 的硬约束进行逐项检查：

1. 写操作必须先 preview，再真实调用。
2. 批量写入必须支持 partial_success。
3. update 必须兼容 P2 时间语义。
4. delete 批量必须具备影响范围预览与确认机制。
5. 回执模型必须包含 successCount/failureCount/items/errors。

同时执行构建与测试门禁：

- git merge-base 祖先校验（P1/P2 证据提交）
- :app:testDebugUnitTest

## 证据与判定

### A. 分支与基线门禁

- 当前分支：feat/p3-agent-tools
- P1 证据祖先：1f359ba -> PASS
- P2 证据祖先：e700b7d -> PASS

判定：PASS

### B. 写操作 preview 先行

- LedgerAgentOrchestrator 在每条草稿先执行 PREVIEW_ACTIONS，再进入 create/update。
- ChatRepository 写路径已接入 orchestrator。

判定：PASS

### C. partial_success 语义

- Orchestrator 与 ChatRepository 均保留 success/failure 分流与汇总。
- 现有批量用例可通过。

判定：PASS

### D. update 与 P2 时间语义兼容

- ChatRepository 仍沿用 P2 的时间语义解析与目标定位逻辑（今天/昨天/前天 + 模糊时段）。
- 相关单测通过。

判定：PASS

### E. delete 工具落地与高风险确认

- Domain 契约已定义 DeleteTransactionsArgs / DELETE_TRANSACTIONS。
- 当前 orchestrator WriteToolAdapter 尚未提供 delete 执行分支。
- 当前 DAO 仅有单笔 deleteTransactionById，无批量预览/确认机制。

判定：GAP（S2 首批必须补齐）

### F. 回执模型 errors 顶层字段

- 现有 AiChatReceiptSummary 包含 successCount/failureCount/items。
- 顶层 errors 字段尚未进入统一模型；当前错误主要体现为 item.failureReason。

判定：GAP（S2 首批必须补齐）

### G. 构建与测试门禁

- :app:testDebugUnitTest -> PASS

判定：PASS

## 决策

结论：当前框架可以开始 P3-S2 开发。

但必须将以下两项作为 S2 的“首批强制任务”：

1. 完整落地 delete_transactions 执行链路。
- 先 preview，后执行。
- 批量删除必须输出影响范围并要求确认。
- 高风险删除需拦截（例如命中条数或金额超过阈值）。

2. 回执模型补齐 errors 顶层字段。
- 统一输出 successCount/failureCount/items/errors。
- 与现有 item.failureReason 做兼容映射，避免 UI 回归。

## S2 入口条件（执行清单）

开始 S2 前后需满足：

1. 所有 delete 调用通过 orchestrator，禁止绕过 preview 直删。
2. 增加 batch delete 预览与确认参数（建议 confirmToken 或 confirm=true 双阶段协议）。
3. 回执模型与 Chat/Ledger/Edit 页统一消费结构化回执（含 errors）。
4. 新增集成测试覆盖：
- 多笔入账部分成功
- 昨天午餐改价
- 晚饭无精确时间改价
- 批量删除预览与确认

## 影响

正向：

- S2 目标范围边界清晰，避免“边写边改口径”。
- 风险点提前显式化，降低返工概率。

风险：

- 若不优先补齐 delete 预览确认与回执 errors，S2 将在验收时集中失败。

## 后续动作

1. 以本 ADR 作为 P3-S2 的进入依据。
2. S2 第一批提交必须包含 delete 安全链与回执 errors 字段。
3. S2 完成后再补一份 ADR，总结写路径安全策略最终形态。
