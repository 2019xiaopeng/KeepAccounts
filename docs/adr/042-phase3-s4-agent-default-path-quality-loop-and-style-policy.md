# ADR-042: Phase3-S4 主路径切换、质量闭环与风格策略

- 状态：Accepted
- 日期：2026-04-08
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step4 (P3-S4)
- 前置 ADR：ADR-040、ADR-041

## 背景

P3-S3 已完成 query/stats 工具能力与可解释输出，但主链路仍依赖 Prompt 兜底。P3-S4 需要把 Agent 工具链升级为默认主路径，并补足质量治理：

1. requestId 级别可追踪的质量样本沉淀。
2. fallback 与误判可量化，支持后续纠偏。
3. 输出风格统一为“结构化事实优先，抚慰语后置”。

## 决策

1. 路由策略改为 Agent First：
- `sendMessage` 默认先走工具路由（query/stats/write）。
- `promptFallbackEnabled` 作为可控兜底开关。

2. 新增质量反馈数据面：
- 新表 `agent_quality_feedback`。
- 记录字段包含 `requestId`、`routePath`、`stage`、`fallbackUsed`、`isMisjudged`、`errorCode`、`metadataJson`。
- 支持把最近一次样本标注为用户纠错样本。

3. 引入统一风格层：
- 新增 `AgentStyleFormatter`。
- query/stats/write/fallback 输出统一遵循结构化第一段、依据第二段、关怀第三段。

4. 质量指标接口化：
- `AgentQualityFeedbackRepository.computeMetrics` 返回 `accuracyRate`、`fallbackRate`、`misjudgeRate`、`userCorrectionRate`。
- `ChatRepository.getAgentQualityMetrics(windowDays)` 提供窗口化读取。

## 备选方案与否决原因

1. 继续保持 Prompt First。
- 否决：路径不稳定，难以形成可复核质量样本。

2. 仅做日志，不落数据库。
- 否决：无法支持纠错样本标注与窗口化指标计算。

3. 风格文案分散在各 Handler 手写。
- 否决：易漂移，无法持续保证“结构化优先”。

## 影响

正向：

1. 主链路可控性提升，fallback 行为可审计。
2. 质量治理形成数据闭环，可支撑后续误判修复迭代。
3. 回复风格一致，解释信息和追踪信息更稳定。

成本：

1. 需要维护数据库 migration 与 schema 文件。
2. 现有文案断言测试需同步到结构化风格口径。

## 验证

1. 关键路径单测通过：
- `ChatRepositoryBatchLedgerTest`
- `QueryDslEngineTest`
- `AgentQualityFeedbackRepositoryTest`
- `AgentStyleFormatterTest`

2. 全量单测通过：
- `:app:testDebugUnitTest`

## 后续

1. 在 UI 中增加质量指标可视化入口（仅开发/灰度可见）。
2. 将误判样本与回放日志关联，支持一键复盘。
3. 为路由策略增加在线开关配置能力。