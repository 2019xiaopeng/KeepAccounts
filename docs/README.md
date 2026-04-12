# KeepAccounts 文档索引（Phase4 基线）

## 1. 主线保留文档

- `prd.md`：产品需求定义。
- `TRD.md`：技术需求与约束。
- `API_Docs.md`：接口与数据结构说明。
- `AI_Humanized_Design.md`：AI 对话与人性化设计原则。
- `KeepAccounts_UI_Design.md`：核心 UI 设计与交互规范。
- `development_plan_v1.2-v1.4.md`：四阶段开发与测试执行手册。
- `p4-kickoff-2026-04-12.md`：Phase4 启动目标、范围、验收、风险与里程碑。
- `rc-smoke-checklist.md`：收敛发布前的 RC 冒烟验证清单。
- `release_notes.md`：当前主线发布说明。
- `copilot_instructions.md`：协作开发规范。
- `adr/`：全部架构决策记录（长期保留）。

## 2. 历史过程文档归档

- 归档分支：`docs/archive-p1-p3-20260412`
- 用途：保留 Phase1-Phase3 全量过程记录（验证证据、清单、演练、阶段报告等）。
- 原则：主线保持可维护最小集合，学习与审计材料在归档分支完整追溯。

## 3. 新增文档规则（从 Phase4 开始）

1. 重大技术选型：必须新增 ADR。
2. 阶段启动/收尾：在主线保留一份启动文档与一份收尾报告。
3. 详细过程记录：优先写入归档分支，避免主线文档持续膨胀。
