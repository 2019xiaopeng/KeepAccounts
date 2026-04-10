# P1 T1 验证证据

更新时间：2026-04-07
目标分支：`feat/p1-batch-ledger`

## 验证方式

1. 编译与静态检查：
   - `.\gradlew.bat :app:compileDebugUnitTestKotlin :app:compileDebugKotlin :app:lintDebug --no-daemon`
   - 结果：`BUILD SUCCESSFUL`
2. 手动执行 JVM 测试类：
   - 执行 `ChatRepositoryBatchLedgerTest`
   - 结果：`MANUAL_TEST_OK`

## 场景 A：标准 T1 输入

- 输入样例：`3.5的杨桃，18块钱的中餐，16.9的奶茶加蛋糕`
- 回执形态：多个 `<DATA>...</DATA>`
- 数据库条数：新增 `3` 条交易
- UI 结果证据：
  - 回执摘要 `successCount = 3`
  - 回执摘要 `failureCount = 0`
  - 回执明细 `items.size = 3`
  - 聊天回执绑定 `transactionIds = [1, 2, 3]`

## 场景 B：异常注入

- 输入样例：`3.5的杨桃，18块钱的中餐，16.9的奶茶加蛋糕`
- 异常注入：第 2 条回执故意缺少 `category`
- 回执形态：单个 `<DATA>{"items":[...]}</DATA>`
- 数据库条数：新增 `2` 条交易
- UI 结果证据：
  - 回执摘要 `successCount = 2`
  - 回执摘要 `failureCount = 1`
  - 回执明细 `items.size = 3`
  - 失败原因：`这笔账单缺少分类，补一句分类后我再试一次。`
  - 成功绑定 `transactionIds = [1, 2]`

## 对应测试文件

- [ChatRepositoryBatchLedgerTest.kt](file:///f:/code/Android/KeepAccounts/app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt)
- [ChatRepository.kt](file:///f:/code/Android/KeepAccounts/app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt)
- [ChatScreen.kt](file:///f:/code/Android/KeepAccounts/app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt)
