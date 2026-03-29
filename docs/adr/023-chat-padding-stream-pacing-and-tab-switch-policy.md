# ADR 023: 聊天页补偿链路收敛、流式节奏优化与 Tab 切换策略

- Status: Accepted
- Date: 2026-03-29

## Context
本轮反馈集中在三个体验问题：
1. 聊天页之外的页面顶部出现额外空白，表现为非状态栏安全区的多余留白。
2. AI 流式输出节奏过快，分句气泡出现间隔不自然；并且中间会闪过 `<note>` 等不应暴露给用户的标签。
3. 主页面切换交互不符合预期：左右滑动应保留动画，但点击底部 Tab 时应直接切换，不走滑动过渡。

## Decision
1. 顶部空白收敛：
   - 移除路由层对多个页面统一附加的 `Modifier.padding(innerPadding)`，避免与页面内部 `statusBarsPadding()` 叠加。
   - 主 Tab 页仅将 Scaffold 的底部 padding 传给聊天页（`PaddingValues(bottom = ...)`），防止顶部重复补偿。
2. 流式显示优化：
   - 在 Repository 层对新增语气泡引入节奏控制（固定最小间隔），使同一轮回复的多气泡呈现更接近真人节奏。
   - 在网关流式解析阶段忽略 `<note>...</note>` 与 `<think>...</think>` 段落，避免中间推理/注释标签闪屏。
   - 在持久化与展示侧继续兜底清洗 `NOTE/THINK` 标签，确保最终 UI 仅显示用户可读文本。
3. Tab 切换策略：
   - 保留 `HorizontalPager` 手势滑动动画。
   - 底部 Tab 点击改为 `scrollToPage` 直接切换，不使用 `animateScrollToPage`。

## Why Not
- 为什么不继续给所有页面统一加 `innerPadding`：
  - 多数页面已自行处理状态栏安全区，再叠加会产生额外顶部空白。
- 为什么不把 `<note>` 仅在 UI 端过滤：
  - 若在上游流中不抑制，仍可能在流式瞬时状态中闪过；应优先在网关解析层拦截。

## Consequences
- 正向：
  - 非聊天页顶部空白消失，页面结构恢复稳定。
  - AI 多条气泡出现更自然，且不再出现 `<note>` 闪烁。
  - 底部按钮切页响应更直接，手势滑动动画仍保留。
- 代价：
  - 流式分气泡会被节奏策略轻微延后（有意设计）。

## Validation
- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 通过。

## Next
1. 若后续需要可配置“回复节奏快慢”，可将气泡间隔开放到设置项。
2. 可加入 UI 自动化用例覆盖：键盘弹起、流式多气泡、底部 Tab 点击与滑动切换的联合回归。
