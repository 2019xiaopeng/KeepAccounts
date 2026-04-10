# ADR-046: Phase3 Agent Workflow 升级为 LLM 规划优先 + 本地执行闭环

- 状态：Proposed
- 日期：2026-04-10
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step5 (P3-S5)
- 前置 ADR：ADR-033、ADR-041、ADR-042、ADR-043、ADR-044、ADR-045

## 背景

P3-S4 已完成人设化、本地路由增强、可观测与可见层分离，但仍存在结构性风险：

1. 金额/数量中文表达与运算表达解析能力不足。
2. 复杂相对时间语义解析缺口会污染账单时间轴。
3. 批量 update/delete 意图容易被降维为单笔操作。
4. 破坏性操作在部分条件下仍存在静默执行风险。
5. 多轮补全时本地与远端路径割裂，导致延迟和幻觉风险上升。

## 决策

采用“LLM 规划优先 + 本地执行闭环”的 Agent V2 工作流：

1. Planner（LLM Function Calling）负责意图识别、槽位提取、时间语义推算与风险分级。
2. Validator/Normalizer 负责参数合法化与归一化（金额、数量、时间）。
3. Executor（本地工具）负责数据库读写、幂等、风控确认、审计日志。
4. Responder 只能基于工具观察结果生成用户文案，禁止无观察结论。

## 为什么选 A 不选 B

### A：LLM 规划 + 本地执行（本决策）

1. 语义理解能力显著高于纯 Regex，覆盖复杂自然语言输入。
2. 本地执行保持可控、可测、可回放，降低误操作风险。
3. 能构建稳定 ReAct 闭环（Think -> Act -> Observe -> Respond）。

### B：继续扩大本地 Regex 规则库

1. 维护成本和回归成本持续上升。
2. 对复杂中文语义、多轮上下文和相对时间扩展不经济。
3. 风险边界难统一，容易出现规则互相覆盖与漏判。

## 影响

正向：

1. 意图识别和多轮补全成功率提升。
2. 删除与批量操作安全边界更明确。
3. 幻觉率下降（用户结论必须有工具观察支撑）。

成本：

1. 需要新增 Planner 契约、Normalizer 模块与状态管理。
2. 发布流程需引入影子模式与灰度策略。

## 实施边界

1. LLM 无数据库直连权限。
2. 删除默认 preview -> confirm -> commit。
3. 仅 transactionId 单条命中允许单阶段删除。
4. Regex 从主判定降级为兜底与保护规则。

## 验收标准

1. Intent Routing Accuracy 达到阶段目标并稳定。
2. Hallucination Escape Rate 为 0（无观察结论）。
3. Destructive Safety Rate 达到 100%（破坏性动作确认覆盖）。
4. Batch Fulfillment Rate 显著提升。

## 回滚策略

保留 P3-S4 现网主路径开关；Planner 路径异常时一键回退到本地规则优先。

## 关联文档

1. `docs/p3-s5-agent-workflow-upgrade-plan.md`
2. `docs/p3-s4-humanized-local-routing-optimization-plan.md`

## 结论

在 Phase3 后续迭代中，采用“LLM 负责规划、本地负责执行”的混合 Agent 架构，以替代“持续堆叠 Regex 规则”的主路径演进方式。