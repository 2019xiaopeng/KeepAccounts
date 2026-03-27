# ADR 007: AI Gateway Interface-First

- Status: Accepted
- Date: 2026-03-27

## Context
Next.js 迁移到 Kotlin 后，需要尽快落地 AI 对话与记账解析能力，但当前 Sprint 仍在持续调整 UI 与本地数据流，直接把网络细节写进 ViewModel 会导致后续改造成本上升。

## Decision
1. 在 domain 层先定义 `AiChatGateway` 契约，不让 UI 直接依赖 Retrofit/OkHttp。
2. 在 data 层新增 `SiliconFlowApi` 与 DTO，预留 SiliconFlow `/chat/completions` 请求形态。
3. 先使用 `SiliconFlowAiGatewayStub` 作为过渡实现，后续逐步替换为 SSE 真流式解析。

## Why A over B

### 选 A: 接口边界 + Stub
- UI 开发可与后端联调解耦，Sprint 可以并行推进。
- 后续切换到真实 SSE 解析时，不需要改动调用方。
- 更符合 TRD 中的 Clean Architecture 边界设计。

### 不选 B: ViewModel 直接调用网络
- 网络协议变化会直接影响多个页面状态代码。
- DATA 标签拦截、失败重试、鉴权策略难以复用。
- 测试替身注入困难，回归成本高。

## Consequences
- 当前代码已具备可演进接口层，后续只需替换 Gateway 实现。
- 需要后续 ADR/任务补齐 SSE 分片解析与 `<DATA>` 拦截落库细节。
