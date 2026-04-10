# P3-S4 二轮增强验证证据（流式安全 + 聊天回执与洞察 UI）

- 更新时间：2026-04-09
- 分支：feat/p3-agent-tools
- 阶段：P3-S4（二轮增强）

## 1. 目标问题

1. 防止流式截断或模型幻觉导致 JSON/标签片段暴露到可见文本。
2. 回执卡片标题按动作语义展示（新增/修改/删除），避免状态误导。
3. Query/Stats 结构化结果在聊天区可视化展示，降低阅读负担。
4. 失败回执补充明确引导，支持快速转人工补全。

## 2. 实施要点

1. `ChatRepository.extractReceiptDraftsFromText` 改为“标签优先”：
   - 若存在 `<DATA>/<RECEIPT>` 结构化 payload，仅解析标签内容。
   - 仅在无标签时再解析 Markdown JSON，避免 `items` 子对象被重复当作独立草稿执行。
2. `ChatRepository` 的流式文本清洗路径保持：
   - 清除完整 payload 标签与 Markdown JSON 块。
   - 截断未闭合 payload 标签片段与未闭合代码围栏片段。
3. `ChatScreen` 增强回执展示：
   - 回执标题按 `action` 显示“新增记录/修改记录/删除记录”。
   - 删除动作禁用“修改”入口，避免误操作语义。
   - 失败状态补充“去手动补全”入口与更清晰文案。
4. `ChatScreen` 新增 Query/Stats 洞察卡：
   - 解析“结构化结果”文本，展示标题、关键值与时间窗口信息。

## 3. 验证命令与结果

1. `./gradlew.bat :app:testDebugUnitTest --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest"`
2. `./gradlew.bat :app:testDebugUnitTest --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest" --tests "com.qcb.keepaccounts.data.repository.ChatRepositoryTimeSemanticsTest"`
3. `./gradlew.bat :app:testDebugUnitTest`

结果：全部通过。

## 4. 关键回归点

1. Markdown JSON 回执可解析并成功记账。
2. 分片 `<DA` + `TA>...</DATA>` 场景可正确聚合解析，不重复入账。
3. `<DATA>` 内 `items` 不再被 inline JSON 正则重复解析，批量成功/失败计数恢复正确。

## 5. 涉及文件

1. `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
2. `app/src/main/java/com/qcb/keepaccounts/ui/screens/ChatScreen.kt`
3. `app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt`