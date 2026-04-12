# ADR-038: Phase3 S1 Foundation with DB+JSONL Replay

- 状态：Accepted
- 日期：2026-04-08
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step1 (P3-S1)

## 背景

P3 需要从 prompt 直执迁移到可验证工具调用。S1 必须先解决三个底座问题：

1. 工具契约稳定且可扩展。
2. 调用链可追踪，出现误判可定位。
3. requestId 级别可离线 replay。

## 决策

1. 在 Domain 层定义六类工具契约（preview/create/update/delete/query/stats）。
2. 引入 LedgerAgentOrchestrator 作为写路径统一编排入口。
3. 每次调用必须落地双日志：
- Room：agent_runs、agent_tool_calls（主审计源）
- JSONL：逐行镜像（容灾与离线排障）
4. replay 先读 DB，必要时回退 JSONL。
5. 校验规则先实现三类硬校验：invalid amount、empty category、invalid time window。

## 备选方案与取舍

方案 A：仅 JSONL 文件日志
- 优点：实现简单。
- 缺点：查询性能和一致性差，不利于 App 内检索。

方案 B：仅 Room 日志
- 优点：结构化查询友好。
- 缺点：当数据库异常或迁移失败时缺少旁路证据。

方案 C：DB + JSONL（采纳）
- 优点：结构化查询与旁路容灾兼得，便于 replay 与审计。
- 成本：实现复杂度略高，需要维护双写一致性。

## 影响

正向影响：
- 工具调用路径可复核，便于 T3 与后续质量治理。
- 编排边界清晰，为 P3 后续 delete/query/stats 落地留出稳定接口。

代价与约束：
- 需要 Room 2->3 迁移。
- 需要额外维护 JSONL 文件生命周期。

## 验证

1. :app:compileDebugKotlin 通过。
2. :app:testDebugUnitTest 通过。
3. 新增 JUnit4 用例覆盖：
- 参数校验
- 编排顺序
- replay（DB 与 JSONL 回退）
