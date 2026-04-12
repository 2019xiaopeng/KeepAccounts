# KeepAccounts 🌊

KeepAccounts 是一款基于 Android 以及大语言模型（LLM）构建的治愈系对话式记账应用。
摒弃传统记账 App 繁琐的输入表单，用户只需像和朋友聊天一样，发送文字或语音，AI 即可自动识别金额、日期、分类，并在本地悄悄为你建好账本。

## 📌 最新版本
- 当前正式版本：**v1.2.0**（Phase1-Phase3 收敛版）
- RC 基线标签：`v1.2.0-rc1`
- 版本说明：`docs/release_notes.md`

## 💡 核心特性 (Features)
- 💬 **对话即记账**：完全基于自然语言的记账体验，支持上下文连记改错。
- 🛡️ **极致隐私 (Local-First)**：没有云端账单库，不要求强制注册。所有的交易流水都严格保存在您的手机本地数据库中（Room Database）。
- 🎨 **治愈系 UI (Jetpack Compose)**：基于全盘水彩薄荷绿与超大圆角的交互设计语言。告别传统财务软件的红绿数字焦虑。
- 🎭 **百变 AI 人设**：支持一键切换 AI 管家性格，提供满级的情绪陪伴价值。
- 📊 **无感报表**：提供日历流水叠加圆环图进度排行，让消费一目了然。

## 🛠️ 技术栈 (Tech Stack)

### Android 客户端
*   **语言**: Kotlin
*   **UI层**: Jetpack Compose (100% 声明式 UI)
*   **架构**: MVVM (Model-View-ViewModel) 配合 Kotlin Flow
*   **本地存储**: Room Database
*   **网络收发**: Retrofit2 + OkHttp (全面支持 SSE 流式请求，实现打字机效果)

### 后端代理 (Stateless AI Gateway)
*   **语言框架**: Python 3.10 + FastAPI
*   **AI 底座模型**: 采用了 **SiliconFlow (硅基流动)** 提供的 **`deepseek-ai/DeepSeek-V3`** 大模型来进行高等级的情感陪伴与结构化数据拆取。
*   **核心职责**: 包装并代理 OpenAI 规范的 API 调用，隐匿真实的 API Keys，拼装带情境说明的庞大 Prompt 与约束，并通过 Server-Sent Events (SSE) 流式推回给安卓前端，**绝不保存任何用户数据**。

## 📂 项目模块规划
*   `/app` - Android App 源码目录
*   `/backend` - (待建) Python FastAPI 轻量级网关目录
*   `/docs` - 相关的设计和产品原型文档 (PRD, TRD, 视觉原型说明)

## 🚀 立即开始 (Getting Started)

1.  请确保已安装 **Android Studio (Ladybug / Koala 或最新版本)**，配置了正确的 SDK (Recommended SDK 35+ / Minimum SDK 24)。
2.  克隆本仓库并在 Android Studio 中打开根目录。
3.  等待 Gradle Sync 图标变绿。
4.  使用实体 Android 手机开启开发者模式连接，或启动 AVD 模拟器。
5.  点击 Run `app`。

## 📦 正式打包 APK（Release）

你现在可以直接在 Android Studio 打开当前 KeepAccounts 根目录进行正式打包。

### 方式 A：命令行构建

```bash
./gradlew :app:assembleRelease
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleRelease
```

默认产物路径：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

### 方式 B：Android Studio 图形化签名打包（推荐发布）

1. 菜单选择：`Build > Generate Signed App Bundle / APK`。
2. 选择 `APK`。
3. 选择或新建 keystore，填写 alias 与密码。
4. Build Variant 选择 `release`。
5. 完成后在 `app/build/outputs/apk/release/` 获取签名 APK。

说明：当前 Gradle release 构建已可成功输出 APK；若用于外部分发，建议使用签名流程生成正式包。

---
*本项目完全遵循透明无广告、断网可用的初心，感谢您的使用与支持。*