# P2 T2 验证证据

更新时间：2026-04-08
验证分支：feat/p2-time-semantics-partial

## 执行命令

1. 核心时间语义回归测试
  - .\gradlew.bat :app:testDebugUnitTest --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryTimeSemanticsTest" --tests "com.qcb.keepaccounts.ui.format.SemanticDateTimeTest" --tests "com.qcb.keepaccounts.ui.format.SemanticSearchTextsTest" --no-daemon --console=plain
2. 编译门禁
  - .\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain

## 测试结果

1. ChatRepositoryTimeSemanticsTest
  - tests=2
  - failures=0
  - errors=0
2. SemanticDateTimeTest
  - tests=3
  - failures=0
  - errors=0
3. SemanticSearchTextsTest
  - tests=2
  - failures=0
  - errors=0

## 场景 A：把昨天中午午餐改成10块

1. 输入：把昨天中午午餐改成10块
2. 预期：命中昨天午餐记录，更新金额为 10.0，时间语义保持昨天中午。
3. 结果：通过
  - amount=10.0
  - recordTimestamp 对应昨天 12:00
  - 回执 transactionId=1
  - 回执 receiptRecordTimestamp 与交易记录一致

对应测试：sendMessage_updatesYesterdayLunchAmountAndKeepsSemanticTimestamp

## 场景 B：晚饭改成26

1. 输入：晚饭改成26
2. 预期：无需追问秒级时间，按语义定位晚饭并更新。
3. 结果：通过
  - 午餐记录金额保持 18.0
  - 晚饭记录金额更新为 26.0
  - recordTimestamp 对应当天 19:00（晚饭语义默认时段）
  - 回执 transactionId=2
  - 回执 receiptRecordTimestamp 与交易记录一致

对应测试：sendMessage_updatesDinnerWithoutExactTimeUsingTodaySemanticTime

## 展示一致性补充验证

1. 聊天回执
  - 日期使用语义 dateText（示例：今天 04-08）
  - 时间使用 HH:mm
2. 账本
  - 列表时间和详情时间统一使用语义时间格式器
3. 编辑页
  - 预填 recordTimestamp 透传
  - 保存时使用 applyCurrentTimeToDate 保持日期语义与当前生效时刻
4. 搜索
  - 支持绝对日期与相对日期（今天/昨天/前天）共同检索

## 本轮补充提交

1. d0b3c9c feat(ai): phase2 semantic relative-date search indexing
2. 9254e81 feat(ai): phase2 align chat receipt date text

## 关联文件

1. [app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt](app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt)
2. [app/src/main/java/com/qcb/keepaccounts/ui/format/SemanticDateTime.kt](app/src/main/java/com/qcb/keepaccounts/ui/format/SemanticDateTime.kt)
3. [app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt](app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt)
4. [app/src/main/java/com/qcb/keepaccounts/ui/screens/HomeScreen.kt](app/src/main/java/com/qcb/keepaccounts/ui/screens/HomeScreen.kt)
5. [app/src/main/java/com/qcb/keepaccounts/ui/screens/LedgerScreen.kt](app/src/main/java/com/qcb/keepaccounts/ui/screens/LedgerScreen.kt)
6. [app/src/main/java/com/qcb/keepaccounts/ui/screens/ManualEntryScreen.kt](app/src/main/java/com/qcb/keepaccounts/ui/screens/ManualEntryScreen.kt)
7. [app/src/main/java/com/qcb/keepaccounts/ui/screens/SearchScreen.kt](app/src/main/java/com/qcb/keepaccounts/ui/screens/SearchScreen.kt)
8. [app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryTimeSemanticsTest.kt](app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryTimeSemanticsTest.kt)
9. [app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticDateTimeTest.kt](app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticDateTimeTest.kt)
10. [app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticSearchTextsTest.kt](app/src/test/java/com/qcb/keepaccounts/ui/format/SemanticSearchTextsTest.kt)
