# ADR 020: 聊天页 IME Insets 与底部导航栏联动策略

- Status: Accepted
- Date: 2026-03-29

## Context
聊天页在 Edge-to-Edge 场景下出现典型的键盘悬空问题：
1. 键盘弹起后，输入栏与键盘顶部之间存在明显空白区。
2. 输入栏与底部主导航栏在非键盘态距离偏大，视觉上“悬空”。
3. 现象本质是底部导航占位与 IME Insets 在同一路径中发生叠加或冲突。

## Decision
1. 主导航联动策略：
   - 在主 Tab 的聊天页中，若 IME 可见，则临时隐藏底部导航栏。
   - 目的：避免 Scaffold 的底部栏占位与 IME 顶起同时生效。
2. 聊天页 Insets 策略：
   - 聊天页消息列表与输入栏外层 Column 统一应用 imePadding。
   - 输入栏自身不再叠加 imePadding/navigationBarsPadding，避免重复计算。
3. 输入栏位置微调：
   - 输入栏底部静态外边距收敛到小值（2dp 级别），更贴近底部视觉锚点。

## Why Not
- 为什么不在输入栏同时使用 navigationBarsPadding + imePadding：
  - 在当前 Scaffold + 主导航结构中，这会与底部栏占位链路叠加，产生过度抬升。
- 为什么不保持底部导航始终显示：
  - 键盘态下底部导航信息密度低且会干扰输入主流程，且易引入 inset 双算。

## Consequences
- 正向：
  - 键盘弹起时输入栏可贴合键盘顶部，消除中间大空白。
  - 非键盘态下输入栏更贴近底部，视觉更接近 IM 应用。
  - Insets 责任边界清晰，后续维护成本更低。
- 代价：
  - 聊天页键盘态会临时隐藏底部导航，交互路径从“切 Tab”切换为“先收起键盘再切 Tab”。

## Validation
- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 通过。

## Next
1. 如需更丝滑体验，可为底部导航显示/隐藏加上轻量过渡动画。
2. 如需跨页统一策略，可在后续抽象出全局 Insets Policy（按页面类型配置）。
