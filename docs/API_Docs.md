# API 接口文档 - AI 智能记账管家 (Android 16 版)

本项目为纯本地 Android 应用，核心数据存储在本地 SQLite (Room) 中，**绝对不上云**。目前本应用唯一的且必须的外部网络请求，正是用于对话理解与解析单据的核心大脑——调用 **SiliconFlow API**。

除了访问该外网接口，其余业务（增删改查、读取列表、报表统计等）均定义为内部接口 (Room DAO)。

---

## 1. 外部大模型 API：SiliconFlow 聊天与解析流
系统在与用户进行情感对话或接收记账指令（如：“今天打车花了30”）时，向该接口发请求并要求同时返回自然语言回复和格式化的 JSON 单据。

### 1.1 接口基础
- **Base URL**: `https://api.siliconflow.cn/v1`
- **Endpoint**: `/chat/completions`
- **Method**: `POST`
- **鉴权 Header**: 
  - `Authorization: Bearer YOUR_SILICONFLOW_API_KEY` (由用户在应用本地填写的 Key 或编译注入配置)
  - `Content-Type: application/json`

### 1.2 请求载荷 (Request Body)
此处展示了如何巧妙绑定 System Prompt 将记账数据强制锁定并结构化的方案。

```json
{
  "model": "Pro/moonshotai/Kimi-K2.5", // 推荐遵循指令强的大模型
  "messages": [
    {
      "role": "system",
      "content": "你是一个名为 {aiName} 的 AI 记账管家，性格是 {aiTone}。用户的名字是 {userName}。你的任务是陪用户聊天，并在用户提到消费或收入时，提取记账信息。\n如果用户的话包含记账信息，你必须在回复的最末尾，严格使用 <DATA> 和 </DATA> 包括出对应的 JSON 串。例如：\n<DATA>\n{\n  \"isReceipt\": true,\n  \"action\": \"create\", // 可选: create, update, delete\n  \"amount\": 30.0,\n  \"category\": \"交通\",\n  \"desc\": \"打车\",\n  \"date\": \"2026-03-27\"\n}\n</DATA>\n如果仅是普通闲聊寒暄，正常回复陪伴语言即可，绝对不要输出 <DATA> 标签段。"
    },
    {
      "role": "user",
      "content": "今天中午吃黄焖鸡花了 25 块钱"
    }
  ],
  "temperature": 0.3, // 保持低温度约束结构稳定性
  "stream": true      // 使用 SSE 机制获得打字机陪伴感
}
```

### 1.3 客户端拆解逻辑 (Client Stream Parsing)
Android 端使用 `OkHttp SSE` 流式接受大语言模型的返回字粒（Char chunks）。
在 ViewModel 数据流中进行特殊判断：
- 一直输出字直到遇到特殊拦截字符串 `<DATA>`。
- 探测到 `<DATA>` 后截断不向 Compose UI Emit 流发送文字。
- 将随后的 JSON chunk 储存在缓存中，直到接收到 `</DATA>`。
- 执行本地 JSON 反序列化解析，通过后台协程写入 Room 数据库，完成记录创建操作。

---

## 2. 内部数据边界 (Room DAO Interfaces)
为了架构清晰，客户端对于各页面的驱动严格依靠向 DAO 获取响应式资源流（Flow）。

### 2.1 账单管理边界 `TransactionDao`
- **单增 `insertTransaction`**: 将上文 AI 解析出来的账单实体或用户手动填写的实体写入。
- **单改 `updateTransaction`**: 点击列表单条记录拉起面板页修改实体后执行，或 AI 指令执行。
- **单删 `deleteTransaction`**: 在左滑悬浮按钮处点红色的删除触发。
- **流视图 `getTransactionsByMonth(year: Int, month: Int)`**: 返回一个观察型的 `Flow<List<Transaction>>`。首页视图和 Ledger 的年度/月度统计均收集 (collect) 此流。底层记录任何改变立即自动驱使所有报表 UI 重绘。

### 2.2 聊天气泡库管理 `ChatDao`
- **保存 `insertChatMessage`**: 收包完成后，分别保存含 JSON 与不含 JSON 的对话，供退出软件时存留记录。
- **流图 `getChatHistory`**: `Flow<List<ChatMessage>>`，挂载至 ChatScreen 展现长长的历史海洋。

### 2.3 终端配置访问 `UserPreferencesRepository`
负责 DataStore 的访问。由 ProfileScreen 等设置界面调起：
- 获取配置： `val preferencesFlow: Flow<UserPreferences>` (包括 aiName, aiTone, avatar路径, themeMode等)
- 推送原子变更： `suspend fun updateTheme(themeStr: String)` 等单向写操作，一旦写入触发 Compose 主题系统重算呈现全局秒换肤。