# ADR 032: AI 角色预设与防 OOC 工程接入

- Status: Accepted
- Date: 2026-03-30

## Context
现有 AI 管家仅支持语气档位（贴心治愈/傲娇毒舌/理智管家），缺少：
1. 明确的角色预设（沈星回/黎深/祁煜/秦彻/夏以昼）。
2. 面向长期对话的防 OOC 工程（防跳戏、防元叙事、防串角色）。
3. 与角色工程对应的文档分册，难以持续维护。

## Decision
1. 在 `AiAssistantConfig` 中新增三项配置：
- `rolePreset: AiRolePreset`
- `oocGuardEnabled: Boolean`
- `oocGuardLevel: OocGuardLevel`

2. 在 `设置 -> AI 专属管家` 页面增加：
- 角色设定区：五角色可切换。
- 防 OOC 工程区：开关 + 轻量/平衡/严格。

3. 在 `ChatRepository` 的系统提示词中引入：
- 角色不可变锚点。
- 角色表达风格与禁区。
- 防 OOC 执行规则（含自检要求）。

4. 在输出后置阶段增加守卫：
- 过滤模型元信息泄漏文本。
- 严格模式识别错误角色自称并回退安全回复。

5. 在 `docs/ai_roles/` 建立角色分册：
- 总册 `README.md`
- 五角色分册 `01_xavier.md` ~ `05_caleb.md`

## Why A over B

### A: 角色预设 + 防 OOC 分层方案（本次选择）
- 角色信息作为配置持久化，UI 与 Prompt 引擎共享同一真源。
- Prompt 约束与输出守卫双层防护，既提高角色一致性，也保留记账可靠性。
- 通过分册文档对齐后续扩展，降低维护成本。

### B: 仅改 Prompt 文案，不改设置与存储
- 用户无法主动切换角色与防护等级。
- 规则不可观测，不利于排查与迭代。
- 长期维护易出现“代码与文档脱节”。

## 操作过程（本次实现记录）
1. 模型层
- 修改 `app/src/main/java/com/qcb/keepaccounts/ui/model/AppUiModels.kt`
- 新增 `AiRolePreset`、`OocGuardLevel`、`AiAssistantConfig` 对应字段。

2. 持久化层
- 修改 `app/src/main/java/com/qcb/keepaccounts/data/local/preferences/UserSettingsRepository.kt`
- 新增 DataStore key 与解析/写入逻辑。

3. 设置页与入口展示
- 修改 `app/src/main/java/com/qcb/keepaccounts/ui/screens/AISettingsScreen.kt`
- 新增角色设定区、防 OOC 开关与等级选项。
- 修改 `app/src/main/java/com/qcb/keepaccounts/ui/screens/ProfileScreen.kt`
- 在 AI 卡片展示“角色摘要 + 语气”。
- 修改 `app/src/main/java/com/qcb/keepaccounts/ui/screens/InitialSetupScreen.kt`
- 透传新增配置字段，保持初始化结构一致。

4. Prompt 与防 OOC 引擎
- 修改 `app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt`
- 新增角色档案构建、系统提示词注入、防 OOC 输出守卫与错误身份回退。

5. 文档分册
- 新增目录 `docs/ai_roles/`
- 新增 `README.md` 与五角色文档。

## Consequences
- 正向：
  - 用户可在设置页可视化管理角色与防 OOC 级别。
  - AI 对话角色一致性更高，元信息泄漏风险降低。
  - 文档与实现同步，后续扩展角色成本可控。
- 代价：
  - 配置与 Prompt 逻辑复杂度上升。
  - 严格模式可能在少数语境下触发保守回退。

## Next
1. 增加“角色一致性测试集”自动化回归（关键提示词+预期输出）。
2. 在 AI 设置页新增“预览测试”按钮，便于快速验证角色稳定性。
3. 根据用户反馈细化 strict 级别误伤场景。