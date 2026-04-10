# P3-S4 三轮增强验证证据（完成态气泡 + 零 JSON 可见）

- 更新时间：2026-04-09
- 分支：feat/p3-agent-tools
- 阶段：P3-S4（三轮增强）

## 1. 目标

1. 聊天气泡改为完成态出现：等待阶段仅展示 TypingRow，回复完成后带文本气泡一次性出现。
2. 聊天可见文本实现“零 JSON 暴露”：包括无标签 Markdown/内联/截断 JSON 片段。

## 2. 关键实现

1. `ChatRepository.sendMessage`：取消 TextDelta 阶段 `syncAssistantReplyChunks`，仅在流结束后写入最终分段。
2. `ChatRepository`：新增可见文本清洗链路，覆盖无标签 JSON 段、尾部截断片段、残留键值行。
3. `ChatScreen`：保留同口径 UI 侧兜底清洗，避免历史消息与异常路径泄漏 JSON。
4. 新增回归测试覆盖：
- 完成态落库行为（无中间 update）。
- 无标签 JSON 不泄漏。
- 未闭合 JSON 片段不泄漏。

## 3. 验证命令与结果

1. `./gradlew.bat :app:compileDebugKotlin`
2. `./gradlew.bat :app:testDebugUnitTest --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest" --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryTimeSemanticsTest"`
3. `./gradlew.bat :app:testDebugUnitTest`

结果：全部通过。

## 4. 涉及文件

1. `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
2. `app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt`
3. `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt`
4. `docs/adr/044-phase3-s4-completion-render-and-zero-json-visibility-policy.md`