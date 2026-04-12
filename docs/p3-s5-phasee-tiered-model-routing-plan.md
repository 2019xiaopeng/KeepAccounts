# P3-S5 PhaseE 方案：双模型分层路由（DeepSeek-V3 + Qwen2.5-7B）

- 日期：2026-04-12
- 状态：Proposed
- 适用分支：`feat/p3-agent-tools`
- 关联文档：`docs/p3-s5-agent-workflow-upgrade-plan.md`、`docs/adr/048-phase3-tiered-model-routing-strategy.md`

## 1. 背景与目标

当前实现把 planner 与 fallback chat 都绑定到单一模型（DeepSeek-V3）。

这会带来两个现实问题：

1. 低风险、短链路请求（如单条金额纠错）也走大模型，平均时延偏高。
2. 成本与吞吐弹性受限，峰值场景缺少轻量分流通道。

本阶段目标：

1. 在不牺牲安全边界的前提下，引入 4B/7B 小模型做低风险流量加速。
2. 保持“本地执行闭环”不变：小模型只负责规划，不直接触达 DB。
3. 通过灰度与可回滚开关，确保线上可控。

## 2. 模型分工（建议）

## 2.1 Planner 分工

1. Lite Planner（Qwen2.5-7B-Instruct）
- 负责：
  - 单动作、低歧义写意图（create/update amount/category）。
  - 简单 query 路由（最近一笔、单关键词筛选）。
- 不负责：
  - delete 批量与确认链路决策。
  - 复杂相对时间（上周三/上个月五号/大前天晚上）。
  - 多目标集合（targetMode=set/topN）和复合统计。

2. Pro Planner（DeepSeek-V3）
- 负责：
  - 高风险动作（delete、批量 update/delete）。
  - 多轮补全复杂场景。
  - 复杂时间语义与统计分析。
  - Lite 低置信度或解析失败时兜底接管。

## 2.2 Chat/Fallback 分工

1. Lite Chat（Qwen2.5-7B-Instruct）
- 负责：
  - 短回复闲聊、轻问答、低风险澄清。
2. Pro Chat（DeepSeek-V3）
- 负责：
  - 长回复、复杂解释、情绪安抚与复杂上下文承接。

说明：agent 主路径里大量回复已由本地 formatter 生成，fallback chat 才是双模型收益重点之一。

## 3. 路由策略

## 3.1 一票直达 Pro 条件

满足任一条件直接走 Pro：

1. 包含 delete 意图或确认删除语句。
2. 命中批量语义（多条、前 N 条、全部、set/topN）。
3. 命中复杂时间语义关键词（上周/上个月/大前天/工作日定位）。
4. 命中统计/趋势/占比类问题。
5. PendingIntent 恢复失败或上轮已发生 misjudge。

## 3.2 Lite 尝试条件

同时满足以下条件可先走 Lite：

1. 单动作（create/update/query one-shot）。
2. 输入长度短（建议 <= 36 中文字符）。
3. 无 delete/批量/复杂时间信号。
4. 无上轮失败重试信号。

## 3.3 升级到 Pro（Escalation）

Lite 任一条件触发即升级到 Pro：

1. `confidence < 0.80`。
2. PlannerOutputValidator 存在关键 issue（intent/slot/risk 不合规）。
3. 生成结果缺失关键字段且无法通过 pending 单问补全。
4. Lite 结果与规则护栏冲突（例如高风险却未标记确认）。

## 4. 依赖注入与模块改造

## 4.1 BuildConfig / local.properties 新配置

在保持现有键兼容前提下，新增：

1. `SILICONFLOW_MODEL_PLANNER_PRO=deepseek-ai/DeepSeek-V3`
2. `SILICONFLOW_MODEL_PLANNER_LITE=Qwen/Qwen2.5-7B-Instruct`
3. `SILICONFLOW_MODEL_CHAT_PRO=deepseek-ai/DeepSeek-V3`
4. `SILICONFLOW_MODEL_CHAT_LITE=Qwen/Qwen2.5-7B-Instruct`
5. `MODEL_ROUTER_ENABLED=true`
6. `MODEL_LITE_ROLLOUT_PERCENT=20`
7. `MODEL_LITE_MIN_CONFIDENCE=0.80`

备注：Qwen 的具体模型 ID 以 SiliconFlow `/v1/models` 返回为准，文档先用候选值。

## 4.2 AppContainer 注入建议

1. 保持单一 `SiliconFlowApi` 与统一超时配置。
2. 初始化两个 planner 实例：
- `plannerLite = SiliconFlowPlannerGateway(api, modelPlannerLite, promptProfile=lite)`
- `plannerPro = SiliconFlowPlannerGateway(api, modelPlannerPro, promptProfile=pro)`
3. 注入一个路由器实现：
- `agentPlanner = TieredPlannerRouter(lite=plannerLite, pro=plannerPro, policy=ModelRoutingPolicy(...))`
4. fallback chat 增加：
- `aiChatGateway = TieredAiChatGateway(liteGateway, proGateway, policy)`

## 4.3 新增/调整类（建议命名）

1. `domain/agent/ModelRoutingPolicy.kt`
2. `domain/agent/ModelRouteDecision.kt`
3. `data/repository/TieredPlannerRouter.kt`（实现 `AgentPlanner`）
4. `data/repository/TieredAiChatGateway.kt`（实现 `AiChatGateway`）
5. `domain/agent/PlannerPromptProfile.kt`（LITE/PRO）

## 5. 提示词微调策略

## 5.1 Lite Planner Prompt（更短、更硬约束）

目标：快且稳，减少思维负担。

建议：

1. 仅保留必要规则：intent 枚举、槽位字段、低置信度时返回 unknown。
2. 明确禁止脑补：不确定就 `missingSlots` + `confidence<0.8`。
3. 降低输出复杂度：默认 `targetMode=single`，复杂情况交给 Pro。

参考骨架：

1. 角色：你是轻量 planner，只返回函数参数 JSON。
2. 规则：无法确定时 intent=unknown，confidence<0.5。
3. 安全：delete/批量/复杂时间一律标记为高风险并建议转交 Pro。

## 5.2 Pro Planner Prompt（完整策略）

目标：处理边界与复杂语义。

建议：

1. 保留完整风险策略（delete 两阶段、批量确认、复杂时间解析要求）。
2. 强调与本地 Validator 一致的字段规范。
3. 保留 explainability 所需的结构字段，便于观测与回放。

## 5.3 Chat Prompt 微调

1. Lite Chat：
- maxTokens 更小（如 128~192），句式短，优先澄清。
2. Pro Chat：
- 允许更长输出和更强上下文整合。
3. 两者统一：
- 不输出结构化调试字段。
- 不泄漏 `<NOTE>/<THINK>` 负载。

## 6. 观测指标与灰度

## 6.1 新增观测字段（建议写入 metadataJson）

1. `plannerModelUsed`: lite/pro
2. `chatModelUsed`: lite/pro
3. `routeReason`: 命中的路由规则
4. `escalatedToPro`: true/false
5. `liteConfidence`: Lite 首判置信度

## 6.2 验收指标

1. P50/P95 首字时延（目标：低风险请求明显下降）。
2. Lite 命中率与升级率（命中率上升、升级率受控）。
3. misjudgeRate 不高于当前基线。
4. 删除/批量安全指标维持 100%。

## 6.3 灰度步骤

1. E1：Shadow（0% 执行，仅记录 Lite 决策）。
2. E2：10% 低风险执行 Lite。
3. E3：20%~40% 扩量，持续监控误判与升级率。
4. E4：稳定后默认开启，保留一键全量回切 Pro。

## 7. 回滚策略

1. `MODEL_ROUTER_ENABLED=false`：立即退回单模型 Pro。
2. `MODEL_LITE_ROLLOUT_PERCENT=0`：保留路由代码但停用 Lite 流量。
3. 发生异常时只回退模型路由，不回退本地执行与安全护栏。

## 8. 实施清单（建议拆分）

1. PR-1：配置键与 DI 接线（不改行为，默认全 Pro）。
2. PR-2：TieredPlannerRouter + Shadow 打点。
3. PR-3：Lite 执行灰度 + 自动升级 Pro。
4. PR-4：TieredAiChatGateway + fallback chat 分层。
5. PR-5：提示词 A/B 与阈值调优。

## 9. 结论

在 KeepAccounts 的现有 Agent 架构下，引入 Qwen2.5-7B 作为 Lite 层最合适的切入点是“低风险规划 + fallback chat”。

这样可以在不牺牲安全边界与可回放能力的前提下，获得可观的延迟优化与成本弹性；复杂与高风险场景继续由 DeepSeek-V3 保底。
