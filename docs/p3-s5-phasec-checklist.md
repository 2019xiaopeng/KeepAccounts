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
- [x] 规则收口：仅显式 `transactionId` 且单条命中允许单阶段删除，其余场景强制二次确认。

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
- [x] 新增回归：显式 `transactionId` 单条删除可直接执行，无需确认。
- [x] 新增回归：planner primary update 在无金额 patch 时可更新非金额字段。
- [x] `:app:compileDebugKotlin` 通过。
- [x] 定向 `:app:testDebugUnitTest --tests ...` 通过。
- [x] 全量 `:app:testDebugUnitTest` 通过。

## C6. 语义归一化补全（金额/数量/时间）

- [x] 新增 `AmountNormalizer`，支持中文金额、口语金额与基础加减表达式。
- [x] 新增 `CountNormalizer`，支持中文数量词与计数短语归一化。
- [x] 新增 `TemporalResolverV2`，支持复杂相对时间解析并输出 `confidence/trace`。
- [x] `ChatRepository` 已接入上述组件（金额解析、删除数量解析、时间解析入口）。
- [x] 新增单测：`AmountNormalizerTest`、`CountNormalizerTest`、`TemporalResolverV2Test`。

## C7. 对话体验与上下文稳定性修复

- [x] 记账回复加入生活内容共情（示例：比赛/加班/出差场景）。
- [x] 备注提取规则修正，避免金额单位残留导致备注脏词（如“中午钱”）。
- [x] 助手多气泡回复增加间隔写入策略，改善“同时砸屏”体验。
- [x] 统计跟进问句支持继承上一轮窗口上下文（如“统计一下吧”承接“这周”）。
- [x] Chat UI 对隐藏负载消息增加过滤，避免 `<NOTE>` 原文泄漏。
- [x] 回归覆盖：`ChatRepositoryBatchLedgerTest` 新增对应场景断言。

## C8. 统计卡片与网关稳定性修复

- [x] 修复统计场景 `<NOTE>` 在多气泡分段时可能被截断的问题，确保 InsightCard 可渲染。
- [x] 查询异常 `resultJson` 改为 `JSONObject` 构造，避免字符串拼接导致的 JSON 转义风险。
- [x] AI 网络客户端新增 `connect/read/write/call timeout` 参数，降低“AI 服务响应超时”频率。
- [x] 默认模型由 `Pro/moonshotai/Kimi-K2.5` 切换为 `deepseek-ai/DeepSeek-V3`。
- [x] 新增 `SILICONFLOW_MODEL` 配置项，支持通过配置文件统一切换模型。

## C9. 更新纠错链路修复（命中目标 / 备注保护 / 动态确认）

- [x] `resolveUpdateTargetTransaction` 在缺少时间/分类线索时，不再对 120 条记录盲目平分择优；优先回溯最近回执绑定的 transaction。
- [x] update 备注写入增加显式意图门禁：仅当用户明确提到“备注/描述/说明”时才允许 patch，避免“不对不对，是14块”覆盖原备注。
- [x] update 成功文案改为动态变量注入，支持“已将{desc}修改为{amount}元”确认表达。
- [x] 新增回归：`ChatRepositoryBatchLedgerTest` 与 `AgentStyleFormatterTest` 覆盖上述场景。

## 本轮关键改动文件

- `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowPlannerGateway.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/AppContainer.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/AgentToolContracts.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/PlannerOutputValidator.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/PendingIntentStateStore.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/AmountNormalizer.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/CountNormalizer.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/TemporalResolverV2.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/agent/AgentStyleFormatter.kt`
- `app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt`
- `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/AgentStyleFormatterTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/data/repository/SiliconFlowPlannerGatewayTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/PlannerOutputValidatorTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/PendingIntentStateStoreTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/AmountNormalizerTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/CountNormalizerTest.kt`
- `app/src/test/java/com/qcb/keepaccounts/domain/agent/TemporalResolverV2Test.kt`
- `app/build.gradle.kts`
- `README.md`
- `docs/API_Docs.md`

## 下一步（PhaseC 后续）

1. 将 pending 状态从内存实现升级到持久层（进程重启可恢复）。
2. 在观测面板新增 rollout gate 快照输出（allowed/reason/sampleCount/misjudgeRate/mismatchSamples）。
3. 补充 planner primary 的 delete 二次确认回放样例，用于发布前演练。