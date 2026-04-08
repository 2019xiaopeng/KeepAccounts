# ADR 036: Phase2 聊天回执日期文案与跨页口径对齐

- Status: Accepted
- Date: 2026-04-08

## Context
Phase2 已引入语义时间显示（今天/昨天/前天），但聊天回执卡片的“日期”行仍只显示标签（如 `今天`），而首页、编辑页和搜索页会展示“相对词 + 日期”文本（如 `今天 04-08`）。

该差异会造成两个问题：
1. 用户在聊天页无法直接看到具体日期，跨页核对成本上升。
2. 同一笔记录在不同页面的日期表达不一致，不符合 Phase2 的统一展示目标。

## Decision
将聊天回执卡片“日期”字段从 `dateLabel` 切换为 `dateText`：

1. `dateLabel`：仅相对词或绝对日期（信息量较少）。
2. `dateText`：相对词 + 日期，或绝对日期（信息更完整）。

本次决策只调整显示文案，不改变落库时间戳与解析逻辑。

## Why A over B

### A: 日期行展示 `dateText`
- 与首页、编辑页、搜索页口径一致。
- 用户无需跳页即可确认“是哪一天”。
- 改动小，不影响数据模型与业务流程。

### B: 继续展示 `dateLabel`
- 信息不完整，仍需依赖“时间”行或跳转验证。
- 与其他页面存在表达差异。
- Phase2 的“统一时间展示口径”目标不够闭合。

## Consequences
- 正向：
  - 聊天回执日期可读性提升，跨页核对更直接。
  - 减少“同一记录多种日期文案”的认知切换。
- 代价：
  - 回执卡片日期字段长度略有增加。

## References
- [app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt](app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt)
- [app/src/main/java/com/qcb/keepaccounts/ui/format/SemanticDateTime.kt](app/src/main/java/com/qcb/keepaccounts/ui/format/SemanticDateTime.kt)
- [app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticDateTimeTest.kt](app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticDateTimeTest.kt)
- [docs/development_plan_v1.2-v1.4.md](docs/development_plan_v1.2-v1.4.md)
