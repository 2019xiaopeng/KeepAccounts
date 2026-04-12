# P3-S6: 记账对话体验升级开发规范 (Chat Experience Enhancements)

- 日期：2026-04-12
- 状态：Completed
- 适用分支：`feat/p3-agent-tools`

## 目标

为当前记账 AI Agent 提供国民级 IM 体验，落地两项核心能力：

1. 长按多选删除聊天气泡（不删账单）。
2. 微信风格分页时间线（Paging 3 + 时间分割线）。

## 核心安全原则（已落实）

1. 删除聊天记录（单条删除、多选删除、清空）仅操作 `chat_messages`。
2. 不对 `transactions` 做联动删除，真实账单流水保持完整。
3. Paging 3 用于聊天列表分页加载，降低列表渲染与上下文压力。

## 实施清单完成情况

- [x] **Phase 1: 基础设施**
  - [x] `ChatMessageDao` 已保留并复用 `deleteMessagesByIds`、`clearMessages`。
  - [x] DAO 新增 `getPagedMessages(): PagingSource<Int, ChatMessageEntity>`。
  - [x] 依赖新增：`androidx.paging:paging-runtime-ktx`、`androidx.paging:paging-compose`、`androidx.room:room-paging`。
  - [x] `SemanticDateTime.kt` 已实现 `formatWeChatStyleTime()` 并补齐单测。

- [x] **Phase 2: 状态与 ViewModel**
  - [x] `ChatViewModel` 暴露 `pagedMessages`（Paging 3 Flow）。
  - [x] `ChatViewModel` 暴露 `deleteSelectedMessages(ids: Set<Long>)`。
  - [x] `ChatViewModel` 暴露 `clearChat()`。

- [x] **Phase 3: 视图层（聊天列表与时间线）**
  - [x] `ChatScreen` 改为 `collectAsLazyPagingItems()` 渲染分页列表。
  - [x] 在气泡前按 >5 分钟间隔插入 `TimeDividerComponent`。
  - [x] 时间文案使用 `formatWeChatStyleTime()`（当天/昨天/周内/同年/跨年）。

- [x] **Phase 4: 视图层（多选交互）**
  - [x] 页面引入 `isSelectionMode`、`selectedMessageIds`。
  - [x] `ChatHeader` 支持多选态（取消、已选择 N 条、隐藏右侧操作）。
  - [x] `InputBar` 与 `DeleteSelectionBar` 使用 `AnimatedVisibility` 切换。
  - [x] `MessageRow` 注入长按与点选手势，支持左侧勾选图标与位移反馈。
  - [x] 多选态禁用气泡文本 `SelectionContainer`，避免长按冲突。

- [x] **Phase 5: 安全验证**
  - [x] 新增单测验证：多选删除/清空聊天仅影响 `chat_messages`，`transactions` 保持不变。

## 关键改动文件

1. `app/src/main/java/com/qcb/keepaccounts/data/local/dao/ChatMessageDao.kt`
2. `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
3. `app/src/main/java/com/qcb/keepaccounts/ui/viewmodel/ChatViewModel.kt`
4. `app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt`
5. `app/src/main/java/com/qcb/keepaccounts/ui/format/SemanticDateTime.kt`
6. `app/src/main/java/com/qcb/keepaccounts/MainActivity.kt`
7. `app/build.gradle.kts`
8. `app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticDateTimeTest.kt`
9. `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryTimeSemanticsTest.kt`
10. `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt`

## 验证记录

执行命令与结果：

1. `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.qcb.keepaccounts.ui.format.SemanticDateTimeTest" --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryTimeSemanticsTest" --tests "com.qcb.keepaccounts.data.repository.TieredAiChatGatewayTest" --tests "com.qcb.keepaccounts.data.repository.TieredPlannerRouterTest"`
   - 结果：成功。
2. `./gradlew :app:testDebugUnitTest`
   - 结果：成功。

## 说明

当前版本中，聊天记录删除行为已严格与账单流水解耦。后续若新增“删除账单”能力，应独立走账本域操作入口，不复用聊天记录删除链路。
