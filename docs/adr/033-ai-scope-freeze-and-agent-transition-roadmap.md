# ADR 033: AI 需求范围冻结与 Agent 化过渡路线

- Status: Accepted
- Date: 2026-04-03

## Context
近期 AI 相关需求增长快，单纯通过 Prompt 拼接持续堆叠规则，已接近可维护性上限。为了提高稳定性与交付效率，需要冻结范围并建立分阶段路线。

## Decision
后续 AI 需求仅保留四项：
1. 一次性记录多笔账单（批量解析 + 逐笔入账）。
2. 时间语义简化（单时间字段，修改账单后更新时间）。
3. 架构从 Prompt 拼接逐步过渡到 Agent + 工具调用。
4. 引入 Android 16 Rich Ongoing Notifications 提升长任务反馈体验。

## Why A over B

### A: 冻结为四项核心目标（本次）
- 能聚焦解决当前真实痛点：批量、纠错、耗时等待、执行可靠性。
- 降低路线分散和反复插队带来的研发成本。
- 为后续 Agent 工具化打基础。

### B: 继续并行推进多条需求线
- 需求面广但执行深度不足。
- Prompt 复杂度继续上升，回归成本显著增加。
- 用户核心体验问题无法优先闭环。

## Consequences
- 正向：
  - 优先级更清晰，需求评审更简单。
  - 文档、实现和验收标准可稳定对齐。
  - 有利于从“提示词工程”过渡到“可验证工具执行”。
- 代价：
  - 其他非四项需求进入冻结，不再并行推进。

## Scope Policy
- 仅当四项路线已达到阶段性验收，才允许引入新 AI 需求。
- 新需求必须先通过 ADR 评审并明确替换关系。

## References
- [docs/development_plan_v1.2-v1.4.md](docs/development_plan_v1.2-v1.4.md)
- [docs/AI_Humanized_Design.md](docs/AI_Humanized_Design.md)
