# ADR 031: 账本按选中时间过滤与当前月趋势截断

- 日期: 2026-03-29
- 状态: Accepted

## 背景

账本页存在两个一致性问题：

1. 底部“账本明细”未和当前选中时间绑定，仍可能展示全量记录。
2. 月度“每日趋势”会把未来日期也绘制出来，形成不合理的平线/零值延伸。

## 决策

1. 在 ViewModel 引入账本时间状态：`LedgerTimeSelection(year, month)` 与 `LedgerFilterMode(MONTH, YEAR)`。
2. 在 ViewModel 提供派生流：
   - `selectedMonthTransactions`
   - `selectedYearTransactions`
   - `filteredTransactions`（按 `LedgerFilterMode` 选择月/年结果）
3. UI 侧账本明细只绑定 `filteredTransactions`，不再直接对全量数据库数据做明细过滤。
4. 月度趋势数据改为按“日”构建：
   - 过去月份：填充整月天数。
   - 当前真实月份：仅填充 1..today，today 之后数据点置空（不绘制）。
5. 趋势图绘制支持空值断线：线条在最后有效点结束；X 轴可继续显示到月底标签。

## 影响

- 切换月份/年份后，账本明细和时间选择状态保持一致，刷新更即时且可预期。
- 当前月趋势不再错误延伸到未来日期，视觉语义更符合真实时间线。

## 代价与权衡

- ViewModel 增加额外状态流与组合流，复杂度小幅上升。
- 月度趋势从“分桶”改为“日级”后，数据点数量增加，但在当前数据规模下可接受。
