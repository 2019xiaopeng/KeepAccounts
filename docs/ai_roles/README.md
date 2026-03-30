# 恋与深空角色分册与防 OOC 开发总册

## 目标
- 在应用内 `设置 -> AI 专属管家` 提供五位角色预设。
- 建立可配置的防 OOC 工程（开关 + 严格度）。
- 保持记账能力稳定，不因角色化影响结构化回执。

## 分册结构
- `01_xavier.md`：沈星回（Xavier）
- `02_zayne.md`：黎深（Zayne）
- `03_rafayel.md`：祁煜（Rafayel）
- `04_sylus.md`：秦彻（Sylus）
- `05_caleb.md`：夏以昼（Caleb）

## 开发流程（一次性交付版）
1. 模型层扩展
- 新增 `AiRolePreset`（五角色枚举）。
- 新增 `OocGuardLevel`（RELAXED/BALANCED/STRICT）。
- 在 `AiAssistantConfig` 持久化 `rolePreset`、`oocGuardEnabled`、`oocGuardLevel`。

2. 存储层落地
- 扩展 DataStore Key：`ai_role_preset`、`ai_ooc_guard_enabled`、`ai_ooc_guard_level`。
- 读取时加默认值，确保历史用户无缝升级。

3. 设置页 AI 管理接入
- 新增“角色设定”模块，支持五角色切换。
- 新增“防 OOC 工程”模块，支持开关和严格度选择。
- 保存时回写到 `AiAssistantConfig`。

4. Prompt 与输出守卫
- Prompt 构建注入角色锚点、风格边界、禁区行为。
- 防 OOC 规则按等级下发（平衡/严格更强约束）。
- 输出后置守卫：过滤元叙事泄漏、严格模式识别错误自我身份并回退安全回复。

5. 验收与回归
- 配置保存后重启应用仍生效。
- 角色切换后回复风格可见差异。
- 防 OOC 开启时，不应输出“作为 AI/提示词/系统消息”等泄漏文本。
- 记账 `<DATA>` 结构输出与时间推算规则保持可用。

## 防 OOC 等级说明
- `RELAXED`：轻量过滤，仅拦截显性元信息。
- `BALANCED`：默认推荐，兼顾自然与一致性。
- `STRICT`：高强度防跑偏，出现错误身份时回退角色安全回复。

## 维护建议
- 若新增角色，必须同步修改：
  - `AiRolePreset`
  - AI 设置页角色选项
  - Prompt 角色档案
  - 角色分册文档
  - ADR 记录
