# ADR 032: AI 修改记账能力与可执行事项入口

- Status: Accepted
- Date: 2026-03-30

## Context
当前 AI 对话在结构化回执上仅落地 `create`，导致用户在自然纠错场景中（例如“刚刚记错了，是15块钱”“把昨天中午午餐改成10块”）无法修改已有交易，只会新增一条错误记录。

另外，设置中缺少“AI 对话明确能做什么”的独立说明入口，用户对可执行指令边界不清晰。

## Decision
1. 在数据层增加更新能力：
- `TransactionDao` 新增 `getRecentTransactions(limit)`。
- `TransactionDao` 新增 `updateTransactionById(...)`。

2. 在 AI 记账管道中增加 `update` 执行路径：
- `ChatRepository` 通过 `AiReceiptDraft.action` + 用户纠错关键词识别 `update` 意图。
- 针对 update 进行“目标交易定位”评分：日期、时段、分类、收支类型、最近性。
- 定位成功后更新金额/分类/时间/备注，失败时返回可补充提示，不误创建新交易。

3. 强化时间解析，支持模糊时段：
- 支持“昨天中午/今天晚上/深夜”等映射到默认时刻。
- 修正中午时段转换逻辑，避免小时误判。

4. 系统提示词升级为双动作契约：
- 明确 `create` 与 `update`。
- 明确纠错关键词优先触发 `update`。
- 引入 AI_Humanized 的最小打扰与单问澄清原则。

5. 设置页新增独立入口：
- 在设置菜单中增加“AI 对话可做事项”。
- 页面列出可直接说的示例：记录收支、修改上一条、按条件修正。

## Why A over B

### A: 在 ChatRepository 内做 update 识别与落地（本次）
- 复用现有 `<DATA>` 契约与持久化链路，改动可控。
- 可在仓库层统一处理“误判回退”和“定位失败提示”。
- 与 UI 解耦，不需要新建复杂编辑流程。

### B: 强制跳转账本手动编辑
- 中断对话流程，违背“低纠错成本”。
- 用户需要学习额外页面操作，体验割裂。

## 操作记录
1. 修改 `app/src/main/java/com/qcb/keepaccounts/data/local/dao/TransactionDao.kt`
- 新增近期查询与更新接口。

2. 修改 `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
- 新增 `applyReceiptTransaction`、`tryUpdateTransaction`、`resolveUpdateTargetTransaction`。
- 新增 `action` 归一化和纠错关键词识别。
- 新增模糊时段解析与日期时间 cue 识别。
- 系统提示词加入 update 规范和人性化规则。

3. 修改 `app/src/main/java/com/qcb/keepaccounts/ui/navigation/KeepAccountsDestination.kt`
- 新增 `SETTINGS_TYPE_AI_CAPABILITIES`。

4. 修改 `app/src/main/java/com/qcb/keepaccounts/ui/screens/ProfileScreen.kt`
- 增加“AI 对话可做事项”菜单入口。

5. 修改 `app/src/main/java/com/qcb/keepaccounts/ui/screens/AppSettingsScreen.kt`
- 新增 AI 能力说明页面与示例文案。

## Consequences
- 正向：
  - 修正类语句可直接更新交易，不再默认新增。
  - 对“昨天中午午餐改价”这类自然语句的成功率显著提升。
  - 用户在设置页可明确知道 AI 能做什么。
- 代价：
  - 目标交易定位采用启发式评分，仍可能在极端歧义语句下需要用户补充信息。

## Next
1. 为 update 流程补自动化测试样例（最近一笔修正、按日期分类修正、定位失败回退）。
2. 在聊天回执中加“已修改第N笔/某日期某分类”的更明确确认文案。
3. 统计 update 成功率与补问率，持续优化定位评分。