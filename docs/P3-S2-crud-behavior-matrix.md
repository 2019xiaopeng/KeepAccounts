# P3-S2 CRUD 行为矩阵

- 版本：v1
- 日期：2026-04-08
- 适用范围：`feat/p3-agent-tools` 的 P3-S2 写路径

## 统一约束

1. 所有写动作（create/update/delete）必须先经过 preview。
2. 批量场景采用 partial_success：单条失败不阻塞其他条。
3. delete 默认两阶段：先预览计划，再用户确认后执行。
4. 统一回执结构：`successCount` / `failureCount` / `items` / `errors`。

## 行为矩阵

| 动作 | 输入示例 | Preview 输出 | 执行门禁 | 执行结果 | 回执关键字段 |
| --- | --- | --- | --- | --- | --- |
| create（单条） | 今天午饭 28 | 解析金额/分类/时间/备注 | preview 通过后执行 | 新增 1 条交易 | successCount=1，items[0].status=success |
| create（批量） | 昨天奶茶 18，晚饭 42，打车 25 | 每条生成预览项 | 逐条 preview；失败项保留 | 支持部分成功 | successCount/failureCount 按条统计，errors 汇总失败原因 |
| update（按 transactionId） | 把这条改成 35（含 transactionId） | 展示目标交易 + 新值 | 目标存在且参数合法 | 更新 1 条交易 | items 记录 transactionId、before/after 可追溯 |
| update（按时间语义） | 昨天午餐改成 30 | 解析 P2 时间语义定位目标 | 命中最近匹配交易 | 更新 1 条或失败 | failureReason/ errors 说明“未找到匹配记录”等 |
| delete（按 id） | 删除 id=123 | 回显待删交易摘要 | 仍需确认（确认删除） | 删除 1 条交易 | successCount=1，items 含 transactionId |
| delete（自然语言批量） | 删除最近两条餐饮记录 | 返回命中交易列表与金额汇总 | 必须二次确认 | 删除 N 条交易 | successCount=N，deleteCount=N，items 展开每条 transactionId |
| delete（高风险） | 删除最近十条记录 | 返回高风险提示 | 若未确认则阻断执行 | 首轮不落库 | failureCount>0，errors 含确认提示 |
| delete（确认后重试） | 确认删除 | 复用/重建删除计划 | 通过确认门禁 | 真正落库删除 | 从失败预览转为成功回执 |

## 失败与降级策略

1. 参数校验失败：该条标记失败，写入 `failureReason`，并聚合到 `errors`。
2. 目标不存在（update/delete）：该条失败，其他条继续。
3. 预览阻断（delete 未确认）：返回失败回执，不执行真实删除。
4. 部分成功：`items` 同时包含 success 与 failure，计数与 `errors` 保持一致。

## 回执示例（简化）

```json
{
  "successCount": 2,
  "failureCount": 1,
  "errors": [
    "删除 4 笔记录属于高风险操作，请回复‘确认删除’继续。"
  ],
  "items": [
    {
      "status": "success",
      "action": "delete",
      "transactionId": 101,
      "amount": 35.0,
      "category": "餐饮美食"
    },
    {
      "status": "failure",
      "action": "update",
      "failureReason": "未找到匹配记录"
    }
  ]
}
```

## 测试映射

- create/update/delete 统一链路：`ChatRepositoryBatchLedgerTest`
- delete 预览与确认两阶段：`ChatRepositoryBatchLedgerTest`
- orchestrator delete 路由：`LedgerAgentOrchestratorTest`
- 时间语义兼容性（P2）：`ChatRepositoryTimeSemanticsTest`
