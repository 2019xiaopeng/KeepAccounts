# P3-S4 验证证据（Agent Default Path + Quality Loop + Style Policy）

- 更新时间：2026-04-08
- 分支：feat/p3-agent-tools
- 阶段：P3-S4

## 1. 验证命令

1. `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.qcb.keepaccounts.domain.agent.QueryDslEngineTest" --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest"`
2. `./gradlew.bat :app:testDebugUnitTest --tests "com.qcb.keepaccounts.data.agent.AgentQualityFeedbackRepositoryTest" --tests "com.qcb.keepaccounts.domain.agent.AgentStyleFormatterTest" --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest"`
3. `./gradlew.bat :app:testDebugUnitTest`
4. `for i in 1..3: ./gradlew.bat :app:testDebugUnitTest --tests "ChatRepositoryBatchLedgerTest.sendMessage_queryRecentOneRoutesToQueryToolAndBypassesGateway" --tests "ChatRepositoryBatchLedgerTest.sendMessage_statsSameMerchantRoutesToStatsToolAndReturnsMerchantFrequency"`

结果：全部通过。

补充结论：第 4 项三轮重复执行均通过，query/stats 主路径在重复场景下保持稳定。

## 2. 需求对照验证

### 2.1 Agent 默认主路径（Prompt 兜底可控）

- 证据：
  - `ChatRepository.sendMessage` 先走 query/stats/write 工具路由。
  - `agentDefaultPathEnabled`、`promptFallbackEnabled` 控制主路径与兜底行为。
- 结论：通过。

### 2.2 质量闭环（样本沉淀 + 纠错标注 + 指标）

- 证据：
  - 新表：`agent_quality_feedback`。
  - 新仓储：`AgentQualityFeedbackRepository`。
  - 指标：`accuracyRate` / `fallbackRate` / `misjudgeRate` / `userCorrectionRate`。
  - 测试：`AgentQualityFeedbackRepositoryTest`。
- 结论：通过。

### 2.3 风格策略（结构化事实优先，抚慰后置）

- 证据：
  - `AgentStyleFormatter` 统一输出模板。
  - query/stats/write/fallback 全部经风格层格式化。
  - 测试：`AgentStyleFormatterTest`。
- 结论：通过。

### 2.4 兼容既有 P3-S3 路由能力

- 证据：
  - `ChatRepositoryBatchLedgerTest.sendMessage_queryRecentOneRoutesToQueryToolAndBypassesGateway`
  - `ChatRepositoryBatchLedgerTest.sendMessage_statsSameMerchantRoutesToStatsToolAndReturnsMerchantFrequency`
- 结论：通过（且文案断言升级为结构化口径）。

## 3. 关键文件清单

1. `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
2. `app/src/main/java/com/qcb/keepaccounts/domain/agent/AgentStyleFormatter.kt`
3. `app/src/main/java/com/qcb/keepaccounts/data/agent/AgentQualityFeedbackRepository.kt`
4. `app/src/main/java/com/qcb/keepaccounts/data/local/entity/AgentQualityFeedbackEntity.kt`
5. `app/src/main/java/com/qcb/keepaccounts/data/local/dao/AgentQualityFeedbackDao.kt`
6. `app/src/main/java/com/qcb/keepaccounts/data/local/AppDatabase.kt`
7. `app/src/main/java/com/qcb/keepaccounts/data/AppContainer.kt`
8. `app/src/test/java/com/qcb/keepaccounts/data/agent/AgentQualityFeedbackRepositoryTest.kt`
9. `app/src/test/java/com/qcb/keepaccounts/domain/agent/AgentStyleFormatterTest.kt`
10. `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt`

## 4. 风险与后续

1. 当前质量指标为离线聚合，后续可增加 UI 可视化。
2. 可补充“误判样本 -> 回放日志”的联动追踪视图。
3. 路由开关可进一步外置为远程配置。