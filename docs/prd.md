# KeepAccounts: Android 端产品需求与技术架构文档 (PRD & Architecture)

## 1. 项目概述 (Overview)
KeepAccounts 是一款基于大语言模型 (LLM) 的创新型对话式记账 Android 应用。应用采用“治愈系/水彩薄荷绿 (Pastel Healing Style)”设计语言，通过拟人化的 AI 角色（如：Nanami🌊）与用户进行情感陪伴与互动，从而自动化完成烦杂的记账流程。本 PRD 专为 Android 客户端开发视角编写。

---

## 2. 系统架构设计 (System Architecture)
系统采用 **“Android 原生前端客户端 + Python 异步后端服务 + 大模型云端解析”** 的三层架构。

### 2.1 Android 前端技术栈 (Android Client)
为了实现极佳的 UI 渲染性能、圆润通透的玻璃态设计以及丝滑的动画体验，推荐以下原生开发栈：
*   **开发语言**：Kotlin
*   **UI 框架**：**Jetpack Compose**。Compose 的声明式 UI 非常适合快速构建高定制化的动态组件（如：动态聊天流、多段高亮进度条、毛玻璃组件）。
*   **异步与状态管理**：Kotlin Coroutines (协程) + Flow，结合 ViewModel 配合 `StateFlow` 进行单向数据流 (UDF) 管理。
*   **网络请求**：Retrofit + OkHttp。使用 OkHttp 的 SSE (Server-Sent Events) 功能接收流式 AI 响应。
*   **图表库**：Vico (针对 Compose 的轻量图表库) 或 YCharts，用于实现账本页的圆环图与趋势图。

### 2.2 后端服务技术栈 (Backend Service)
*   **开发语言与框架**：Python 3.10+ & **FastAPI**
*   **特性**：全面使用 `async/await` 处理高并发的 API 请求，特别是对接大模型 API 时的异步等待。
*   **AI 调度层**：LangChain 或 直接使用原生 OpenAI SDK (支持 DeepSeek/通义千问等类 OpenAI 接口模型)，用于 Prompt 组装与 JSON Schema Schema 校验锁定。

---

## 3. 数据流与存储设计 (Data Storage)

为了保证“离线可用”及“极速的页面加载体验”，Android 端采用 **Local-First (本地优先)** 的存储策略。

### 3.1 数据库结构
*   **Android 本地存储**：使用 **Room Database**（Android 官方推荐的 SQLite ORM 框架）。所有用户核心数据全部本地化存储，保护隐私，无须强制联网登录即可使用记账功能。
    *   `TransactionEntity` (账单明细表)
    *   `ChatEntity` (聊天记录表，缓存 AI 与用户的对话气泡)
    *   `BudgetEntity` (预算设置表)
*   **云端服务端存储**：**无**。取消全局账单的云端数据库，后端仅作为无状态 (Stateless) 的 AI 代理层转发模型请求，确保数据隐私。
*   **数据迁移与清理**：依靠 Android 本地文件读写与 SAF (Storage Access Framework) 实现数据的 JSON/CSV 格式**导出与导入**功能，方便用户更换手机时进行手动数据迁移；并提供**清除缓存**选项释放非核心资源。

### 3.2 静态资源策略 (Static Resources)
绝不要在列表和聊天流中通过网络加载高频图片，以防卡顿。响应时间优先。
*   **治愈系背景与头像**：将基础的高清“水彩薄荷绿”背景、常用 emoji 切图、分类 Icon（☕餐饮、🍃日常）以 **WebP 格式** 直接打包进 Android 的 `res/drawable` 资源包中。
*   **加载与交互动画**：使用 **Lottie (lottie-compose)**，将 AI 的“思考中”、“记账成功飘落的纸屑”等设计为矢量 JSON 动画放入本地 `assets`，几 KB 即可实现极高质量的动态效果。

---

## 4. AI 响应时间优化与体验设计 (AI Latency Optimization)
大模型 API 的请求通常需要 1~3 秒不等的生成时间，若处理不当会导致用户界面假死。本应用采用以下策略：

1.  **乐观 UI 更新 (Optimistic UI)**：
    *   用户发送语音/文字后，气泡立刻上屏。
    *   底部迅速弹出本地生成的 Lottie 动画：“Nanami 正在翻开账本...” (占位符气泡，显示 typing 状态)。
2.  **流式输出 (Streaming Response)**：
    *   后端 FastAPI 端点使用 `StreamingResponse`，大模型吐出一个字，Android 端就往屏幕上的对话气泡里填一个字。提升“陪伴感”。
3.  **异步后台保存**：
    *   大模型在后台完成 JSON 结构化提取后，先静默写入本地 Room 数据库。
    *   写入一旦成功，通过 EventBus/Flow 通知全局 UI 更新（日历上的数字跳动，首页首页预算进度条缩减），而无需阻塞当前聊天。

---

## 5. 手机权限调用与硬件交互 (Android Permissions & Hardware)

### 5.1 必备系统权限 (`AndroidManifest.xml`)
*   **网络权限**：
    *   `<uses-permission android:name="android.permission.INTERNET" />` （基础请求）
*   **录音与语音输入** (核心交互)：
    *   `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
    *   *说明*：用于首页和聊天页的“按住说话”或“晃一晃说话”。需采用动态运行时权限请求。
*   **设备震动** (交互情绪反馈)：
    *   `<uses-permission android:name="android.permission.VIBRATE" />`
    *   *说明*：当长按账单快速复用、拖拽分类、以及收到 AI 的“记账成功自定义卡片”时，调用手机马达提供清晰的 Haptic Feedback。
*   **通知权限** (超支提醒/后台记账成功)：
    *   `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` (Android 13+)
*   **加速度传感器 (传感器不需要显式声明权限，但需注册监听)**：
    *   *说明*：调用 `SensorManager`，用于实现“晃一晃手机直接唤醒语音面板”的功能。

---

## 6. 核心业务流程 (Core Application Flow)
1. **输入触发**：用户在聊天页打字，或晃动手机触发 Android `SpeechRecognizer` 转录成文字。
2. **状态装载**：Android `ChatViewModel` 新增临时气泡，使用 Lottie 展示等待。
3. **网络传输**：通过 Retrofit 发送纯文本给后端 `/api/chat`。
4. **后端发力**：FastAPI 并发调用 LLM：一是流式返回拟人化情感文本，二是要求模型遵循 JSON Schema 严格返回结构化账单 `{amount: xx, category: xx}`。
5. **端侧渲染**：
    *   Android 端实时接收 SSE 流，打出情感回复。
    *   收到完整的 JSON 块后，立刻将其序列化存入 Room DB，并渲染为特殊的 **“记账回执 UI 卡片”**。
6. **状态分发**：数据库刷新触发 `Flow` 变更，整个 Android APP 的预算进度条、日历红绿数字同步刷新。