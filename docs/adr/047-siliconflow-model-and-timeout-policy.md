# ADR-047: SiliconFlow 默认模型切换与超时策略

- 状态：Accepted
- 日期：2026-04-11
- 决策者：KeepAccounts 团队
- 关联阶段：Phase3 Step5 (P3-S5)
- 前置 ADR：ADR-046

## 背景

线上对话中出现两类稳定性问题：

1. 默认模型仍为 `Pro/moonshotai/Kimi-K2.5`，与当前迭代目标模型不一致，影响统一调优。
2. AI 请求使用 OkHttp 默认超时（10s），在流式生成时容易触发 `SocketTimeoutException`，表现为“AI 服务响应超时”。

## 决策

1. 默认模型统一切换为 `deepseek-ai/DeepSeek-V3`。
2. 新增配置项 `SILICONFLOW_MODEL`，允许通过配置文件集中切换模型。
3. AI 客户端超时策略调整为：
- `connectTimeout = 60s`
- `readTimeout = 60s`
- `writeTimeout = 60s`
- `callTimeout = 75s`

## 为什么选 A 不选 B

### A：DeepSeek-V3 + 显式超时配置（本决策）

1. 模型入口统一，便于在同一基线下做质量与延迟观测。
2. 显式超时比系统默认值更适配流式 LLM 响应。
3. 通过 `SILICONFLOW_MODEL` 保留后续快速回切能力。

### B：继续使用 Kimi-K2.5 + 默认超时

1. 默认 10s 超时对流式推理容错不足，线上超时噪声高。
2. 模型配置分散在代码常量中，切换成本高且容易遗漏。

## 影响

正向：

1. “AI 服务响应超时”触发频率下降。
2. 模型切换路径标准化，便于灰度与回滚。

成本：

1. 文档与配置项需同步维护。
2. 需要通过回归测试确认 query/stats/write 路径无行为回归。

## 回滚策略

1. 将 `SILICONFLOW_MODEL` 回切到历史模型值即可。
2. 如需进一步压缩响应时延，可在后续迭代中按场景分层配置超时参数。

## 关联文档

1. `docs/p3-s5-agent-workflow-upgrade-plan.md`
2. `docs/p3-s5-phasec-checklist.md`
