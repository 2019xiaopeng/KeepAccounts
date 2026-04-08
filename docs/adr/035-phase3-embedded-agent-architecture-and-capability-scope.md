# ADR 035: Phase3 内嵌式 Agent 架构与能力范围决策

- Status: Accepted
- Date: 2026-04-08

## Context
当前 AI 记账链路主要依赖 Prompt 约束与回执解析，随着需求扩展到“全面接管账本”（增删改查+分析），仅靠 Prompt 规则堆叠会带来以下问题：

1. 可维护性下降：规则冲突与回归风险快速上升。
2. 可观测性不足：缺少 requestId 级别工具调用链。
3. 可验证性不足：难以对执行路径做确定性测试。
4. 可解释性不足：错误与部分成功场景难以标准化反馈。
5. 人性化与准确性耦合：回复风格和执行路径混在一起时容易互相干扰。
6. 复杂检索场景增长：仅支持高消费检索不足以覆盖“最频繁消费”类真实管家需求。

## Decision
采用“内嵌式 Agent + 本地工具编排”架构，作为 Phase3 标准实现路线。

### 1. 能力范围

Phase3 Agent 必须覆盖：

1. 交易 CRUD：create/update/delete/query。
2. 分析能力：周/月/年趋势与分类统计。
3. 结构化回执：success/failure/partial_success。
4. 全链路日志：requestId、toolName、args、result、errorCode。
5. 人性化回答：在保证工具执行正确的前提下提供管家式表达。
6. 频次偏好洞察：支持高频商家/类别/时段检索与可解释输出。

### 2. 工具集

1. preview_actions(actions[])
2. create_transactions(items[])
3. update_transactions(filters, patch)
4. delete_transactions(filters)
5. query_transactions(filters)
6. query_spending_stats(window, groupBy, metric)

### 3. 执行策略

1. 默认先 preview 再执行。
2. Orchestrator 管理工具调用顺序与回退策略。
3. 工具层仅执行，不承担意图决策。
4. 保留 Prompt 兜底路径，分阶段灰度切换。
5. 采用双层输出策略：先产出结构化结果，再产出人性化文案。
6. 建立后台质量反馈闭环：误判样本、纠错样本、命中率指标用于持续提准。

## Why A over B

### A: 内嵌式 Agent + 本地工具编排

1. 能把业务能力显式化为可测试工具。
2. 支持 requestId 级调用链回放与审计。
3. 可把“理解”和“执行”职责分离，降低回归复杂度。
4. 便于逐步扩展到分析与自动化场景。
5. 能同时满足“像真实管家一样交流”与“可验证执行”两类目标。

### B: 继续以 Prompt 规则直驱落库

1. 规则增长后维护成本高，容易互相覆盖。
2. 难以给出稳定可回放的执行链证据。
3. 在批量、复杂查询和统计场景下稳定性不足。
4. 对频次偏好等复杂问题缺少稳定可解释依据。

## Consequences

- 正向：
  1. 账本能力可模块化扩展。
  2. 工具链具备测试友好性与审计能力。
  3. 能支撑“全面接管型 Agent”目标。
  4. 能覆盖高频偏好与习惯分析等真实管家场景。
- 代价：
  1. 需要新增编排层与日志模型。
  2. 初期实现复杂度上升。
  3. 需要额外建设质量评估与提准流程。

## Implementation Notes

1. Phase3 采用 P3-A/B/C/D/E 五段拉长实施，降低一次性改造风险。
2. 每段必须配套测试与文档证据再进入下一段。

## References

1. [docs/development_plan_v1.2-v1.4.md](docs/development_plan_v1.2-v1.4.md)
2. [docs/p3-embedded-agent-design.md](docs/p3-embedded-agent-design.md)
3. [docs/adr/033-ai-scope-freeze-and-agent-transition-roadmap.md](docs/adr/033-ai-scope-freeze-and-agent-transition-roadmap.md)
