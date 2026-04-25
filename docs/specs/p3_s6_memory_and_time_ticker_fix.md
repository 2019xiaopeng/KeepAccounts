# P3-S6 补充修复：Paging 内存释放与相对时间动态刷新规范

**背景**：
当前版本的聊天页（ChatScreen）虽然接入了 Paging 3，但未配置 `maxSize`，导致上滑历史记录时内存无上限增长（未主动释放不可见页）。同时，微信风格的相对时间分割线（如“今天 14:20”）在渲染后即固定，跨天或应用挂后台恢复后不会自动刷新为“昨天”。

请严格按照以下规范对相关代码进行重构。

---

## 任务 1：Paging 3 内存主动释放机制
**目标**：限制 Paging 3 内存中缓存的数据量，实现平滑的“加载新页，丢弃旧页”。

**修改点 1：`ChatViewModel.kt`**
定位到 `pagedMessages` 的 `Pager` 初始化块：
```kotlin
val pagedMessages: Flow<PagingData<AiChatRecord>> =
    Pager(PagingConfig(pageSize = 20)) { ... }
```
**要求**：
1. 为 `PagingConfig` 增加 `maxSize = 60` 属性。
2. 增加 `enablePlaceholders = false` 属性（防止因占位符导致的跳动）。
3. 确保最终配置类似于：`PagingConfig(pageSize = 20, maxSize = 60, enablePlaceholders = false)`。

---

## 任务 2：相对时间文案动态刷新机制（Time Ticker）
**目标**：打破“生成即固定”的静态 UI，让时间分割线（TimeDividerComponent）能够随现实时间的推移（如跨越零点）自动重组并更新文案。

**修改点 1：`ChatScreen.kt` 引入全局 Time Ticker**
在 `ChatScreen` 组件的顶部状态声明区域（紧接着 `val listState = rememberLazyListState()` 之后），新增一个驱动时间刷新的状态：
```kotlin
// 驱动相对时间（今天/昨天等）动态刷新的 Ticker
var currentTimeTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

// 1. 每分钟自动 Tick 一次
LaunchedEffect(Unit) {
    while (true) {
        delay(60_000L) // 60秒
        currentTimeTick = System.currentTimeMillis()
    }
}

// 2. 监听生命周期，App 从后台切回前台时立即 Tick 一次（防止挂机跨天）
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            currentTimeTick = System.currentTimeMillis()
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
```
*(注意：需要按需引入 `androidx.lifecycle.compose.LocalLifecycleOwner` 和 `androidx.lifecycle.LifecycleEventObserver`)*

**修改点 2：`ChatScreen.kt` 绑定 Ticker 到时间分割线**
定位到 `LazyColumn` 内部渲染 `TimeDividerComponent` 的位置。
原来的代码可能是：
```kotlin
TimeDividerComponent(
    text = formatWeChatStyleTime(currentRecord.timestamp),
    palette = palette,
)
```
**要求**：
1. 将 `formatWeChatStyleTime` 的第二个参数（默认是 `System.currentTimeMillis()`）显式绑定为刚刚创建的 `currentTimeTick`。
2. 修改后类似于：
```kotlin
TimeDividerComponent(
    text = formatWeChatStyleTime(
        timestamp = currentRecord.timestamp, 
        nowMillis = currentTimeTick // 强制依赖会变的状态，触发 Compose 重组
    ),
    palette = palette,
)
```

---

## 验收标准 🎯
- [ ] 向上滑动聊天记录超过 3 页（60条）后，继续上滑时，通过 Android Studio Profiler 或 Logcat 可以确认 Paging 触发了旧页数据的 drop（内存回收）。
- [ ] 停留在聊天页，当现实时间跨越零点（或手动修改手机系统时间到第二天），无需任何点击或滑动操作，列表中的“今天”会自动变为“昨天”。
- [ ] App 挂在后台跨天后，重新切回前台，时间分割线立刻刷新为正确的相对语义。
