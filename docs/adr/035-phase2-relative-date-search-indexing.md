# ADR 035: Phase2 相对日期搜索索引统一

- Status: Accepted
- Date: 2026-04-08

## Context
Phase2 已把时间展示语义统一为“今天/昨天/前天优先”，但搜索页仍主要依赖绝对日期文本（如 `yyyy-MM-dd`、`MM-dd`）。
这会导致用户输入“昨天 午餐”“前天 奶茶”时命中率不稳定，出现“展示是相对日期，搜索却不支持相对日期关键词”的体验割裂。

## Decision
在搜索索引阶段统一加入语义日期文本：

1. 新增 `buildSemanticDateSearchTexts(timestamp)`，同时生成：
   - 绝对日期/时间文本（兼容旧查询习惯）
   - 相对日期标签与日期时间文本（今天/昨天/前天）
2. 搜索字段构建改为消费该统一函数，避免页面内散落格式规则。
3. 查询归一化补充相对日期同义词映射：`今日->今天`、`昨日/昨晚/昨夜->昨天`、`前日->前天`。

## Why A over B

### A: 在索引层一次性统一绝对+相对日期 token
- 对用户最直观：输入习惯不需要迁移。
- 对实现最稳定：查询逻辑不变，只增强可命中字段。
- 与 Phase2 时间语义目标一致，跨页面认知成本更低。

### B: 仅保留绝对日期，要求用户改用固定格式搜索
- 与当前 UI 文案不一致（页面大量使用“今天/昨天/前天”）。
- 需要用户学习额外搜索格式，体验回退。
- 后续扩展（如“本周”“上周”）成本更高。

## Consequences
- 正向：
  - 时间语义在“展示-搜索”链路保持一致。
  - 相对日期查询命中率提升，尤其是聊天改账后的回查场景。
  - 时间 token 规则集中在格式层，后续维护更简单。
- 代价：
  - 每条交易新增若干日期 token，字段匹配量略增。

## References
- [app/src/main/java/com/qcb/keepaccounts/ui/format/SemanticDateTime.kt](app/src/main/java/com/qcb/keepaccounts/ui/format/SemanticDateTime.kt)
- [app/src/main/java/com/qcb/keepaccounts/ui/screens/SearchScreen.kt](app/src/main/java/com/qcb/keepaccounts/ui/screens/SearchScreen.kt)
- [app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticSearchTextsTest.kt](app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticSearchTextsTest.kt)
- [docs/p2-t2-verification-evidence.md](docs/p2-t2-verification-evidence.md)
