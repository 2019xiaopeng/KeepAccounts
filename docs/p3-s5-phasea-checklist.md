# P3-S5 PhaseA 清单记录（契约 + 影子模式）

- 日期：2026-04-10
- 分支：feat/p3-agent-tools
- 承接基线：47ad20a
- 目标：在不改变现网执行路径的前提下，补齐 Planner V2 契约与影子对比埋点，为 PhaseB 灰度切流做数据准备。

## PhaseA 验收门槛

1. 现有 query/write/fallback 行为不变，用户可见回复不回退。
2. 每次本地路由可额外产出一条 `PLANNER_SHADOW` 质量样本。
3. 影子样本包含：legacy action、planner action、match 标记、置信度、风险级别、缺槽信息。
4. Planner 异常或无输出时不影响主流程（降级为 `SHADOW_NO_PLAN`）。
5. `:app:compileDebugKotlin` 通过，关键回归单测通过。

## 工作清单

## A1. 契约层（domain）

- [x] 新增 `PlannerInputV2`。
- [x] 新增 `PlannerIntentType` / `PlannerTargetMode` / `PlannerRiskLevel`。
- [x] 新增 `IntentPlanV2`。
- [x] 新增 `ToolCallEnvelope` / `ObservationEnvelope`。
- [x] 新增 `AgentPlanner` 与 `NoOpAgentPlanner`。

## A2. 执行链路影子埋点（repository）

- [x] 在 `ChatRepository.sendMessage` 前置调用 planner shadow（仅记录，不执行）。
- [x] query/write/fallback/fallback-blocked 路由均记录 shadow 对比结果。
- [x] 新增 action 归一化与 match/mismatch 判定。
- [x] metadata 写入 planner 置信度、风险、targetMode、missingSlots。

## A3. 质量指标扩展（agent quality）

- [x] `AgentQualityStage` 增加 `PLANNER_SHADOW`。
- [x] 影子记录沿用现有 `AgentQualityFeedbackRepository` 存储通道。
- [ ] 增加按 stage 维度聚合展示（PhaseA+1）。

## A4. 装配层（DI）

- [x] `DefaultAppContainer` 注入 `NoOpAgentPlanner`。
- [x] `ChatRepository` 显式启用 `plannerShadowEnabled = true`。

## A5. 测试与回归

- [x] 新增 planner contract/serialization 单测。
- [x] 新增 shadow 记录行为单测。
- [x] `:app:compileDebugKotlin` 通过。
- [x] `ChatRepositoryBatchLedgerTest` 定向回归通过。
- [x] 跑全量 `:app:testDebugUnitTest`。

## 实时变更日志

| 时间 | 变更 | 影响文件 | Commit |
| --- | --- | --- | --- |
| 2026-04-10 | 创建 PhaseA 清单记录 | `docs/p3-s5-phasea-checklist.md` | `b0d6a55` |
| 2026-04-10 | 接入 planner shadow 骨架（不改主执行） | `AgentToolContracts.kt` / `ChatRepository.kt` / `AgentQualityFeedbackRepository.kt` / `AppContainer.kt` | `12d616d` |
| 2026-04-10 | 新增 planner 契约序列化单测并完成全量回归 | `AgentPlannerContractsTest.kt` / `p3-s5-phasea-checklist.md` | 待提交 |

## 后续（进入 PhaseB 前）

1. 引入真实 `PlannerGateway`（Function Calling），替换 NoOp。
2. 增加 shadow 对比离线报表（intent 一致率、缺槽率、误判率）。
3. 以灰度开关切 query/stats 主路径，并保留一键回滚。
