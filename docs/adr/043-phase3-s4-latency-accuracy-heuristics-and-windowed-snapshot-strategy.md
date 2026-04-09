# ADR-043: Phase3-S4 低延迟与准确率平衡策略（上下文压缩 + 窗口化快照 + 口语意图放宽）

- 状态：Accepted
- 日期：2026-04-09
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step4 (P3-S4)
- 前置 ADR：ADR-041、ADR-042

## 背景

P3-S4 完成后，已具备 Agent 主路径与质量闭环，但在真实交互里仍存在三个体验风险：

1. Fallback 到远端模型时，历史上下文按消息气泡传入，token 成本偏高。
2. query/stats 工具执行前固定加载 600 条快照，存在不必要的内存压力。
3. 本地规则对口语化输入（如“前天晚饭30”“本月餐饮”）命中率不足，导致 fallback 率上升。

## 决策

1. 对 fallback 上下文做“动态限额 + 内容净化 + 气泡合并”：
- 上下文条数按输入复杂度动态取值（8~18）。
- 历史消息发送前剥离 `<DATA>/<RECEIPT>/<NOTE>/<THINK>` payload。
- 连续同角色消息合并后再传输，减少 token。

2. query/stats 快照加载改为“窗口化 SQL 预过滤 + 动态 limit”：
- 新增 `TransactionDao.getTransactionsInRange(...)`。
- 根据 window（today/last7days/last30days/custom 等）优先走范围查询。
- 对“最近一笔”类查询使用更小快照预算；统计类按窗口与 topN 动态预算。

3. 放宽本地意图识别，降低口语输入漏判：
- 增加 `querySoftIntentHints`、`queryWindowRelaxedHints`。
- write 路由加入 `writeSceneHints`，避免仅因数字触发误写入。
- 对“窗口+主题词”组合（如“本月餐饮”）允许走 query 路由。

## 备选方案与否决原因

1. 保持 24 条固定上下文 + 固定 600 快照。
- 否决：延迟与资源占用在高频场景下不可控。

2. 直接把所有 query/stats 下推为 SQL 聚合。
- 否决：改造面过大，超出当前阶段可控范围。

3. 仅扩词典，不改快照与上下文策略。
- 否决：只能降低 fallback，无法同时改善 token 与内存开销。

## 影响

正向：

1. 降低 fallback 场景 token 传输体积。
2. 减少 query/stats 本地内存扫描压力。
3. 提升口语化输入本地命中率，缩短简单任务响应链路。

成本：

1. 规则复杂度上升，需要持续维护 hint 集。
2. 快照预算过低时可能影响极端大账本下统计精度，需要后续基准验证。

## 验证

1. 定向回归通过：
- `ChatRepositoryBatchLedgerTest`
- `ChatRepositoryTimeSemanticsTest`
- `QueryDslEngineTest`

2. 全量单测通过：
- `:app:testDebugUnitTest`

3. 新增口语化场景断言：
- “打车花了 50”本地 write 命中并绕过网关。
- “前天晚饭 30”本地 write 命中并识别餐饮分类。
- “本月餐饮”本地 query 命中并绕过网关。

## 后续

1. 基于 `agent_quality_feedback` 增加 fallback_rate 与 misjudge_rate 趋势看板。
2. 对 last12months 大样本场景补性能基准与精度对照测试。
3. 将 fallback 上下文策略做成可配置开关，支持灰度调参。