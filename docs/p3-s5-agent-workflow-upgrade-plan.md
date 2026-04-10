# P3-S5 Agent Workflow 升级方案（LLM 规划优先 + 本地执行闭环）

- 日期：2026-04-10
- 状态：In Progress（PhaseA/PhaseB/PhaseC 已完成，PhaseD 待开始）
- 适用范围：Phase3 后续迭代（承接 P3-S4）
- 关联 ADR：ADR-033、ADR-041、ADR-042、ADR-043、ADR-044、ADR-045、ADR-046（拟）

## 1. 背景与结论

你提出的 5 个风险点判断是准确的，而且本质上都指向同一个核心问题：

1. 本地 Regex 路由在“可解释、可测”上有优势，但在复杂语义（中文金额、相对时间、多轮补全）上天花板明显。
2. 远端模型在没有工具上下文时容易补偿性输出（幻觉或空话），破坏可信度。
3. 当前流程仍是“规则先判定、命中后直接收敛”，尚未形成稳定的 ReAct 闭环。

因此，Phase3 的下一阶段不建议简单继续扩词典，而应升级为：

- LLM 负责“理解与规划”（Intent/Slots/Time/风险级别）。
- 本地工具负责“执行与约束”（DB 读写、风控、幂等、审计）。
- 回答生成必须基于工具结果（Observation），禁止无观察输出业务结论。

## 2. P3 目标工作流（V2）

## 2.1 总体流程

1. 用户输入
2. Planner（LLM Function Calling）生成结构化计划 `IntentPlanV2`
3. Validator 对计划做参数合法化/归一化/风控升级
4. Executor 调用本地工具（create/update/delete/query/stats）
5. Observer 汇总执行结果（成功/失败/部分成功/命中条目）
6. Responder 基于观察结果生成人设化回复
7. Renderer 将可见文本与卡片分层渲染（隐藏调试字段）
8. Logger 写入可回放轨迹（requestId、tool args/result、风险决策）

## 2.2 边界原则

1. LLM 不直接访问数据库。
2. 所有账务结论必须来自工具观察结果，不允许“无观察结论”。
3. 删除、批量修改等破坏性动作必须经过风险门控。
4. 用户可见层不展示技术日志字段。

## 3. 对 5 个风险点的针对性方案

## 3.1 金额与数字解析盲区

### 问题

- 中文金额（如“一百五”）和基础运算（如“10加20”）无法稳定解析。
- 删除数量“十二笔”在旧实现下存在截断风险。

### 方案

1. 新增 `AmountNormalizer`（工具层前置）：
- 支持阿拉伯数字、中文数字、口语金额（如“一百五”=150）。
- 支持基础运算表达式（加减，限制长度与操作符数量）。
2. 新增 `CountNormalizer`：
- 支持“前十二笔/12笔/两笔”等数量表达统一解析。
3. 在 `Validator` 中输出标准化后的 `normalizedAmount/normalizedCount`，执行层只使用归一值。

## 3.2 相对时间语义断层

### 问题

- “上周三/上个月五号/大前天晚上”解析失败后回退到当前时间，造成记账时间污染。

### 方案

1. 新增 `TemporalResolverV2`：
- 支持相对周、相对月、工作日定位、时段映射。
- 输出 `resolvedDateTime` + `confidence` + `resolutionTrace`。
2. 低置信度策略：
- 不静默回退当前时间。
- 触发一次最小澄清（只问一个关键问题）。

## 3.3 批量修改/删除意图被降维为单笔

### 问题

- 现有 update 路径偏向“单最优记录”，批量意图会漏改。

### 方案

1. `IntentPlanV2` 增加 `targetMode`：
- `single` / `set` / `topN`
2. Executor 支持 `resolveTargets()` 返回目标集合。
3. 对集合执行时返回逐条结果，UI 卡片明确展示成功/失败条目。

## 3.4 高危静默删除边界不足

### 问题

- 当前阈值下可能出现“多条匹配但直接删”的风险。

### 方案

1. 删除默认两阶段：`preview -> confirm -> commit`。
2. 仅以下场景允许单阶段删除：
- 明确 transactionId 且单条命中。
3. 其余情况强制确认，不再按金额/条数阈值豁免。

## 3.5 多轮补全上下文割裂

### 问题

- “花了20”后补一句“打车”，本地无法拼接上下文导致路由跳远端。

### 方案

1. 新增本地 `PendingIntentState`（短时会话态，带 TTL）：
- 记录缺失槽位（amount/category/time）。
- 下一轮优先尝试补齐并本地提交。
2. Planner 输入携带最近一轮未完成计划摘要，避免重复理解。
3. 只有当补齐失败且 Planner 低置信度时，才进入闲聊兜底。

## 4. Regex 路由的定位调整

不是“立刻删除 Regex”，而是“降级为兜底与保护网”：

1. 主路径：Function Calling Planner。
2. 次路径：轻规则保护（高危防误删、极端输入兜底）。
3. 旧 Regex 大词典逐步退出主判定，只保留少量硬安全规则。

## 5. 分阶段落地计划

## Phase A（契约与影子模式）

1. 定义 `IntentPlanV2`/`ToolCallEnvelope`/`ObservationEnvelope`。
2. 线上仍走现有主路径，同时并行跑 Planner（不执行）做对比打点。
3. 验收：拿到意图一致率、槽位完整率、风险误判率基线。

## Phase B（查询与简单记账优先切换）

1. query/stats 与单笔 create 切到 Planner 主路。
2. 对照现网结果做灰度（例如 10% -> 30% -> 100%）。
3. 验收：查询命中率、幻觉率、平均延迟不劣化。

## Phase C（高风险写操作切换）

1. update/delete/batch 接入集合目标解析与两阶段删除。
2. 引入 `PendingIntentState` 完成多轮补全。
3. 验收：误删率为 0，批量漏执行率显著下降。

## Phase D（规则收敛与治理）

1. 大词典从主路移除，仅留安全兜底规则。
2. 建立回放集与回归集，固定每次发布前跑完整 Agent 回归。
3. 验收：质量指标稳定 2 个迭代周期。

## 6. 数据结构建议（简版）

```json
{
  "intent": "update_transactions",
  "targetMode": "set",
  "targets": {
    "selector": "recent+keyword",
    "keyword": "打车",
    "window": "yesterday",
    "topN": 2
  },
  "patch": {
    "amount": 15,
    "category": "交通出行"
  },
  "riskLevel": "medium",
  "confidence": 0.86,
  "needsConfirmation": true,
  "missingSlots": []
}
```

## 7. 验收指标（建议纳入 CI / 预发布）

1. Intent Routing Accuracy（按意图类型分桶）。
2. Slot Completion Rate（多轮补全成功率）。
3. Hallucination Escape Rate（无工具观察却输出结论的比例，目标 0）。
4. Destructive Safety Rate（破坏性操作二次确认覆盖率，目标 100%）。
5. Batch Fulfillment Rate（批量意图完整执行率）。

## 8. 风险与回滚

1. 风险：Planner 输出不稳定导致执行抖动。
- 对策：Validator 严格约束 + 影子模式先行 + 灰度放量。
2. 风险：延迟上升。
- 对策：查询与写入工具并行准备、缓存近期快照、压缩 prompt token。
3. 回滚：保留 P3-S4 主路径开关，一键切回本地规则优先。

## 9. 对当前代码的最小改造入口（后续实施）

1. `ChatRepository.sendMessage`：接入 Planner 分支与统一调度。
2. `AgentToolValidator`：从“纯校验”扩展为“校验+归一化”。
3. 新增模块：
- `AmountNormalizer`
- `TemporalResolverV2`
- `PendingIntentStateStore`
- `PlannerGateway`（Function Calling）

## 10. 结论

建议采纳“LLM 规划优先 + 本地执行闭环”的混合 Agent 工作流，作为 Phase3 后续主路线。

这条路线能保持本地执行的可控与可审计，同时补上 Regex 路由在语义理解、多轮记忆和复杂时间金额解析上的天花板。

## 11. 里程碑进展（2026-04-10）

1. PhaseA：已完成（契约、影子模式、回放与观测基线）。
2. PhaseB：已完成（planner primary 覆盖 query/stats/single-create，含合法化与聚合报表）。
3. PhaseC（本轮）：已完成首批核心落地。
- planner 执行器扩展到 update/delete/batch（writeItems）。
- 接入 PendingIntentState（TTL）支持多轮补全。
- 接入放量双阈值门禁（misjudgeRate + mismatchSamples）。
- 编译与全量单测通过。
4. PhaseC（规范收口）：已完成两处一致性修复。
- 删除确认规则收口为“仅显式 transactionId 且单条命中可单阶段删除，其余均需确认”。
- update 执行与校验口径对齐，支持非金额字段修改（金额可不变）。