# KeepAccounts 核心技术设计文档 (TRD)

## 1. 架构原则与选型 (Architecture & Stack)

系统从最初的云端同步设想完全迁移为 **Local-First (纯本地优先)** 的原生客户端架构：
1.  **开发语言**: Kotlin (1.9+)
2.  **UI 框架**: Jetpack Compose (响应式声明 UI)，利用 `Navigation Compose` 提供全屏/下沉页面的顺滑交互。
3.  **状态与架构**: MVVM / MVI + Clean Architecture，采用 Kotlin Coroutines 异步编程与 `StateFlow` 实现 UDF (单向数据流)。
4.  **本地数据库**: Room Database 作为唯一真理来源。任何新产生的流水（手动或AI解析），保存入库后通过 `Flow<List<Transaction>>` 驱动全量UI刷新。
5.  **服务端/AI 交互**: 应用直接采用 Retrofit + OkHttp SSE 与 SiliconFlow 大模型端点进行会话，以获得类似 ChatGPT 的流式打字机回复，不架设自营服务器收集隐私。

---

## 2. 数据库设计 (Room Database Schema)
系统全面基于 SQLite。包含三个核心结构：

### 2.1 TransactionEntity (账单流水表)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | String (UUID)| 主键 |
| `type` | Int | 0=支出(Expense), 1=收入(Income) |
| `amount` | Double | 记录金额 |
| `categoryName` | String | 分类名称（如：餐饮、日用） |
| `categoryIcon` | String | 图标标识映射（对应于 Android 内部的 SVG Drawable 映射机制） |
| `remark` | String | 用户备注 |
| `recordTimestamp`| Long | 消费发生的实际时间戳（重要，日历图表依赖它处理对齐） |
| `createdTimestamp`| Long | 生成的时间戳 |

### 2.2 ChatEntity (聊天记录缓存)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | String (UUID)| 主键 |
| `role` | String | 'user' / 'assistant' / 'system' |
| `content` | String | 聊天文本内容 |
| `timestamp`| Long | 发送时间 |
| `isReceipt`| Boolean| 标示是否为“记账回执”卡片消息 |
| `receiptId`| String | 外键关联到 `TransactionEntity.id` 以便展现 |

### 2.3 Store (DataStore) 设定期
放弃传统的 SharedPreferences，采用 Jetpack DataStore。
维护参数如：`aiName` (AI 称呼)，`aiAvatarUri` (本地头像)，`aiTone` (性格预设)，`userName` (主人称谓)，`theme` (水彩薄荷绿/樱花粉红/天空湛蓝)。

---

## 3. 面向 Android 16 的核心 UI 交互方案映射

### 3.1 高级磨砂玻璃 (Glassmorphism)
基于 Next.js 版本的 `Tailwind css blur` 效果，向 Kotlin 迁移需要封装：
```kotlin
Modifier.graphicsLayer {
    // Android 12+ 高性能支持
    renderEffect = BlurEffect(radiusX = 50f, radiusY = 50f, edgeTreatment = TileMode.Decal)
}.background(Color.White.copy(alpha = 0.4f))
```
统一使用此修饰并搭配大圆角 ( `RoundedCornerShape(32.dp)` ) 构建所有卡片。

### 3.2 图标像素级迁移策略
严格废弃硬编码 Emoji (跨设备跨系统渲染不一致且不够精细)，将 Web 原型使用的高达几百个 `lucide-react` 线条 SVG 移植至 Android：
*   **格式**: VectorDrawable (XML) 
*   **统一粗细**: 控制 strokeWidth 统一恒定为 `2.5` 以提供统一饱满的治愈氛围。

### 3.3 图表面板重构
使用 `com.patrykandpatrick.vico:compose-m3`：
*   将历史 Web 版本的原柱状图迁移为更为平滑治愈的 **曲线图 (Line Chart)**。
*   X/Y 轴与颜色随月度/年度维度，以及明暗颜色主题 (Expense/Income/Balance) 实时切换组合。

---

## 4. 平台硬件与 Android 16 独占特性

### 4.1 语音引擎对接
调用 Android 原生 API 进行录音。
*   **Service**: 封装 `SpeechRecognizer`，提供长按/按两下/摇局手机启动 `startListening()` 和 `stopListening()`。
*   **输出流**: 使用 `Coroutines Flow` 进行 Partial Results 与 Final Result 回调并填入输入框。

### 4.2 Rich Ongoing Notifications (状态栏彩色胶囊 / 灵动岛)
利用 Android 16 前台服务特性 (Foreground Service)：
*   启动独立 Service (需 `FOREGROUND_SERVICE` 与 `FOREGROUND_SERVICE_MICROPHONE` 权限)。
*   在 `Notification.Builder` 中配合 `setOngoingStyle()`/类似最新胶囊流特性，渲染出：正在录音（橙色呼吸胶囊） -> 正在传输分析大模型（水彩薄荷绿旋转胶囊）的状态闭环，允许用户在锁屏亦能快速唤醒记账并获得强烈的多任务体验反馈。

---

## 5. 安全及架构注意事项
*   网络请求只与 SiliconFlow 进行单点交互。对大模型吐出的内容执行：`流拦截 -> <DATA>...</DATA> Json 解析 -> 过滤展示到屏幕 -> DB 落地` 的全流程隔离管线。
*   主题变量 (Color) 将不走硬代码，从顶层 `CompositionLocalProvider` 中注入 `LocalAppColors`，实现切换时不刷新 Activity 仅触发 Recomposition 以完成一键主题切换功能。