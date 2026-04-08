# KeepAccounts 核心技术设计文档 (TRD)

## 1. 架构与技术栈

系统采用 **Android 本地优先 (Local-First)** 架构，目标是保证记账核心能力在离线场景下可用，并减少隐私暴露面。后续规划为在用户授权登录后提供**可选云同步**，但不改变离线可用的基础原则。

1. 开发语言与平台
- Kotlin 1.9+
- Android 原生（Jetpack Compose）

2. 分层结构
- UI 层：`ui/screens` + `ui/components`，仅负责渲染与交互。
- 状态层：`ui/viewmodel`，通过 `StateFlow` 暴露状态与事件。
- 领域契约层：`domain/contract`，定义 AI 网关接口与流式事件模型。
- 数据层：`data/repository` 负责业务编排，向下依赖 DAO 与远端网关。

3. 本地存储
- Room（SQLite）作为交易与聊天记录的事实源。
- DataStore 保存用户偏好（主题、预算、提醒时间、AI 配置等）。

4. 外部网络依赖
- 仅调用 SiliconFlow `/chat/completions`（流式）。
- 其余业务逻辑（账本、搜索、统计、设置）均本地完成。

---

## 2. 数据模型设计

### 2.1 `TransactionEntity`（账单流水）

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long（自增主键） | 交易主键 |
| `type` | Int | `0=支出`，`1=收入` |
| `amount` | Double | 金额（正值） |
| `categoryName` | String | 分类名 |
| `categoryIcon` | String | 分类图标标识（当前主要用于兼容字段） |
| `remark` | String | 备注 |
| `recordTimestamp` | Long | 交易发生时间（核心时间字段） |
| `createdTimestamp` | Long | 入库时间 |

### 2.2 `ChatMessageEntity`（聊天记录）

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long（自增主键） | 消息主键 |
| `role` | String | `user` / `assistant` |
| `content` | String | 消息正文（可能包含 `<DATA>...</DATA>`） |
| `isReceipt` | Boolean | 是否为回执消息 |
| `transactionId` | Long? | 可空外键，关联 `TransactionEntity.id` |
| `timestamp` | Long | 消息时间 |

### 2.3 DataStore（用户设定）

核心字段（`UserSettingsState`）：
- 用户信息：`userName`、`homeSlogan`、`userAvatarUri`
- 主题配置：`theme`
- AI 配置：`aiConfig`（名称、头像、语气、聊天背景）
- 账本配置：`ledgerCurrency`、`defaultLedgerName`、`reminderTime`、`monthlyBudget`
- 分类配置：`manualCategories`

---

## 3. 关键业务流

### 3.1 手动记账链路

`ManualEntryScreen` -> `MainViewModel.addManualTransaction` -> `TransactionRepository.insertTransaction` -> `TransactionDao.insertTransaction`。

交易入库后，由 `observeAllTransactions(): Flow<List<TransactionEntity>>` 触发首页、账本、搜索等页面自动刷新。

### 3.2 AI 对话记账链路

1. `ChatScreen` 提交用户输入。
2. `ChatViewModel` 调用 `ChatRepository.sendMessage`。
3. `ChatRepository` 组装系统提示词（含当前日期时间与时间推算规则），调用 `AiChatGateway.streamReply`。
4. `SiliconFlowAiGateway` 解析流：
- 普通文本 -> `AiStreamEvent.TextDelta`
- `<DATA>...</DATA>` -> `AiStreamEvent.ReceiptParsed(AiReceiptDraft)`
5. `ChatRepository` 根据 `AiReceiptDraft` 生成交易并落库，同时将 `transactionId` 回写到聊天消息。

### 3.3 记账时间解析策略（当前实现）

`resolveRecordTimestamp` 优先级：
1. 用户原始输入中的显式日期/时间（最高优先级）
2. AI 回执 `recordTime`
3. AI 回执 `date`
4. 当前系统时间

补充规则：
- 支持相对日期（今天/昨天/前天/大前天/明天/后天）。
- 支持中文时段映射：早上 08:00、中午 12:00、下午 15:00、晚上 19:00、深夜 23:00。
- “昨天”无明确时分时，默认 12:00。

### 3.4 账本时间过滤与趋势渲染

`MainViewModel` 使用 `StateFlow + combine` 维护：
- `ledgerTimeSelection`（年/月）
- `ledgerFilterMode`（月/年）
- `filteredTransactions`（账本明细唯一消费流）

趋势图采用 `List<Double?>`：
- 当前月仅绘制到今天；未来日期填 `null`。
- 绘制时遇 `null` 断线，形成“未来留白”。

### 3.5 每日提醒通知

已实现 `AlarmManager + BroadcastReceiver + NotificationCompat` 链路：
- 支持设置提醒时间。
- 支持开机后自动恢复提醒。

---

## 4. 实现状态对齐（2026-03）

| 能力 | 状态 | 说明 |
| :--- | :--- | :--- |
| 文本 AI 对话记账 | 已实现 | 流式文本 + 回执解析 + 入库 |
| 回执 `recordTime` 全链路 | 已实现 | Prompt、网关、解析、入库、UI 已对齐 |
| 账本月/年联动过滤 | 已实现 | 由 ViewModel 派生流统一驱动 |
| 当前月趋势未来留白 | 已实现 | `null` 点位 + 断线绘制 |
| 搜索（时间/金额/分类/备注） | 已实现 | 本地多条件匹配 |
| CSV/JSON 导入导出真实读写 | 暂缓 | 当前仅保留入口，作为离线备份迁移低优先级方案 |
| 语音输入（SpeechRecognizer） | 不单列开发 | 依赖系统输入法语音能力，应用内 Mic 暂不实现 |
| 图片/OCR 记账 | 未实现 | 聊天页暂无图片上传与 OCR 解析链路 |
| Rich Ongoing Notifications | 未实现 | 暂无前台服务胶囊通知能力 |
| 分类拖拽排序/图标编辑 | 未实现 | 当前仅支持分类新增与删除 |
| 账号登录/身份绑定（含微信） | 规划中 | 先完成技术选型与合规评估 |
| 可选云同步（多设备） | 规划中 | Local-First 前提下处理冲突合并与离线回放 |
| AI 人性化交互策略 | 规划中 | 需补齐澄清、纠错、可解释建议策略 |

---

## 5. 设计约束与风险提示

1. 时间语义风险
- 中文自然语言时间语义复杂，任何 prompt 规则更新都应同步本地解析策略，避免“模型正确、客户端回退错误”。

2. 数据一致性风险
- 聊天回执展示时间必须优先使用交易 `recordTimestamp`，避免消息时间与账本时间不一致。

3. 功能文案风险
- 设置页文案与真实能力需保持一致。对“占位入口”必须显式标注未完成，避免误导测试与验收。

4. 构建验证建议
- 文档更新后的功能开发建议至少执行一次 `:app:compileDebugKotlin` 验证主链路可编译。

5. 同步一致性风险
- 多设备编辑同一时间窗口的账单时需定义冲突优先级（时间戳、字段级合并、人工确认），否则会出现数据“互相覆盖”。

6. 合规与隐私风险
- 登录与微信绑定涉及用户标识和授权管理，需在落地前明确数据最小化采集、授权撤回与隐私条款更新策略。