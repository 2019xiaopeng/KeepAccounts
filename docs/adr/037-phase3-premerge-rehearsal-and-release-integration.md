# ADR 037: Phase3 启动前 P1/P2 合并演练与正式集成决策

- Status: Accepted
- Date: 2026-04-08

## Context

Phase3 计划接管 AI 账本核心链路，但 P1（批量记账）与 P2（时间语义）尚未合并入统一 release 基线。若直接在旧基线上启动 P3，会导致以下风险：

1. 关键入口文件冲突：`ChatRepository.kt`、`ChatScreen.kt`、`AppUiModels.kt`。
2. 构建与环境配置漂移：`gradle.properties`、`gradlew.bat`。
3. 测试桩与协议签名在分支叠加后可能出现不兼容。

为降低返工风险，先执行隔离 worktree 合并演练，再进行正式 release 合并。

## Decision

采用“两步集成 + 一次验证”策略：

1. 正式顺序固定为：
   - `release <- feat/p1-batch-ledger`
   - `release <- feat/p2-time-semantics`
2. P2 冲突处理采用演练已验证版本：
   - `app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt`
   - `gradle.properties`
   - `gradlew.bat`
3. 合并后补齐测试兼容修复：
   - `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryTimeSemanticsTest.kt`
4. 合并完成后必须执行编译与关键单测再允许进入 P3 编码阶段。

## Conflict Resolution Notes

1. `ChatScreen.kt`
   - 保留 P1 批量回执能力（批量成功/失败展示、批量条目语义）。
   - 接入 P2 时间语义展示（`semanticDateTimeText`）。
   - 批量条目时间统一改为语义时间输出，移除旧日期格式函数依赖。
2. `gradle.properties`
   - 保留跨环境配置：`org.gradle.java.installations.auto-download=false`。
   - 移除机器绑定路径：`org.gradle.java.installations.paths=...`。
3. `gradlew.bat`
   - 保留 Android SDK 自动探测逻辑，确保跨机器可执行。
4. `ChatRepositoryTimeSemanticsTest.kt`
   - 解决测试桩重名。
   - 对齐 DAO 接口签名（`transactionBindings`）。
   - 对齐事件参数协议（`ReceiptParsed(List<AiReceiptDraft>)`）。

## Validation

在正式合并分支执行以下验证并通过：

1. 编译：
   - `:app:compileDebugKotlin`
2. 关键测试：
   - `ChatRepositoryBatchLedgerTest`
   - `ChatRepositoryTimeSemanticsTest`
   - `SemanticDateTimeTest`
   - `SemanticSearchTextsTest`

## Evidence Commits

1. `1f359ba`: Merge `origin/feat/p1-batch-ledger` into release integration branch.
2. `e700b7d`: Merge `origin/feat/p2-time-semantics` with verified conflict resolutions.

## Consequences

- 正向：
  1. release 基线已同时具备 P1 与 P2 能力，Phase3 可在统一语义模型上展开。
  2. 冲突处理策略有 ADR 留痕，可复盘、可复用。
  3. 合并后编译与关键测试通过，降低 P3 启动不确定性。
- 代价：
  1. 增加了一次演练与一次正式集成的流程成本。
  2. release 分支在进入 P3 前需要持续维持测试回归纪律。

## References

1. [docs/adr/033-ai-scope-freeze-and-agent-transition-roadmap.md](docs/adr/033-ai-scope-freeze-and-agent-transition-roadmap.md)
2. [docs/adr/034-batch-receipt-protocol-normalization.md](docs/adr/034-batch-receipt-protocol-normalization.md)
3. [docs/adr/035-phase2-relative-date-search-indexing.md](docs/adr/035-phase2-relative-date-search-indexing.md)
4. [docs/adr/036-phase2-chat-receipt-datetext-alignment.md](docs/adr/036-phase2-chat-receipt-datetext-alignment.md)