# P3-S5 PhaseC 清单记录（高风险写操作 + PendingIntent + 放量门禁）

- 日期：2026-04-10
- 分支：feat/p3-agent-tools
- 承接基线：PhaseB+（fdba746 / b148670 / cff8aa7）
- 目标：
  1. 接入 planner 驱动的 update/delete/batch 执行能力。
  2. 强化两阶段删除（preview -> confirm -> commit）。
  3. 接入 PendingIntentState（TTL）支持多轮补全。
  4. 在放量入口增加双阈值门禁（misjudgeRate + mismatchSamples）。

## PhaseC 验收门槛

1. planner primary 可执行 `CREATE/UPDATE/DELETE`，且支持批量 write items。
2. 删除场景严格执行两阶段确认策略。
3. 缺槽位写意图可进入 pending，下一轮补齐后自动续执。
4. planner 放量命中后仍需通过门禁：
   - 样本量达到最小阈值时，`misjudgeRate` 不得超过上限。
   - `mismatchSamples` 不得超过上限。
5. 编译 + 定向单测 + 全量单测通过。

## 工作清单

## C1. Planner 写执行扩展（update/delete/batch）

- [x] `IntentPlanV2` 增加 `writeItems`，兼容 `createItems`。
- [x] `SiliconFlowPlannerGateway` schema/解析支持 `writeItems`。
- [x] `ChatRepository` 的 planner primary 执行路径支持 `CREATE/UPDATE/DELETE`。
- [x] 批量写入通过 `handleWriteIntent` 统一落地（保持已有批量回执渲染）。

## C2. 两阶段删除强化

- [x] delete 统一走 preview 校验。
- [x] 未确认时返回确认提示，不执行删除。
- [x] 明确确认后再执行 commit。

## C3. PendingIntentState（TTL）多轮补全

- [x] 新增 `PendingIntentStateStore` 抽象与内存实现。
- [x] `ChatRepository.sendMessage` 入口增加 pending 优先续执。
- [x] planner 校验失败且可恢复时，落盘 pending 状态并提问缺槽位。
- [x] 下一轮补齐后自动执行写入并清空 pending。

## C4. 放量门禁（双阈值）

- [x] `shouldUsePlannerPrimary` 增加 rollout gate 检查。
- [x] 从 observation report 聚合 planner primary 样本。
- [x] 按 `plannerGateMinSamples`、`plannerGateMaxMisjudgeRate`、`plannerGateMaxMismatchSamples` 决策是否放行。

## C5. 测试与验证

- [x] 更新 `PlannerOutputValidatorTest`：覆盖 writeItems create/delete 校验与 createItems 兼容回退。
- [x] 更新 `SiliconFlowPlannerGatewayTest`：覆盖 update intent + writeItems 解析。
- [x] 新增 `PendingIntentStateStoreTest`：覆盖 TTL 过期与 clear 行为。
- [x] 扩展 `ChatRepositoryBatchLedgerTest`：
  - pending 多轮补全闭环；
  - misjudgeRate 门禁拦截；
  - mismatchSamples 门禁拦截。
- [x] `:app:compileDebugKotlin` 通过。
- [x] 定向 `:app:testDebugUnitTest --tests ...` 通过。
- [x] 全量 `:app:testDebugUnitTest` 通过。

## 本轮关键改动文件

- `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowPlannerGateway.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/AgentToolContracts.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/PlannerOutputValidator.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/PendingIntentStateStore.kt`
- `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/data/repository/SiliconFlowPlannerGatewayTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/PlannerOutputValidatorTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/PendingIntentStateStoreTest.kt`

## 下一步（PhaseC 后续）

1. 将 pending 状态从内存实现升级到持久层（进程重启可恢复）。
2. 在观测面板新增 rollout gate 快照输出（allowed/reason/sampleCount/misjudgeRate/mismatchSamples）。
3. 补充 planner primary 的 delete 二次确认回放样例，用于发布前演练。