# P1 批量记账工作基线

更新时间：2026-04-06
目标分支：`feat/p1-batch-ledger`

## 1. 分支基线

- 当前已切换到 `feat/p1-batch-ledger`。
- 本地与远端均未发现 `release/ai-4phase-2026q2`，因此当前功能分支实际从 `main` 当前工作树创建。
- 当前工作树保留原有未提交改动，主要集中在 `docs/`、`.idea/`、`.vscode/` 与 `app/release/`。

## 2. 现有单条 AI 回执处理链路

### 2.1 UI 入口

1. `MainActivity` 挂载 `ChatScreen`，并把 `chatViewModel.sendMessage` 作为发送入口传入。
2. `ChatScreen` 底部 `InputBar` 收集用户输入，调用 `onSendMessage(userText)`。
3. 首页 `HomeScreen` 的 AI 入口会切到 Chat Tab；聊天回执中的“修改”会跳到 `ManualEntryScreen`。

### 2.2 流式解析入口

1. `ChatViewModel.sendMessage()` 调用 `ChatRepository.sendMessage()`。
2. `ChatRepository.sendMessage()` 先写入一条用户消息，再拼装 `system + recent messages` 请求。
3. `SiliconFlowAiGateway.streamReply()` 逐行消费 SSE `data:` 流。
4. 网关只识别单个 `<DATA>...</DATA>` 段：
   - 普通文本发出 `AiStreamEvent.TextDelta`
   - `<DATA>` 完整闭合后发出单个 `AiStreamEvent.ReceiptParsed(AiReceiptDraft)`

### 2.3 单条执行入口

1. `ChatRepository.sendMessage()` 仅维护一个 `parsedReceipt: AiReceiptDraft?`。
2. 结束后通过 `extractReceiptDraftFromText()` 做一次单条兜底解析。
3. `normalizeReceiptAction()` 把动作归一到 `create/update`。
4. `applyReceiptTransaction()` 只处理一条草稿：
   - `tryCreateTransaction()` 插入一条 `TransactionEntity`
   - `tryUpdateTransaction()` 只定位并更新一条已有交易
5. 最终只得到一个 `AppliedTransaction?`，并把单个 `transactionId` 绑定到最后一条 assistant 消息。

### 2.4 UI 展示入口

1. `ChatRepository.observeChatRecords()` 通过 `chat_messages + transactions` 组合出 `AiChatRecord`。
2. `AiChatRecord` 仅承载单个 `transactionId`、`receiptRecordTimestamp`、`receiptType`。
3. `ChatScreen.toDemoMessage()` 只解析单个 `<RECEIPT>` 或 `<DATA>` payload。
4. `ReceiptCard` 只展示单条分类、金额、备注、日期、时间。
5. 删除操作会删掉对应单条 `chat_message`，并级联删除其单个 `transactionId` 对应交易。
6. 修改操作会把当前回执转成一份 `ManualEntryPrefill` 跳到手动记账页。

## 3. 需兼容路径

### 3.1 必改路径

1. Prompt 与网关层要兼容两种批量回执协议：
   - 多个 `<DATA>...</DATA>`
   - 单个 `<DATA>{"items":[...]}</DATA>`
2. 应用层要从单个 `AiReceiptDraft` 改为可顺序执行的草稿列表。
3. 执行结果要支持部分成功，失败项不能阻断其他条目。
4. 聊天结果 UI 要从单条成功卡片扩展为批量结果卡片，展示成功数、失败数、失败原因。

### 3.2 重点受限点

1. `AiStreamEvent.ReceiptParsed` 当前一次只承载一个 `AiReceiptDraft`。
2. `ChatRepository.sendMessage()` 当前只保存一个 `parsedReceipt`、一个 `appliedTransaction`、一个 `linkedTransactionId`。
3. `ChatMessageEntity.transactionId` 是单值，无法直接表达“一条回执对应多条交易”。
4. `AiChatRecord` 与 `ChatScreen` 的 payload 解析都是单条结构。
5. `ReceiptCard` 的编辑、删除交互也都假设只有一条交易。

### 3.3 已天然兼容路径

1. 首页、账本、搜索本质都直接读取 `transactions` 表。
2. 只要最终成功插入多条 `TransactionEntity`，这些页面会自动按现有列表逻辑展示多条记录。
3. `MainViewModel` 的筛选、统计、分组逻辑本身按交易列表工作，不依赖单条 AI 回执结构。

## 4. 关键文件

- `app/src/main/java/com/qcb/keepaccounts/MainActivity.kt`
- `app/src/main/java/com/qcb/keepaccounts/ui/viewmodel/ChatViewModel.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowAiGateway.kt`
- `app/src/main/java/com/qcb/keepaccounts/domain/contract/AiChatGateway.kt`
- `app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt`
- `app/src/main/java/com/qcb/keepaccounts/ui/model/AppUiModels.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/local/entity/ChatMessageEntity.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/local/entity/TransactionEntity.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/local/dao/ChatMessageDao.kt`
- `app/src/main/java/com/qcb/keepaccounts/data/local/dao/TransactionDao.kt`
- `app/src/main/java/com/qcb/keepaccounts/ui/viewmodel/MainViewModel.kt`
- `docs/development_plan_v1.2-v1.4.md`

## 5. 当前结论

- 如果“任务1”指本次要求的“建立分支基线 + 梳理现状链路”，则已完成。
- 如果“任务1”指 P1 开发计划中的完整批量记账改造与 T1 验证，则当前尚未完成，只完成了前置基线梳理。
