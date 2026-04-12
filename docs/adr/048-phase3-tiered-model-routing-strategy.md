# ADR-048: Phase3 双模型分层路由策略（DeepSeek-V3 + Qwen2.5-7B）

- 状态：Proposed
- 日期：2026-04-12
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step5 (P3-S5 / PhaseE)
- 前置 ADR：ADR-046、ADR-047

## 背景

当前 planner 与 fallback chat 统一使用 DeepSeek-V3。该策略质量稳定，但存在：

1. 低风险请求时延偏高，影响对话体感。
2. 所有流量都走大模型，缺少成本与吞吐弹性。
3. 在安全边界已由本地执行层兜底的前提下，模型层可进一步分层优化。

## 决策

采用“双模型分层路由”：

1. Lite 模型（候选：Qwen2.5-7B-Instruct）处理低风险、低歧义请求。
2. Pro 模型（DeepSeek-V3）处理高风险、复杂语义和 Lite 兜底升级。
3. 本地执行闭环不变：模型只做规划，不直接触达数据库。
4. 以灰度开关控制流量，默认支持一键回切全 Pro。

## 为什么选 A 不选 B

### A：双模型分层（本决策）

1. 在不改变安全护栏的情况下获得时延与成本收益。
2. 通过自动升级 Pro，降低 Lite 误判风险。
3. 与现有 Agent 架构兼容，改造集中在路由与 DI 层。

### B：继续单模型 DeepSeek-V3

1. 质量稳定，但无法释放低风险场景的提速潜力。
2. 高峰时段成本与吞吐压力持续累积。

### C：全面切换小模型

1. 高风险和复杂语义场景的准确率与鲁棒性不可接受。
2. delete/批量/复杂时间语义易触发安全回退，整体收益不稳定。

## 影响

正向：

1. 低风险请求 P50 时延预计下降。
2. 在同等预算下可承载更高并发。
3. 路由与观测能力增强，便于后续自动调参。

成本：

1. 增加路由策略与提示词双配置维护成本。
2. 需补齐模型选择相关回归用例与观测字段。

## 风险与缓解

1. 风险：Lite 误判导致执行前回退增多。
- 缓解：Validator 严格校验 + `confidence` 阈值 + 强制升级 Pro。
2. 风险：路由规则复杂化。
- 缓解：先 Shadow 再灰度，规则命中写入 metadataJson 可审计。
3. 风险：模型 ID 变更导致配置失效。
- 缓解：启动时校验模型配置，异常自动回退 Pro。

## 回滚策略

1. `MODEL_ROUTER_ENABLED=false`，全量回切 DeepSeek-V3。
2. 或 `MODEL_LITE_ROLLOUT_PERCENT=0`，保留代码不承载 Lite 流量。

## 关联文档

1. `docs/p3-s5-phasee-tiered-model-routing-plan.md`
2. `docs/p3-s5-agent-workflow-upgrade-plan.md`
