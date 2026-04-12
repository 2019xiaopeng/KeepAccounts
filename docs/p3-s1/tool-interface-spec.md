# P3-S1 Tool Interface Spec

更新时间：2026-04-08
适用分支：feat/p3-agent-tools

## 1. 目标

P3-S1 在保持现有聊天能力可用的前提下，建立可校验的工具契约、编排骨架、日志链路与离线 replay 能力。

## 2. 工具契约（S1）

以下 6 类工具契约全部定义在 Domain 层（sealed + data class）：

1. preview_actions(actions[])
- 入参：action、amount、category、recordTime。
- 作用：执行前预演命中情况，决定是否继续执行写操作。

2. create_transactions(items[])
- 入参：批量记账项。
- S1 实现：已接入真实执行路径。

3. update_transactions(filters, patch)
- 入参：目标过滤条件与变更补丁。
- S1 实现：已接入真实执行路径。

4. delete_transactions(filters)
- 入参：删除过滤条件。
- S1 状态：仅完成契约定义，执行留到后续阶段。

5. query_transactions(filters)
- 入参：查询过滤条件。
- S1 状态：仅完成契约定义，执行留到后续阶段。

6. query_spending_stats(window, groupBy, metric)
- 入参：统计窗口、分组维度、指标。
- S1 状态：契约定义+窗口校验规则，执行留到后续阶段。

## 3. 编排骨架

入口：LedgerAgentOrchestrator.execute(...)

每条 draft 的执行顺序：
1. preview_actions
2. 参数校验
3. create_transactions 或 update_transactions
4. 写入 agent_tool_calls + JSONL 镜像

运行状态：
- SUCCESS
- PARTIAL_SUCCESS
- FAILED

## 4. 日志字段（必须）

agent_runs：
- requestId
- idempotencyKey
- userInput
- status
- startedAt
- endedAt
- errorCode
- errorMessage

agent_tool_calls：
- requestId
- runId
- stepIndex
- toolName
- argsJson
- resultJson
- status
- errorCode
- errorMessage
- latencyMs
- timestamp

JSONL 镜像：每行一条完整 tool call，字段与 agent_tool_calls 对齐。

## 5. Replay 能力

AgentReplayService 提供：
1. replayFromDatabase(requestId)
2. replayFromMirrorOrDatabase(requestId)

策略：
- 先使用数据库重建调用链。
- 若数据库调用链为空，自动回退到 JSONL 镜像。

## 6. 错误码映射（S1）

| ErrorCode | 语义 | 触发点 |
| --- | --- | --- |
| INVALID_AMOUNT | 金额非法（空/0） | create/update 参数校验 |
| EMPTY_CATEGORY | 分类为空 | create/update 参数校验 |
| INVALID_TIME_WINDOW | 时间窗口非法 | stats 参数校验 |
| TARGET_NOT_FOUND | 未定位到目标记录 | update 执行阶段 |
| VALIDATION_FAILED | 参数校验失败 | 编排层汇总 |
| TOOL_NOT_IMPLEMENTED | 工具仅定义未落地 | delete/query/stats 执行占位 |
| UNEXPECTED_ERROR | 未归类异常 | 执行兜底 |

## 7. 示例

请求：把刚才那笔咖啡改成12

调用链（按 stepIndex）：
1. PREVIEW_ACTIONS
2. UPDATE_TRANSACTIONS

重放断言：
- requestId 一致
- stepIndex 连续
- toolName 顺序一致
- argsJson/resultJson/errorCode 可复核
