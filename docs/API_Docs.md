# API 接口文档（KeepAccounts 当前实现）

本项目是纯本地 Android 应用。除 AI 对话能力外，不依赖业务后端。

- 本地存储：Room + DataStore
- 外部网络：仅 SiliconFlow `/chat/completions`

---

## 1. 外部接口：SiliconFlow 流式对话

### 1.1 基础信息

- Base URL: `https://api.siliconflow.cn/v1`
- Endpoint: `POST /chat/completions`
- 客户端接口：`SiliconFlowApi.streamChatCompletions(@Body request)`
- 传输方式：`@Streaming`（逐行读取 `data:` 事件）

### 1.2 鉴权与请求头

- `Authorization: Bearer YOUR_SILICONFLOW_API_KEY`
- `Content-Type: application/json`

### 1.3 请求体（简化示例）

```json
{
  "model": "deepseek-ai/DeepSeek-V3",
  "messages": [
    {
      "role": "system",
      "content": "你是记账管家... 当识别到记账信息时，必须在末尾输出 <DATA> JSON"
    },
    {
      "role": "user",
      "content": "昨天晚上打车花了30"
    }
  ],
  "temperature": 0.3,
  "stream": true
}
```

### 1.4 流式响应解析约定

网关逐行处理 `data:` 事件：

1. 提取 `choices[0].delta.content`。
2. 普通文本片段输出为 `AiStreamEvent.TextDelta`。
3. 遇到 `<DATA>...</DATA>` 时缓存并解析为 `AiReceiptDraft`，输出 `AiStreamEvent.ReceiptParsed`。
4. 过滤隐藏片段（如 `<note>...</note>`、`<think>...</think>`），不进入 UI。
5. 收到 `[DONE]` 后输出 `AiStreamEvent.Completed`。

---

## 2. `<DATA>` 回执契约

### 2.1 当前使用的数据结构

```json
{
  "isReceipt": true,
  "action": "create",
  "amount": 30.0,
  "category": "交通",
  "desc": "打车",
  "recordTime": "2026-03-29 19:00",
  "date": "2026-03-29"
}
```

### 2.2 字段说明

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `isReceipt` | Boolean | 是否为记账回执 |
| `action` | String | 当前主要使用 `create` |
| `amount` | Double? | 金额 |
| `category` | String? | 分类名 |
| `desc` | String? | 备注 |
| `recordTime` | String? | 记录时间（`yyyy-MM-dd HH:mm`） |
| `date` | String? | 日期（兼容字段） |

### 2.3 客户端时间落库优先级

`ChatRepository.resolveRecordTimestamp` 使用以下优先级：

1. 用户原始输入中的显式日期/时间
2. `recordTime`
3. `date`
4. 当前系统时间

补充：支持相对日期与模糊时段默认映射（如早上 08:00、中午 12:00、晚上 19:00）。

---

## 3. 本地内部接口（DAO）

### 3.1 `TransactionDao`

- `insertTransaction(transaction): Long`
- `insertTransactions(transactions)`
- `countTransactions(): Int`
- `observeAllTransactions(): Flow<List<TransactionEntity>>`
- `observeTransactionById(id): Flow<TransactionEntity?>`
- `deleteTransactionById(id)`

### 3.2 `ChatMessageDao`

- `insertMessage(message): Long`
- `observeAllMessages(): Flow<List<ChatMessageEntity>>`
- `getRecentMessages(limit): List<ChatMessageEntity>`
- `getMessageById(id): ChatMessageEntity?`
- `updateMessage(id, content, isReceipt, transactionId)`
- `deleteMessagesByIds(ids)`
- `deleteMessageById(id)`
- `clearMessages()`

---

## 4. 错误与提示语策略

网关已对常见网络错误做用户可读映射：

- `401`: API Key 或 Base URL 配置异常
- `403`: 权限不足
- `429`: 请求频率过高
- `5xx`: 服务端异常
- 网络不可用/超时：本地提示重试

---

## 5. 边界说明

当前版本尚未提供以下“文件 API”能力：

- CSV/JSON 真实导出
- CSV/JSON 真实导入

设置页相关入口目前仅为交互占位，不涉及实际文件读写。