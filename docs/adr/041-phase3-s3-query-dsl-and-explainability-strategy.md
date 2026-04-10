# ADR-041: Phase3-S3 查询 DSL 与可解释结果策略

- 状态：Accepted
- 日期：2026-04-08
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step3 (P3-S3)
- 前置 ADR：ADR-038、ADR-040

## 背景

P3-S3 需要落地 `query_transactions` 与 `query_spending_stats`，并满足：

1. 统一 Query DSL，避免自由 SQL 拼接。
2. 查询/统计结果可解释（时间窗口、样本量、排序依据、聚合方式）。
3. 同时覆盖“花得最多”和“消费最频繁”口径。
4. 输出可复核并与账本原始数据一致。

## 决策

1. 采用统一 DSL 分层：
- `QueryDslBuilder` 负责把工具参数归一化为 DSL。
- `QueryDslExecutor` 负责执行过滤、排序、聚合。

2. 将可解释字段作为结果的一等公民：
- `sampleSize`
- `timeWindow`
- `sortKey`
- `aggregationMethod`

3. 在 DSL 与执行器基础上，落地 Repository 工具入口与聊天意图路由：
- `queryTransactionsTool` / `querySpendingStatsTool` 作为统一执行入口。
- `sendMessage` 命中查询/统计意图时优先走工具路径，并记录调用日志。

## 方案细节

### 1) 窗口与过滤

统一时间窗口：`today/yesterday/last7days/last30days/last12months/custom`。

统一过滤维度：交易ID、关键词、金额范围、日期关键词。

### 2) 交易查询

- 排序：`record_time_desc`、`amount_desc`
- 分页：`limit`
- 输出：交易列表 + 可解释字段

### 3) 统计查询

- 分组：`category/merchant/timeslot/day/month`
- 指标：`total_amount/frequency/category_ratio`
- 排序：`value_desc/frequency_desc`
- 输出：bucket 列表 + 可解释字段

### 4) 自然语言到工具路由

- 查询类意图：`最近一笔`、`最近N天`、`最高消费`、`关键词检索`。
- 统计类意图：`最频繁`、`分类占比`、`周/月/年趋势`、`总吃同一家`。
- 命中 query/stats 意图时，绕过自由文本主链路，直接执行工具调用并输出解释字段。

## 备选方案与否决原因

1. 在 DAO 层拼接字符串 SQL。
- 否决原因：约束不透明、测试困难、易引入注入和分支漂移。

2. query 与 stats 各自定义一套参数模型。
- 否决原因：重复实现窗口/过滤逻辑，后续维护成本高。

3. 仅返回数据，不返回解释信息。
- 否决原因：无法满足可解释与可复核要求。

## 验证

当前阶段通过单元测试与仓储层集成测试验证：

1. 最近一笔
2. 最近一周最高消费
3. 最近最频繁消费
4. 月度分类占比
5. 高频商家识别（“总吃同一家”）
6. sendMessage 查询/统计意图路由（并验证未走 AI 网关）

并通过参数校验测试验证窗口、排序、groupBy、metric、topN 的约束。

## 影响

正向：

1. 为 P3-S3 查询能力建立统一扩展点。
2. 将“可解释性”前置到模型层，减少 UI/对话层重复拼装。
3. 测试样例可直接作为后续集成测试基线。

成本：

1. 需要后续将 InMemory 骨架平滑替换为仓储数据源执行。
2. 统计口径仍需在真实数据规模下做性能校验。

## 后续

1. 增加 query/stats 多步编排的一致性回放断言。
2. 在真实数据规模下补性能基准与精度回归。
3. 增加查询结果卡片化展示与跨页面复核入口。
