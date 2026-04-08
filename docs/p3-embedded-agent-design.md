# Phase3 内嵌式 Agent 详细设计（草案）

更新时间：2026-04-08
目标分支：feat/p3-agent-tools
对应阶段：Phase3（Agent + 工具调用）

## 1. 设计目标

构建一个可内嵌在当前 KeepAccounts App 内的账本 Agent，使其具备“可接管、可追踪、可回放、可回退、可陪伴”五个核心特性：

1. 可接管：覆盖账本增删改查与消费分析。
2. 可追踪：每一次工具调用都记录 requestId、参数、结果。
3. 可回放：可按 requestId 重建完整执行链。
4. 可回退：工具链异常时可回退到安全兜底路径。
5. 可陪伴：在保证执行准确的同时，保持人性化管家式回答体验。

## 2. 功能范围（接管清单）

### 2.1 交易 CRUD

1. 新增交易：支持单笔与多笔。
2. 修改交易：支持按交易ID、时间语义、分类、关键词等定位后修改。
3. 删除交易：支持单笔与批量删除。
4. 查询交易：支持最近一笔、最近N笔、时间窗口检索、排序检索。

### 2.2 分析能力

1. 时间窗口分析：周/月/年消费趋势。
2. 分类统计：按类别统计金额、占比、TopN。
3. 习惯分析：高频消费时段、高频类别、异常波动提醒。
4. 可解释反馈：每个分析结论都能给出数据依据。

### 2.3 偏好与频次洞察

1. 高频消费识别：识别最近一周/月最频繁消费商家、类别、时段。
2. 复购行为识别：支持“总吃同一家”“总点同一类餐”这类习惯性消费判断。
3. 频次+金额联合洞察：区分“花得最多”和“买得最频繁”。
4. 结构化依据输出：返回频次统计样本数量、时间窗口、排序依据。

### 2.4 交互与反馈

1. 所有动作均返回结构化回执（success/failure/partial_success）。
2. 用户可直接询问：
   - 最近一笔记录
   - 最近一周消费最高的一笔
   - 最近最频繁消费的是哪家/哪类
   - 某次记录修改
   - 周/月/年消费习惯
   - 某类消费统计
3. 对话输出采用“双层结构”：
   - 机器可解析层：结构化结果（供 UI/日志/回放使用）
   - 用户可阅读层：人性化管家式表达（关怀、建议、提醒）

## 3. 技术架构（内嵌式）

## 3.1 组件划分

1. Intent Layer（LLM）
   - 负责意图理解与槽位抽取，不直接落库。
2. Orchestrator Layer（本地编排器）
   - 负责工具顺序、幂等控制、错误分流。
3. Tool Layer（本地工具）
   - 负责 create/update/delete/query/stats 的实际执行。
4. Validation Layer（参数校验）
   - 负责金额、类型、时间、分类、过滤条件合法性校验。
5. Audit Layer（日志审计）
   - 负责 requestId 级别的调用链记录。
6. Receipt Layer（回执组装）
   - 负责输出统一结构供聊天页/账本页展示。
7. Response Style Layer（风格策略层）
   - 负责把结构化结果转换为人性化表达，不干预工具执行。
8. Quality Feedback Layer（质量反馈层）
   - 负责聚合误判样本、纠错样本和命中率指标，驱动持续提准。

## 3.2 推荐工具接口

1. preview_actions(actions[])
   - 用途：执行前预演，返回命中范围与风险提示。
2. create_transactions(items[])
   - 用途：新增单笔/多笔交易。
3. update_transactions(filters, patch)
   - 用途：按过滤条件修改交易。
4. delete_transactions(filters)
   - 用途：按过滤条件删除交易。
5. query_transactions(filters)
   - 用途：交易检索与排序。
6. query_spending_stats(window, groupBy, metric)
   - 用途：统计与趋势分析。

## 3.3 统一返回模型（建议）

1. requestId
2. toolName
3. status（success/partial_success/failure）
4. summary（成功数、失败数、命中数）
5. items（逐条执行结果）
6. errors（错误码、错误原因、修复建议）

## 4. 关键执行流程

1. ChatRepository 接收用户输入。
2. Orchestrator 生成 requestId，并先调用 preview_actions。
3. 根据预演结果调用 create/update/delete/query/stats 之一或组合。
4. Validation 层在每次执行前做参数校验。
5. Tool 执行后写入 Audit 日志。
6. Receipt Layer 组装结构化回执返回 UI。
7. 若执行异常，进入回退策略（不丢请求，不丢日志）。

## 5. 数据模型建议

建议新增两张表（或等价存储）：

1. agent_runs
   - requestId
   - userInput
   - status
   - startTime
   - endTime
2. agent_tool_calls
   - requestId
   - stepIndex
   - toolName
   - argsJson
   - resultJson
   - status
   - errorCode
   - latencyMs
3. agent_quality_feedback
   - requestId
   - intentType
   - expectedResult
   - actualResult
   - mismatchType
   - correctedByUser
   - createdAt

## 6. Phase3 拉长实施计划

### P3-A：基础设施

1. 完成工具契约定义与参数模型。
2. 建立 requestId 与工具日志链。
3. 验收：任意调用可回放。

### P3-B：交易执行能力

1. 落地 create/update/delete 工具。
2. 实现批量与部分成功语义。
3. 验收：增删改路径稳定并可解释。

### P3-C：查询与分析能力

1. 落地 query_transactions 与 query_spending_stats。
2. 支持“最近一笔”“一周最高”“月/年习惯分析”“分类统计”。
3. 验收：结果与账本一致，统计可复核。

### P3-D：收口与切换

1. 默认路径切换到工具链。
2. 保留 Prompt 兜底降级。
3. 验收：核心场景稳定，异常可回退。

### P3-E：准确率与人性化双优化

1. 建立后台质量看板：命中率、误判率、回退率、用户纠错率。
2. 建立误判样本闭环：自动归档高价值失败样本。
3. 建立风格策略模板：不同场景下保持“管家式”但不过度冗长。
4. 验收：准确率稳定提升且用户侧主观体验不退化。

## 7. 测试策略（T3 拆分）

1. T3-CRUD：单笔/多笔新增、修改、删除。
2. T3-Query：最近记录、时间窗口、高消费检索。
3. T3-Frequency：高频商家/类别/时段检索。
4. T3-Analyze：周/月/年趋势与分类统计。
5. T3-Replay：requestId 调用链回放一致。
6. T3-Determinism：同一输入重复3次路径一致。
7. T3-Humanized：结果正确前提下，回复保持管家风格与可读性。

## 8. 风险与对策

1. 风险：意图歧义导致误改误删。
   - 对策：强制 preview + 高风险二次确认。
2. 风险：工具参数漂移导致执行不稳定。
   - 对策：统一校验层 + 错误码标准化。
3. 风险：日志过多影响性能。
   - 对策：分级日志与定期归档。
4. 风险：路径切换引发回归。
   - 对策：保留兜底路径，灰度切换。
5. 风险：人性化表达覆盖结构化事实。
   - 对策：强制“先结构化、后表达”双层输出，表达层只读结构化结果。
6. 风险：复杂需求（频次+金额+时间窗口）导致查询歧义。
   - 对策：统一 query DSL 与 disambiguation 规则，必要时追问单个关键槽位。

## 9. 准确率提升机制

1. 离线评测：固定用例集每日回归（CRUD/查询/分析/频次）。
2. 在线评测：记录真实请求中的失败模式并自动分类。
3. 纠错学习：用户手动修正后的样本进入高权重训练/规则池。
4. 阈值告警：当误判率或回退率超过阈值时自动告警。
5. 版本对比：每次策略变更需有 A/B 精度对比结论。

## 10. 交付清单

1. 工具接口文档。
2. Orchestrator 时序图（可选）。
3. 参数校验规则表。
4. 日志字段规范。
5. T3 证据文档。
6. Phase3 审计记录文档。
7. 质量评估与提准报告（含误判样本统计）。
