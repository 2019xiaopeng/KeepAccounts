# KeepAccounts 核心技术设计文档 (TRD)

## 1. 架构原则 (Architecture Principles)
1.  **纯本地优先 (Local-First)**：所有账单、配置、AI 角色设定均存储于手机本地 Room 数据库。彻底切断云端账单库，保障隐私，断网亦可查看和管理账单。
2.  **无状态 AI 网关 (Stateless AI Gateway)**：后端的 Python 仅仅作为一个代理层 (Proxy)。它不保存用户的任何聊天记录，仅负责“接收上文 -> 包装大模型 Prompt -> 流式返回结果”。
3.  **单向数据流 (Unidirectional Data Flow - UDF)**：Android 端采用 MVI 架构，ViewModel 通过 `StateFlow` 将数据推给 Compose 界面。

---

## 2. 数据库设计 (Room Database Schema)
核心表结构设计如下（Kotlin Entity）：

### 2.1 TransactionEntity (账单流水表)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long (PK, Auto) | 主键 |
| `type` | Int | 0=支出(Expense), 1=收入(Income) |
| `amount` | Double | 记录金额 |
| `categoryName` | String | 分类名称（如：餐饮） |
| `categoryIcon` | String | 分类图标（如：🍔） |
| `remark` | String | 用户备注 |
| `recordTimestamp`| Long | 消费发生的实际时间戳 |
| `createdTimestamp`| Long | 该条记录生成的时间戳 |

### 2.2 ChatMessageEntity (聊天气泡表)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long (PK, Auto) | 主键 |
| `role` | String | 'user' / 'assistant' / 'system' |
| `content` | String | 聊天文本内容 |
| `isReceipt` | Boolean| 是否为特殊的“记账回执卡片” |
| `transactionId`| Long (FK)| 关联的流水 ID（如果是回执卡片） |
| `timestamp` | Long | 发送时间 |

### 2.3 UserConfigEntity (用户与 AI 配置表)
Key-Value 形式存储或单独 DataStore，记录：`ai_name`, `ai_persona` (性格枚举), `theme_color`, `monthly_budget`。

---

## 3. 核心技术难点实现：AI 响应的“分轨解析流”
**大模型底座**：采用 **SiliconFlow API (硅基流动)** 提供的 **`Pro/moonshotai/Kimi-K2.5`** 模型。Kimi 系列模型具备极强的上下文记忆和指令遵循能力，非常适合处理复杂的性格 Prompt 设定及约束 JSON 输出格式。

**具体后端实现设计 (Python + FastAPI代理)**：
1. 后端使用标准的 `openai` Python SDK 进行调用，只需将 Base URL 指向 SiliconFlow：
   ```python
   from openai import AsyncOpenAI
   client = AsyncOpenAI(
       api_key="your_siliconflow_api_key",
       base_url="https://api.siliconflow.cn/v1"
   )
   response = await client.chat.completions.create(
       model="Pro/moonshotai/Kimi-K2.5",
       messages=[...],
       stream=True
   )
   ```
2. **“分轨”挑战**：大语言模型需要同时干两件事：① 用拟人化口吻聊天；② 准确输出 JSON 结构化账单以便存入数据库。
3. **解决方案 (特殊的 Prompt 格式约定)**：
    我们在 Kimi 的 `system` prompt 中强制规定：“必须先输出多段闲聊回复文本对用户进行情绪安抚，最后严格用 `<DATA>` 和 `</DATA>` 标签包裹结算 JSON 工具数据。” 例如：
    ```text
    这顿饭吃得好丰盛呀，不过要注意肠胃哦~ 帮你记下来了！
    <DATA>
    {"amount": 105.0, "category": "餐饮", "icon": "🍲", "remark": "午餐", "type": 0}
    </DATA>
    ```
4. **Android 端拦截器响应 (Retrofit + OkHttp SSE)**：
    *   不断接收流式回调 `data_chunk`。
    *   如果流尚未出现 `<DATA>` 标签，直接将字符提交给 `ChatViewModel` 的 `StateFlow`，屏幕上的聊天气泡实现“逐字打印”。
    *   一旦检测到 `<DATA>`，后续的流数据不再上屏，而是在后台存入一个局部变量 `jsonBuilder`。
    *   流彻底结束收到 `[DONE]` 时，解析 `jsonBuilder` 里的 JSON，调用 Room DAO `insertTransaction()` 存入本地数据库，随后触发账单界面的刷新，并在聊天流插入专属的 `isReceipt=true` 账单卡片。