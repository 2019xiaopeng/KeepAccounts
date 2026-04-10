# P3-S5 PhaseB 清单记录（Planner 主路径灰度：query/stats/single-create）

- 日期：2026-04-10
- 分支：feat/p3-agent-tools
- 承接基线：3218aea
- 目标：在保留回滚能力的前提下，接入 Planner 主路径灰度开关，并优先切 query/stats 与单笔 create。

## PhaseB 验收门槛

1. 可配置主路径开关：`plannerPrimaryEnabled`、`plannerPrimaryRolloutPercent`、`plannerPrimaryMinConfidence`。
2. rollout 命中且置信度达标时，query/stats/single-create 优先走 planner 计划执行。
3. planner 无计划/低置信度/不支持意图时，自动回落既有本地路由，不影响线上行为。
4. 质量埋点可区分主路径来源（`PLANNER_PRIMARY` vs `AGENT_PRIMARY`）。
5. 编译、定向回归、全量单测通过。

## 工作清单

## B1. 主路径开关与灰度

- [x] `ChatRepository` 增加 planner primary 开关参数。
- [x] 增加百分比灰度桶逻辑（按 requestId 稳定分桶）。
- [x] 增加最小置信度门控。

## B2. Planner 主路径执行（受控）

- [x] 支持 planner 驱动 `QUERY_TRANSACTIONS`。
- [x] 支持 planner 驱动 `QUERY_SPENDING_STATS`。
- [x] 支持 planner 驱动 single `CREATE_TRANSACTIONS`。
- [x] 不支持意图（update/delete/batch/chitchat）保持旧路径。

## B3. 观测与路由标识

- [x] `AgentRoutePath` 增加 `PLANNER_PRIMARY`。
- [x] planner 主路径执行写入 `TOOL_EXECUTION` 质量样本。
- [x] shadow 路径保持不变，用于与旧路径对照。

## B4. DI 与默认策略

- [x] `AppContainer` 注入主路径参数（默认开关开启 + 10% rollout + 0.75 置信度）。
- [x] 当前仍使用 `NoOpAgentPlanner`，实际线上仍回落旧路径（安全）。

## B5. 测试与回归

- [x] 新增 planner 主路径 query 命中测试。
- [x] 新增 planner 主路径 single-create 命中测试。
- [x] 新增低置信度回落旧路径测试。
- [x] `:app:compileDebugKotlin` 通过。
- [x] `ChatRepositoryBatchLedgerTest` 通过。
- [x] 全量 `:app:testDebugUnitTest` 通过。

## 实时变更日志

| 时间 | 变更 | 影响文件 | Commit |
| --- | --- | --- | --- |
| 2026-04-10 | PhaseB 主路径灰度与执行逻辑 | `ChatRepository.kt` / `AgentToolContracts.kt` / `AgentQualityFeedbackRepository.kt` / `AppContainer.kt` | `31c87c9` |
| 2026-04-10 | PhaseB 主路径回归测试 | `ChatRepositoryBatchLedgerTest.kt` | `31c87c9` |
| 2026-04-10 | PhaseB 清单文档 | `p3-s5-phaseb-checklist.md` | 本提交 |

## 下一步（PhaseB+）

1. 引入真实 `PlannerGateway`（Function Calling）替代 `NoOpAgentPlanner`。
2. 增加 planner 输出合法化（字段缺失、非法 window、越界 topN）专项校验。
3. 增加按 routePath/stage/intent 的日报聚合视图，支撑灰度放量决策。
