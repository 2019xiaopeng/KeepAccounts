# KeepAccounts 四阶段开发与测试执行手册（单文档版）

> 版本：2026-04-06
> 目的：用一个文档完成开发流程定义，覆盖四项需求、四个开发阶段、四次阶段测试、四段 Agent Prompt。

## 1. 需求范围（仅保留四项）

1. 一次性记录多笔账单（同一条输入落多笔交易）。
2. 时间语义简化（不强依赖精确消费时刻）。
3. 从 Prompt 拼接过渡到 Agent + 工具调用。
4. Android 16 Rich Ongoing Notifications（前台任务 + 状态胶囊/持续通知）。

## 2. 总体开发流程（严格不进 main）

### 2.1 分支策略

1. 只把 `main` 作为只读基线，不直接开发、不直接 push。
2. 先创建集成分支：`release/ai-4phase-2026q2`。
3. 每个阶段使用独立功能分支：
   - `feat/p1-batch-ledger`
   - `feat/p2-time-semantics`
   - `feat/p3-agent-tools`
   - `feat/p4-rich-ongoing-notify`
4. 每阶段完成后：功能分支 commit + push + 提 PR 到 `release/ai-4phase-2026q2`。
5. 四阶段全部通过后，再由你决定是否从集成分支合并回 `main`。

### 2.2 每阶段固定执行模板

```bash
# 0) 准备集成分支（只做一次）
git checkout main
git pull
git checkout -b release/ai-4phase-2026q2
git push -u origin release/ai-4phase-2026q2

# 1) 开始某阶段（以 P1 为例）
git checkout release/ai-4phase-2026q2
git pull
git checkout -b feat/p1-batch-ledger

# 2) 开发 + 测试通过后提交
git add .
git commit -m "feat(ai): phase1 batch ledger apply and result card"
git push -u origin feat/p1-batch-ledger

# 3) 发起 PR：feat/p1-batch-ledger -> release/ai-4phase-2026q2
```

### 2.3 阶段交付硬门槛（DoD）

1. 编译通过（至少 `:app:compileDebugKotlin`）。
2. 阶段测试通过（本手册定义的 T1/T2/T3/T4 对应项）。
3. 关键技术决策补 ADR（重大选型必须写）。
4. 已完成 commit + push 到阶段分支（非 main）。

## 3. 四阶段开发与四次阶段测试

## Phase 1（P1）批量记账闭环

### 开发目标

把“单条输入 -> 多条交易”打通，允许部分成功。

### 开发任务

1. 统一回执归一化：支持多 `<DATA>` 与单 `<DATA>{items:[...]}</DATA>` 两种形态。
2. 应用层从单草稿改为 `List<LedgerActionDraft>` 顺序执行。
3. 每条草稿独立入账，失败项不阻断成功项。
4. 聊天结果卡片支持“成功X/失败Y”与失败原因展示。

### 阶段测试 T1（批量入账正确性）

1. 输入：`3.5的杨桃，18块钱的中餐，16.9的奶茶加蛋糕`。
2. 预期：至少新增3条独立交易，不合并为1条。
3. 异常注入：让其中一条缺分类，预期其余条目仍成功。
4. 通过标准：数据库记录数与成功条数一致，UI 显示失败原因与重试提示。

### P1 提交与推送

```bash
git checkout release/ai-4phase-2026q2
git pull
git checkout -b feat/p1-batch-ledger
git add .
git commit -m "feat(ai): phase1 batch ledger apply pipeline"
git push -u origin feat/p1-batch-ledger
```

## Phase 2（P2）时间语义简化

### 开发目标

时间从“精确时刻”转为“账单生效时间 + 日期标签优先”。

### 开发任务

1. 统一规则：今天/昨天/前天优先，缺省默认今天。
2. 模糊词（中午/晚上）仅用于定位，不强制精确到分秒。
3. 修改账单时更新时间字段（语义：当前生效时间）。
4. 聊天、账本、编辑页统一时间展示口径。

### 阶段测试 T2（时间一致性）

1. 输入：`把昨天中午午餐改成10块`。
2. 预期：命中昨天对应记录并更新金额，时间标签显示“昨天”。
3. 输入：`晚饭改成26`（无精确时间）。
4. 预期：不追问秒级时间，按日期语义完成更新。
5. 通过标准：聊天回执时间、账本时间、编辑预填时间一致。

### P2 提交与推送

```bash
git checkout release/ai-4phase-2026q2
git pull
git checkout -b feat/p2-time-semantics
git add .
git commit -m "feat(ai): phase2 simplified time semantics"
git push -u origin feat/p2-time-semantics
```

## Phase 3（P3）Agent + 工具调用（核心）

### 开发目标

把复杂逻辑从 Prompt 约束迁移到可验证工具调用，构建“内嵌式账本 Agent”，使其可全面接管当前 App 的账本能力。

### P3 接管能力范围（必须覆盖）

1. 记账（Create）：支持单笔与多笔入账，且每笔都有结构化反馈。
2. 改账（Update）：支持按时间语义（今天/昨天/前天）、关键词、金额范围或交易ID定位并修改。
3. 删账（Delete）：支持单笔删除与批量删除，执行前必须可预览影响范围。
4. 查账（Read）：支持“最近一笔”“最近N天”“最近一周消费最高记录”等检索。
5. 分析（Analyze）：支持周/月/年消费习惯分析与分类统计（占比、TopN、趋势）。
6. 回执与可解释性：所有动作都返回结构化结果（成功/失败/部分成功+原因）。
7. 人性化回答：在保持工具精确执行的同时，保留管家式自然对话与情境化反馈。
8. 习惯偏好识别：支持“最频繁消费商家/类别/时段”检索，例如“总是吃同一家饭”。

### 开发任务

1. 定义工具集（从最小集扩展为可接管集）：
   - `preview_actions(actions[])`
   - `create_transactions(items[])`
   - `update_transactions(filters, patch)`
   - `delete_transactions(filters)`
   - `query_transactions(filters)`
   - `query_spending_stats(window, groupBy, metric)`
2. 形成编排：LLM 负责意图理解，Orchestrator 负责任务编排，Tool Executor 负责落库执行。
3. 工具层参数强校验（金额、日期、分类、类型、过滤条件、分页参数）。
4. 增加可追踪日志（requestId、toolName、args、result、latency、errorCode）。
5. 回执统一：聊天页、账本页和分析结果页统一消费结构化回执模型。
6. 增加后台质量日志与评估闭环：记录模型判断、工具命中、纠错结果，用于持续提准。
7. 增加对话风格策略层：分离“执行结果”和“人性化表达”，避免风格影响工具精度。

### P3 拉长实施步骤（建议）

1. P3-A：工具契约与日志底座
   - 输出：工具接口定义、请求上下文（requestId）、工具调用日志表。
   - 出口标准：任意一次工具调用都可回放。
2. P3-B：CRUD 工具落地
   - 输出：create/update/delete 的可执行工具与参数校验。
   - 出口标准：单笔、多笔、批量修改/删除可稳定执行且有结构化回执。
3. P3-C：查询与分析工具落地
   - 输出：最近记录检索、高消费记录检索、高频消费偏好检索、周/月/年分析、分类统计。
   - 出口标准：查询结果与账本数据一致，统计结果可复核，频次统计可解释。
4. P3-D：策略收口与回退机制
   - 输出：Prompt 直执路径降级为兜底，默认走 Agent 工具编排。
   - 出口标准：主路径稳定，异常场景可回退且不丢失用户请求。
5. P3-E：准确率与人性化双优化
   - 输出：后台评估看板、误判样本归档、话术模板与风格策略。
   - 出口标准：核心场景准确率稳定提升且回复风格保持“真实管家”体验。

### 阶段测试 T3（工具调用可靠性）

1. 改账路径：输入 `我记错了，把刚才那笔咖啡改成12`。
   - 预期：先走 `preview_actions`，再走 `update_transactions`。
2. 批量入账路径：输入多笔消费，预期走 `create_transactions(items[])` 并支持部分成功。
3. 查询路径：输入 `最近一笔记录`、`最近一周消费最高的一笔`，预期走 `query_transactions(filters)`。
4. 频次路径：输入 `最近最频繁的消费是什么`、`是不是总吃同一家`，预期返回商家/类别频次与依据。
5. 分析路径：输入 `分析过去一周/一个月/一年消费习惯`、`统计哪一类消费最多`，预期走 `query_spending_stats(...)`。
6. 人性化路径：在查询/分析完成后，回复应保留管家式自然表达，不影响结构化结果准确性。
7. 断言：日志可回放完整调用链（requestId 维度），并可定位误判来源。
8. 通过标准：同一场景重复3次，工具调用路径一致，结果一致。

### P3 提交与推送

```bash
git checkout release/ai-4phase-2026q2
git pull
git checkout -b feat/p3-agent-tools
git add .
git commit -m "feat(ai): phase3 agent tool orchestration"
git push -u origin feat/p3-agent-tools
```

## Phase 4（P4）Android 16 Rich Ongoing Notifications + 全链路收口

### 开发目标

支持后台持续处理与可见状态，形成端到端可恢复体验。

### 开发任务

1. 增加 AI 前台服务承载后台执行。
2. 状态机：`PROCESSING`、`PARTIAL_SUCCESS`、`SUCCESS`、`FAILED`。
3. 通知点击进入“本次 AI 执行详情页”。
4. Android 16 用 Rich Ongoing Notifications，低版本降级普通前台通知。
5. 对齐聊天结果卡片与通知文案口径。

### 阶段测试 T4（后台稳定性与状态可见性）

1. 触发批量记账后切后台3分钟，再回前台。
2. 预期：通知状态持续可见，回到应用后结果可追溯。
3. 断网重连测试：处理中断网再恢复。
4. 预期：最终状态正确（成功/部分成功/失败）且不丢失处理摘要。
5. 通过标准：Android 16 与 Android 16 以下均可正常工作（高版本增强、低版本降级）。

### P4 提交与推送

```bash
git checkout release/ai-4phase-2026q2
git pull
git checkout -b feat/p4-rich-ongoing-notify
git add .
git commit -m "feat(ai): phase4 rich ongoing notification and execution detail"
git push -u origin feat/p4-rich-ongoing-notify
```

## 4. 需求覆盖矩阵（确保“完成所有需求”）

| 用户需求 | 完成阶段 | 对应测试 |
| --- | --- | --- |
| 一次性记录多笔账单 | P1 | T1 |
| 时间语义简化 | P2 | T2 |
| Agent + 工具调用 | P3 | T3 |
| Android 16 Rich Ongoing Notifications | P4 | T4 |

## 5. 四段 Agent Prompt（可直接复制使用）

## Prompt 1：Batch Ledger Agent（P1）

```text
你是 KeepAccounts 的 Batch Ledger Agent。目标是在分支 feat/p1-batch-ledger 完成“单条输入生成多条交易”的全链路改造。

硬约束：
1) 禁止在 main 分支开发、提交或推送。
2) 目标分支：feat/p1-batch-ledger，PR 指向 release/ai-4phase-2026q2。
3) 兼容两种回执：多个 <DATA> 与单 <DATA>{items:[...]}</DATA>。
4) 允许部分成功，失败项不可阻断其他条目。

必须交付：
1) 代码改动（解析归一化、批量执行、结果卡片）。
2) T1 测试证据（输入样例、数据库条数、UI 结果）。
3) 重大选型 ADR（如回执协议归一化策略）。
4) git commit + push 命令执行记录。

提交信息建议：feat(ai): phase1 batch ledger apply pipeline
```

## Prompt 2：Time Semantics Agent（P2）

```text
你是 KeepAccounts 的 Time Semantics Agent。目标是在分支 feat/p2-time-semantics 完成“时间语义简化与跨页面一致显示”。

硬约束：
1) 禁止在 main 分支开发、提交或推送。
2) 目标分支：feat/p2-time-semantics，PR 指向 release/ai-4phase-2026q2。
3) 规则：今天/昨天/前天优先；无日期默认今天；不追问秒级时间。
4) 修改账单后必须更新时间字段（生效时间语义）。

必须交付：
1) 解析与更新逻辑改造。
2) 聊天回执、账本、编辑页时间展示口径统一。
3) T2 测试证据（“把昨天中午午餐改成10块”等场景）。
4) git commit + push 命令执行记录。

提交信息建议：feat(ai): phase2 simplified time semantics
```

## Prompt 3：Agent Tools Orchestrator（P3）

```text
你是 KeepAccounts 的 Agent Tools Orchestrator。目标是在分支 feat/p3-agent-tools 完成从 Prompt 约束到工具执行的核心迁移。

硬约束：
1) 禁止在 main 分支开发、提交或推送。
2) 目标分支：feat/p3-agent-tools，PR 指向 release/ai-4phase-2026q2。
3) 工具集必须覆盖 preview/create/update/delete/query/stats 六类能力。
4) 所有工具调用必须有 requestId 级别日志可追踪，并可回放。
5) 回复必须保留人性化管家风格，但不得影响工具执行准确性。

必须交付：
1) P3-A：工具契约 + 日志底座。
2) P3-B：CRUD 工具（create/update/delete）及参数校验。
3) P3-C：查询/分析工具（query_transactions/query_spending_stats）。
4) P3-D：默认工具路径 + Prompt 兜底回退。
5) P3-E：后台质量日志 + 准确率优化闭环 + 风格策略收口。
6) T3 测试证据（重复3次调用路径一致，可回放，含频次检索场景）。
7) 重大选型 ADR（工具边界、执行策略、回退策略、风格策略）。
8) git commit + push 命令执行记录。

提交信息建议：feat(ai): phase3 agent tool orchestration
```

## Prompt 4：Android System UX Agent（P4）

```text
你是 KeepAccounts 的 Android System UX Agent。目标是在分支 feat/p4-rich-ongoing-notify 完成 Android 16 Rich Ongoing Notifications 与低版本降级方案。

硬约束：
1) 禁止在 main 分支开发、提交或推送。
2) 目标分支：feat/p4-rich-ongoing-notify，PR 指向 release/ai-4phase-2026q2。
3) 状态机必须包含 PROCESSING / PARTIAL_SUCCESS / SUCCESS / FAILED。
4) 通知文案必须与聊天结果卡片口径一致。

必须交付：
1) 前台服务 + 通知状态更新 + 详情页跳转。
2) Android 16 增强与低版本降级。
3) T4 测试证据（后台切换、断网重连、状态恢复）。
4) 重大选型 ADR（前台服务与通知策略）。
5) git commit + push 命令执行记录。

提交信息建议：feat(ai): phase4 rich ongoing notification and execution detail
```

## 6. 执行提醒

1. 每个阶段只做该阶段范围，避免跨阶段混改。
2. 每阶段结束必须先过对应测试再 commit/push。
3. 任何阶段都不允许直接向 `main` push。
4. 四阶段完成后，以 `release/ai-4phase-2026q2` 作为统一验收基线。