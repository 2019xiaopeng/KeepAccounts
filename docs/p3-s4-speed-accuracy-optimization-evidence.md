# P3-S4 速度与准确率优化验证证据（Context + Snapshot + Heuristics）

- 更新时间：2026-04-09
- 分支：feat/p3-agent-tools
- 范围：P3-S4 增强优化

## 1. 目标

在不破坏准确率与可追踪性的前提下，降低简单任务与 fallback 场景的响应成本：

1. 降低 fallback token 开销。
2. 降低 query/stats 快照加载开销。
3. 提升口语输入本地命中率，减少不必要 fallback。

## 2. 代码改动摘要

1. fallback 上下文优化：
- 动态上下文条数（8~18）
- 历史消息 strip payload 后再发网关
- 连续同角色消息合并并限制最大 turn

2. 快照加载优化：
- 新增 `TransactionDao.getTransactionsInRange(...)`
- query/stats 按 window 先走 SQL 范围过滤
- 根据查询类型动态设置快照预算（最近一笔更小，统计更稳健）

3. 意图识别优化：
- 扩展 query soft/window hints
- 新增 write scene hints
- 放宽“窗口 + 主题词”query 入口并规避纯数字误触发 write

## 3. 验证命令

1. `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest" --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryTimeSemanticsTest" --tests "com.qcb.keepaccounts.domain.agent.QueryDslEngineTest"`
2. `./gradlew.bat :app:testDebugUnitTest`

结果：全部通过。

## 4. 关键场景验证

1. “打车花了 50”
- 预期：本地 write 命中，绕过网关
- 证据：`sendMessage_colloquialTaxiExpenseRoutesToLocalWriteAndBypassesGateway`

2. “前天晚饭 30”
- 预期：本地 write 命中，分类识别为餐饮
- 证据：`sendMessage_colloquialRelativeMealRoutesToLocalWriteAndInfersCategory`

3. “本月餐饮”
- 预期：本地 query 命中，绕过网关
- 证据：`sendMessage_monthCategoryColloquialQueryRoutesToLocalQueryAndBypassesGateway`

## 5. 风险与后续

1. 快照预算仍是启发式策略，需在大账本上补精度/性能基准。
2. fallback 上下文压缩策略可继续灰度调参。
3. 口语词典需结合真实用户语料持续扩展。