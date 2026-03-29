# ADR 024: 非聊天页底部补偿恢复与设置子页状态栏安全滚动

- Status: Accepted
- Date: 2026-03-29

## Context
发布前回归发现两类问题：
1. 非聊天页（首页、账本、我的）出现内容穿透到底部导航栏下方。
2. 设置相关子页面上滑时内容与系统状态栏重叠，顶部安全区表现不稳定。

根因是此前为聊天页键盘动画做的 Insets 收敛导致“普通页面补偿链路”被弱化。

## Decision
1. 主 Tab 普通页恢复底部补偿：
   - 在 MainTabsPager 中为 Home/Ledger/Profile 传入 `Modifier.padding(bottom = scaffoldPadding.calculateBottomPadding())`。
   - 聊天页保持独立链路（consumeWindowInsets + imePadding）不变。
2. 设置类子页面统一状态栏安全滚动：
   - 在这些页面的根 `LazyColumn` 上增加 `statusBarsPadding()`。
   - 移除原先仅加在首行 Header 的 `statusBarsPadding()`，避免滚动过程中的局部重叠与重复补偿。
3. 流式显示节奏微调：
   - 将 AI 分气泡出现间隔从 520ms 调整为 680ms，使阅读节奏更贴近真人对话。

## Why Not
- 为什么不把聊天页也改成普通页补偿方式：
  - 聊天页需要键盘驱动型布局，普通页补偿会破坏既有键盘贴合策略。
- 为什么不继续仅在 Header 上做 statusBarsPadding：
  - 列表滚动时 Header 离开顶端后，后续内容仍可能进入状态栏区域。

## Consequences
- 正向：
  - 普通页底部内容不再被底栏遮挡。
  - 设置子页上滑时保持状态栏安全区，不再重叠。
  - AI 气泡节奏更自然，阅读负担降低。
- 代价：
  - 各页面 Insets 策略更细分，维护时需要区分聊天页与普通页。

## Validation
- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 通过。

## Next
1. 可补充 UI 回归清单：主 Tab 底部穿透、设置页上滑、聊天键盘弹起三项联测。
2. 若后续调整底栏高度，需要同步检查 `calculateBottomPadding()` 的实际补偿效果。
