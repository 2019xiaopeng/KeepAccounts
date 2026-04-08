# P3-S3 验证证据（Query & Insights）

- 更新时间：2026-04-08
- 分支：feat/p3-agent-tools
- 阶段：P3-S3

## 1. 验证命令

1. `./gradlew.bat :app:compileDebugKotlin`
2. `./gradlew.bat :app:testDebugUnitTest --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest" --tests "com.qcb.keepaccounts.domain.agent.QueryDslEngineTest" --tests "com.qcb.keepaccounts.domain.agent.AgentToolValidatorTest" --rerun-tasks`
3. `./gradlew.bat :app:testDebugUnitTest`

结果：全部通过。

## 2. 需求对照验证

### 2.1 query 与 stats 统一 Query DSL

- 证据：
  - `app/src/main/java/com/qcb/keepaccounts/domain/agent/QueryDslEngine.kt`
  - `app/src/main/java/com/qcb/keepaccounts/domain/agent/QueryInsightsToolExecutor.kt`
- 结论：通过（无自由 SQL 拼接，统一 Builder + Executor）。

### 2.2 可解释字段

- 字段：`sampleSize` / `timeWindow` / `sortKey` / `aggregationMethod`
- 证据：
  - `QueryExplainability` 模型
  - `QueryInsightsToolExecutor` 输出 JSON
  - `ChatRepository` 查询回复文案中包含四字段
- 结论：通过。

### 2.3 同时支持“花得最多”和“消费最频繁”

- 花得最多：`sortKey=amount_desc` 查询路径。
- 消费最频繁：`metric=frequency` 统计路径。
- 结论：通过。

### 2.4 输出可复核

- 证据：
  - 工具结果来自 `transactions` 快照（Repository -> QueryInsightsToolExecutor）。
  - 查询/统计调用写入 `AgentRunLogger`（requestId/toolName/args/result/latency）。
- 结论：通过。

## 3. Prompt 必测场景与对应测试

1. 最近一笔
- `QueryDslEngineTest.queryTransactions_recentOne_returnsLatestRecord`

2. 最近一周最高消费
- `QueryDslEngineTest.queryTransactions_last7DaysHighestExpense_returnsTopAmountInWindow`
- `ChatRepositoryBatchLedgerTest.queryTransactionsTool_recentHighestExpenseInLast7Days_returnsExpectedTopRecord`

3. 最近最频繁消费
- `QueryDslEngineTest.querySpendingStats_mostFrequentConsumption_returnsTopCategoryByFrequency`
- `ChatRepositoryBatchLedgerTest.querySpendingStatsTool_frequencyByCategory_returnsMostFrequentCategory`

4. 月度分类占比
- `QueryDslEngineTest.querySpendingStats_monthlyCategoryRatio_returnsExplainableShareBuckets`

5. 高频商家（总吃同一家）
- `QueryDslEngineTest.querySpendingStats_mostFrequentMerchant_returnsTopMerchantByRemark`
- `ChatRepositoryBatchLedgerTest.sendMessage_statsSameMerchantRoutesToStatsToolAndReturnsMerchantFrequency`

6. 聊天路由验证（命中 query/stats 时绕过 AI 网关）
- `ChatRepositoryBatchLedgerTest.sendMessage_queryRecentOneRoutesToQueryToolAndBypassesGateway`
- `ChatRepositoryBatchLedgerTest.sendMessage_statsSameMerchantRoutesToStatsToolAndReturnsMerchantFrequency`

## 4. 当前范围与剩余事项

已完成：

1. Query DSL + 执行器
2. Repository 工具入口
3. sendMessage 自然语言 query/stats 路由
4. 解释字段输出与日志记录
5. 核心样例测试通过

待后续增强：

1. 趋势统计的大样本性能基准
2. Query/Stats 结果卡片化展示
3. 更丰富的关键词/商家识别字典
