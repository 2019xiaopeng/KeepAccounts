# P3-S3 Query DSL 说明（落地版 v1）

- 更新时间：2026-04-08
- 适用分支：feat/p3-agent-tools
- 阶段：P3-S3 Query & Insights

## 1. 目标

在不拼接自由 SQL 的前提下，统一 `query_transactions` 与 `query_spending_stats` 的查询表达层，保证查询结果可解释、可复核、可测试。

## 2. DSL 核心模型

代码位置：`app/src/main/java/com/qcb/keepaccounts/domain/agent/QueryDslEngine.kt`

### 2.1 统一窗口

- `QueryWindowPreset`：`today` / `yesterday` / `last7days` / `last30days` / `last12months` / `custom`
- `QueryTimeWindow`：
  - `preset`
  - `startAtMillis`
  - `endAtMillis`
  - `label`

### 2.2 交易查询 DSL

- `QueryTransactionsDsl`
  - `filters: TransactionFilter`
  - `timeWindow: QueryTimeWindow`
  - `sortKey: QuerySortKey`（`record_time_desc` / `amount_desc`）
  - `limit`

### 2.3 统计查询 DSL

- `QuerySpendingStatsDsl`
  - `timeWindow: QueryTimeWindow`
  - `groupBy: QueryGroupByDimension`（`category` / `merchant` / `timeslot` / `day` / `month`）
  - `metric: QueryMetricType`（`total_amount` / `frequency` / `category_ratio`）
  - `sortKey: QuerySortKey`（`value_desc` / `frequency_desc`）
  - `topN`

### 2.4 可解释字段

- `QueryExplainability`
  - `sampleSize`
  - `timeWindow`
  - `sortKey`
  - `aggregationMethod`

## 3. 构建与执行

### 3.1 构建器

- `QueryDslBuilder.buildTransactions(args)`
- `QueryDslBuilder.buildStats(args)`

职责：

1. 将工具入参转换为 DSL。
2. 解析窗口与自定义区间。
3. 归一化排序/分组/指标与默认值。

### 3.2 执行器（已接入工具层）

- `QueryDslExecutor.executeTransactions(dsl, source)`
- `QueryDslExecutor.executeStats(dsl, source)`
- `QueryInsightsToolExecutor.queryTransactions(args)`
- `QueryInsightsToolExecutor.querySpendingStats(args)`
- `ChatRepository.queryTransactionsTool(args)`
- `ChatRepository.querySpendingStatsTool(args)`

聊天入口路由：

- `ChatRepository.sendMessage(...)` 已增加查询/统计意图识别。
- 命中 query/stats 意图时，直接走工具路径（不走 LLM 文本生成主链路）。
- 工具调用结果会写入 `AgentRunLogger`，保留 `requestId/toolName/args/result/latency` 可回放证据。

职责：

1. 统一过滤（窗口、关键词、金额范围、交易ID）。
2. 交易查询排序与分页。
3. 统计分组聚合（金额、频次、占比，含商家频次）。
4. 输出可解释字段。

## 4. 参数校验约束（当前实现）

代码位置：`app/src/main/java/com/qcb/keepaccounts/domain/agent/AgentToolValidator.kt`

- query：
  - `window` 合法
  - `custom` 必须带合法 `startAtMillis/endAtMillis`
  - `sortKey` 仅允许 `record_time_desc` / `amount_desc`
  - `limit` 范围 1..100
  - 金额范围 `min <= max`
- stats：
  - `window` 合法
  - `groupBy`、`metric`、`sortKey` 合法
  - `groupBy` 支持 `merchant`
  - `category_ratio` 仅支持 `groupBy=category`
  - `topN` 范围 1..50

## 5. 测试覆盖（当前）

代码位置：`app/src/test/java/com/qcb/keepaccounts/domain/agent/QueryDslEngineTest.kt`

覆盖场景：

1. 最近一笔（recent one）
2. 最近一周最高消费（highest expense in last7days）
3. 最近最频繁消费（most frequent consumption）
4. 月度分类占比（monthly category ratio）
5. 高频商家识别（同一家消费）

代码位置：`app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt`

覆盖场景：

1. Repository query 工具入口：最近一周最高消费
2. Repository stats 工具入口：分类频次统计
3. sendMessage 路由 query：最近一笔（并验证未走 AI 网关）
4. sendMessage 路由 stats：总吃同一家（并验证未走 AI 网关）

## 6. 下一步

1. 增加 query/stats 多步编排（preview -> query/stats -> explain）的一致性策略。
2. 补充周/月/年趋势在真实账本大样本上的性能与正确性基准。
3. 将查询结果结构化卡片化（与聊天普通文本分层展示）。
