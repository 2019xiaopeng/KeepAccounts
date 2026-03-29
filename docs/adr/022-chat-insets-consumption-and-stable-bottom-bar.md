# ADR 022: 聊天页 Insets 消费链路与稳定底栏策略

- Status: Accepted
- Date: 2026-03-29

## Context
聊天页出现两个关键体验问题：
1. 软键盘弹起/收起时存在双重回弹，列表与输入栏出现位移断层。
2. 底部导航栏通过显隐改变布局高度时，会触发上方内容“坠落/回弹”。

问题根因是 Insets 处理职责分散：Scaffold padding、底栏占位、聊天页 imePadding 之间存在叠加冲突。

## Decision
1. 聊天页核心 Column 采用固定顺序链路：
   - `fillMaxSize()`
   - `padding(paddingValues)`
   - `consumeWindowInsets(paddingValues)`
   - `imePadding()`

   其中 `paddingValues` 来自父级 Scaffold。
2. 完全移除底栏 `AnimatedVisibility` 显隐策略，不再通过改变底栏布局高度来实现键盘联动。
3. 父级 Scaffold 使用 `contentWindowInsets = WindowInsets.systemBars`（不含 IME），让键盘出现时底栏被覆盖而非参与高度回弹计算。
4. 将 Scaffold `innerPadding` 显式传入主 Tab 与各子页面，避免 NavHost 全局 padding 与页面内 Insets 再次叠加。

## Why Not
- 为什么不继续用 AnimatedVisibility 隐藏底栏：
  - 会改变 bottomBar 的物理占位高度，导致内容区域边界瞬时变化并触发回弹。
- 为什么不把 imePadding 放在更外层 Scaffold：
  - 容易导致整页跟随键盘上跳，超出聊天区域控制边界。

## Consequences
- 正向：
  - 键盘动画期间输入栏与消息列表位移一致，不再出现双重回弹。
  - 底栏不再生硬挤压内容，视觉连续性更稳定。
  - Insets 责任边界清晰，便于后续维护。
- 代价：
  - 路由层需要显式传递 `innerPadding`，代码路径略增加。

## Validation
- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 通过。

## Next
1. 若需要底栏“视觉淡出”效果，可采用不改变布局高度的 alpha 或 translation 动画。
2. 可补充 UI 自动化用例覆盖键盘弹起/收起过程，防止回归。
