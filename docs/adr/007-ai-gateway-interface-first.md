# ADR 007: AI Gateway Interface First

- Status: Accepted
- Date: 2026-03-27

## Context
从 Next.js 迁移到 Kotlin 后，AI 对话与记账解析将先接入 SiliconFlow，但后续可能切换模型提供方、增加离线方案，或引入服务层做策略编排。

## Decision
先定义领域接口 `AiChatGateway`（A），由数据层实现；当前先接入 `SiliconFlowAiGatewayStub`，后续替换为真实流式实现。

## Why A over B

### A: 先做接口抽象（AiChatGateway）
- UI/ViewModel 只依赖统一契约，后续换供应商不改页面层。
- 流式文本与 `<DATA>` 结构化回执可在网关层统一处理，避免逻辑散落在 Screen。
- 便于单元测试：可直接注入 fake/stub 校验记账流程。

### B: 页面层直接调用 Retrofit API
- 供应商耦合高，改 endpoint 或协议时影响范围大。
- SSE 解析与错误处理容易分散到多个页面。
- 难以做离线 fallback 与多模型策略切换。

## Consequences
- 当前多一层抽象，短期代码量略增。
- 长期可扩展性和可测试性更好，适合后续后端能力演进。
