# 删除意图与失败回执误渲染修复记录（2026-04-15）

## 1. 现象

用户输入删除请求（例如“把12号卤肉饭那笔删了”）后，助手文本为“信息不足需补充”，但 UI 同时渲染“✅ 已记账”卡片，金额显示为 `-0.00`，形成强误导。

## 2. 根因分析

1. 删除意图识别链路不完整
- Chat 主 Prompt 只列 create/update，缺少 delete。
- Lite Planner 提示词对 delete 过度保守，容易把 delete 导向 unknown。
- 路由高风险信号词缺少“删了”，导致 Lite -> Pro 升级不稳定。

2. 回执失败态被 UI 成功化
- ChatScreen 的 payload 转 summary 逻辑曾固定 `successCount=1`、`failureCount=0`。
- 回执展示条件只要命中 isReceipt/payload 就画卡片，且金额缺失时兜底为 `0.00`。
- 在失败场景下，造成“失败执行 + 成功卡片”的冲突体验。

## 3. 修复方案

### 3.1 删除意图链路修复

1. Chat 主 Prompt 增加 delete 动作和识别规则。
2. Lite Planner 改为：明确单目标且线索充分时允许 `delete_transactions`，线索不足才输出 unknown。
3. 路由 deleteSignals 补充“删了”。

### 3.2 回执渲染一致性修复

1. ChatScreen 解析 `<RECEIPT>` 时，真实读取 `items/successCount/failureCount/errors`。
2. 移除硬编码成功统计，按 payload 实际状态生成 `AiChatReceiptSummary`。
3. 收紧回执卡片展示条件：仅当存在可解析的 `receiptSummary` 才渲染卡片。
4. 失败场景保留失败状态，不再默认落入“✅ 已记账 / -0.00”。

### 3.3 回执元信息补齐

在 `buildReceiptMetaTag` 中补充失败场景的顶层 action/category/amount/desc/recordTime/date（由首个失败 draft 回填），减少 UI 侧解析歧义。

## 4. 影响范围

1. `ChatRepository.kt`
2. `SiliconFlowPlannerGateway.kt`
3. `ModelRoutingPolicy.kt`
4. `ChatScreen.kt`

## 5. 回归验证建议

1. 删除语句（包含“删了/删掉/删除”）应稳定进入 delete 路径。
2. 线索不足时应给出补充信息提示，且不渲染成功卡片。
3. 失败回执应显示失败计数或失败提示，不应出现默认 `-0.00` 成功卡。
4. 成功新增/修改/删除场景不回退，卡片仍按原逻辑正确展示。
