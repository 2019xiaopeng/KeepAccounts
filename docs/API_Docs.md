# KeepAccounts API 接口文档

因系统全面转换为 Local-First (本地优先) 架构，后端被简化为一个**无状态的转接层 (Stateless Proxy)**。
本核心代理层对接的是 **SiliconFlow** 平台提供的 `Pro/moonshotai/Kimi-K2.5` 模型。它的唯一职责是在服务器端应用标准的 OpenAI SDK 规范调用硅基流动的接口（隐藏真实 API Key），将客户端发来的多轮结构化数据组装为符合长文本/强指令约束逻辑的 Prompt 后，流式发回前端。

## 1. 基础配置
- **BasePath**: `https://api.yourdomain.com/v1`
- **认证**: 对于无状态网关，可以依赖基于 JWT 或简易设备 Token 的 Auth Header 防滥刷。

---

## 2. 接口列表

### 2.1 大模型聊天与账单提取 (Chat Streaming)
**接口路径**: `POST /chat`
**功能说明**: 接收用户的自然语言历史记录及当前账本侧边信息，使用 Server-Sent Events (SSE) 流式返回 AI 聊天的情感文本以及结构化的 JSON 单据。

**Request Header**:
```http
Content-Type: application/json
Authorization: Bearer <DeviceToken>
```

**Request Body**:
```json
{
  "system_context": {
    "current_time": "2026-03-26T12:00:00+08:00",
    "ai_persona": "gentle_sister", // 可选: tsundere, gentle_sister, accountant
    "monthly_budget_remaining": 1520.50
  },
  "history": [
    {"role": "user", "content": "今天中午吃了兰州拉面，花了 20 块钱"},
    {"role": "assistant", "content": "好的哟，已经记下了呢~ 注意吃点清淡的。"}
  ],
  "message": "下午又点了杯瑞幸，35元"
}
```

**Response (SSE Stream)**:
```http
Content-Type: text/event-stream
Cache-Control: no-cache

data: {"type": "text", "content": "下"}
data: {"type": "text", "content": "午"}
data: {"type": "text", "content": "喝"}
data: {"type": "text", "content": "冰"}
...
data: {"type": "text", "content": "咖"}
data: {"type": "text", "content": "啡"}
data: {"type": "text", "content": "哦"}
data: {"type": "data_start"}
data: {"type": "json_chunk", "content": "{\"amount\":"}
data: {"type": "json_chunk", "content": " 35.0, \"category\":"}
...
data: {"type": "json_chunk", "content": "\"type\": 0}"}
data: {"type": "data_end"}
data: {"type": "done"}
```

*说明*：前端 OkHttp 客户端解析这个 SSE 流，当收到 `type: text` 时直接塞进聊天气泡更新 UI；当收到 `type: json_chunk` 时，将内容静默拼装不展示，待收到 `data_end` 时解析 JSON。

---

### 2.2 心跳与可用性检测 (Health Check)
**接口路径**: `GET /health`
**功能说明**: 用于 Android 端唤醒或首屏检测后端 API 是否存活网络通畅。

**Response**:
```json
{
    "status": "ok",
    "timestamp": 1774497600
}
```