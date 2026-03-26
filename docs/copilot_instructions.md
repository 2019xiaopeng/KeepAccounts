# 🤖 Copilot 协作规则与开发流说明 (Agent Instructions)

这份文档是你（AI Agent / GitHub Copilot）在这个 KeepAccounts Android 仓库中的最高优先级行为准则与项目上下文。开始任何编码工作之前，请先阅读本指南和 `TRD.md`。

## 1. 核心系统理念 (Core Philosophy)
*   **平台**：纯血 Android 客户端开发，不允许带入前端网页思维。
*   **语言与 UI 框架**：绝对禁止使用 XML 布局！全盘使用 **Kotlin + Jetpack Compose**。
*   **架构风格**：严格遵循 **MVI + Kotlin Coroutines + Flow (StateFlow / SharedFlow)** 的现代 Android 设计。绝对不保存任何全局云端状态。
*   **视觉语言约定**：“治愈系薄荷绿 (Pastel Healing)”。在写 Compose `Modifier` 时，必须偏好：极大圆角（如 `RoundedCornerShape(24.dp)`）、高度毛玻璃质感与微光阴影，不使用纯黑。具体请参考同目录下的 `KeepAccounts_UI_Design.md` 和 `.temp/` 文件夹中的大体结构。

## 2. 你的阶段性开发工作流 (Development Workflow)
当用户下达新的宏大指令引发开发迭代时，请你分步骤、克制地执行，并在**每一个阶段完成后请求用户验证**：

*   **Phase 1: 数据层 (Local First DB)**
    *   行动：依靠 `TRD.md` 中的设计，优先编写 Room Database 相关的 Entity、DAO 和 Repository。
    *   验证点：提供简单的 ViewModel 插入，并在 Logcat 确认数据库存储无误。
*   **Phase 2: 状态管理层 (ViewModel & StateFlow)**
    *   行动：建立 MVI Intent，确保所有 UI 修改均单向受控。
    *   验证点：观察日志确保流能发出。
*   **Phase 3: Compose UI 单页开发**
    *   行动：使用 Compose 构建独立的 Screen。利用 Dummy 假数据进行 UI 视觉验证。
    *   验证点：用户在 Android Studio 的 `@Preview` 里查看效果或者构建到真机检查样式。
*   **Phase 4: API 与交互串联 (The AI Layer)**
    *   行动：利用 Retrofit + OkHttp SSE 响应硅基流动/Kimi 接口的打字机流，同时在收到特殊标签 `<DATA>` 阻断上屏，进行 JSON 结构体拦截以存入 Room DB。

## 3. 代码规范要求
1.  **绝不生成巨型代码块**：请基于“组件化”思想拆分 Composable 树。不要在一个函数里写超过 200 行 UI 代码，把 Header, Button 等全部单独抽成 `@Composable` 级组件。
2.  **绝对禁止“脑补”**：如果不知道某个接口应该传什么，或者没有读到第三方库怎么引入，请用终端或者明确反问用户。
3.  **不引入多余资源**：首选使用 Compose 自带的 `Icons.Rounded` 作为基础图标进行着色处理。

## 4. 你的默认工具链说明
*   网络层：`Retrofit2`, `OkHttp` 和专门用来收流的 `okhttp-sse`。
*   数据层：`Room`。
*   依赖注入：`Hilt` (推荐) 或简单的单例 Factory 依赖注入。
*   导航：`androidx.navigation:navigation-compose`。

*当你阅读完这段后，如果没有任何特殊开发请求，请以简短、热情的语言告知用户：“我已经准备好了，请问要从 Room 实体类的建立开始，还是建立底部的 Compose 导航栏呢？”*