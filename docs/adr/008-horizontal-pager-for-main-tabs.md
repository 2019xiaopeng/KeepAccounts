# ADR 008: HorizontalPager for Main Tabs

- Status: Accepted
- Date: 2026-03-27

## Context
主导航需要支持四个 Tab 的手势横向切换，并与底部导航强一致联动，同时保持子页面路由仍由 Navigation Compose 管理。

## Decision
主 Tab 容器采用 HorizontalPager；子页面（搜索、手动记账、设置、AI 设置等）继续使用 NavHost。

## Why A over B

### A: HorizontalPager + BottomNavigation 联动
- 支持自然左右滑切换主页面，交互直觉更强。
- 点击底部 Tab 可用统一动画参数控制滚动速度，体验一致。
- 主页面状态可保留在 Pager 中，避免频繁重建。

### B: 仅用 NavHost 切换四个主 Tab
- 主 Tab 间横向滑动体验需要额外手势实现，复杂度更高。
- 页面切换速度和物理感受不易统一到同一动画曲线。
- 在“主 Tab + 子页面”混合结构中，手势和返回行为更易割裂。

## Consequences
- 导航结构更清晰：主容器负责 Tab 滑动，NavHost 负责子路由。
- 需要在 MainActivity 维护 PagerState 与底部导航索引联动。
- 子页面返回继续复用系统原生 Back/边缘返回能力。
