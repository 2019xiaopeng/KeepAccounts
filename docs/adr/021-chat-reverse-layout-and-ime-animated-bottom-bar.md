# ADR 021: 聊天页反向列表与 IME 联动底栏动画策略

- Status: Accepted
- Date: 2026-03-29

## Context
聊天页在键盘唤起时存在两类体验问题：
1. 最新消息在输入阶段可能被遮挡，用户需要手动滚动才能看到底部内容。
2. 底部导航栏在键盘弹起时存在生硬跳动，视觉割裂明显。

项目已启用 Edge-to-Edge，若不明确划分 Insets 责任，易出现列表、输入栏、底栏之间的位移冲突。

## Decision
1. 消息列表改为反向布局：
   - `LazyColumn(reverseLayout = true)`。
   - 数据源使用反转后的消息列表（`asReversed()`），保证最新消息出现在视觉底部。
   - 移除原先“新增消息后强制滚动到底部”的 LaunchedEffect。
2. 底部导航栏改为 IME 感知动画：
   - 使用 `WindowInsets.isImeVisible` 判断键盘状态。
   - `AnimatedVisibility` + `slideInVertically/slideOutVertically` 对底部导航栏做平滑显隐。
   - 键盘弹起时隐藏底栏，收起时恢复，避免被键盘顶到中间。
3. IME Padding 锁定策略：
   - `imePadding()` 仅放在聊天页消息列表+输入框的直接父容器（Column）。
   - 不在外层 Scaffold 增加 imePadding，避免整页一起上跳。

## Why Not
- 为什么不继续使用普通布局 + 手动 scrollToItem：
  - 手动滚动与流式插入并发时容易抖动，复杂度高且边界条件多。
- 为什么不让底栏常驻：
  - 键盘态下常驻底栏会与 IME 争抢底部空间，造成生硬位移。

## Consequences
- 正向：
  - 新消息天然贴底展示，键盘弹起时对话仍保持可见。
  - 底栏过渡顺滑，整体观感更接近 IM 产品。
  - Insets 处理边界明确，可维护性提升。
- 代价：
  - 反向布局后，列表插入顺序和视觉顺序不一致，需要团队保持统一认知。

## Validation
- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 通过。

## Next
1. 可在用户上滑查看历史时加入“新消息回到底部”悬浮按钮。
2. 可为底栏显隐加入轻量透明度动画，进一步降低感知突兀。
