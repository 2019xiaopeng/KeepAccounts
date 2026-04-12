# KeepAccounts Agent 交接说明（2026-04-12）

## 1. 核心目标（接手后优先级）

1. 继续推进 Phase3 的 Agent 能力升级，保持“LLM 负责规划、本地负责执行”的闭环不变。
2. 在不降低安全边界的前提下，推进 PhaseE 双模型分层路由（Lite + Pro）以优化时延与成本。
3. 所有对用户可见结论必须基于工具执行结果，禁止“无观察结论”。

## 2. 实操目录说明（你主要会改哪里）

1. 运行时核心逻辑
- [app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt](app/src/main/java/com/qcb/keepaccounts/data/repository/ChatRepository.kt)
  - 意图路由、Planner 接入、写入执行、回执拼装、可见层文本控制。

2. Planner 网关
- [app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowPlannerGateway.kt](app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowPlannerGateway.kt)
  - Function Calling 请求、Prompt、结构化参数解析。

3. Chat 网关
- [app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowAiGateway.kt](app/src/main/java/com/qcb/keepaccounts/data/repository/SiliconFlowAiGateway.kt)
  - SSE 解析、隐藏标签剥离、错误映射。

4. 依赖注入与配置接线
- [app/src/main/java/com/qcb/keepaccounts/data/AppContainer.kt](app/src/main/java/com/qcb/keepaccounts/data/AppContainer.kt)
  - 模型注入、网关注入、超时策略、Planner 开关与灰度参数。
- [app/build.gradle.kts](app/build.gradle.kts)
  - BuildConfig 字段定义（API URL/KEY、模型配置）。

5. 单测回归主阵地
- [app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt](app/src/test/java/com/qcb/keepaccounts/data/repository/ChatRepositoryBatchLedgerTest.kt)
- [app/src/test/java/com/qcb/keepaccounts/domain/agent/AgentStyleFormatterTest.kt](app/src/test/java/com/qcb/keepaccounts/domain/agent/AgentStyleFormatterTest.kt)

6. 方案与决策文档
- [docs/p3-s5-agent-workflow-upgrade-plan.md](docs/p3-s5-agent-workflow-upgrade-plan.md)
- [docs/p3-s5-phasec-checklist.md](docs/p3-s5-phasec-checklist.md)
- [docs/p3-s5-phasee-tiered-model-routing-plan.md](docs/p3-s5-phasee-tiered-model-routing-plan.md)
- [docs/adr](docs/adr)

## 3. 推荐阅读顺序（先读这些）

1. 总路线与阶段状态
- [docs/p3-s5-agent-workflow-upgrade-plan.md](docs/p3-s5-agent-workflow-upgrade-plan.md)

2. 架构决策（必读）
- [docs/adr/046-phase3-agent-workflow-llm-planner-local-executor.md](docs/adr/046-phase3-agent-workflow-llm-planner-local-executor.md)
- [docs/adr/047-siliconflow-model-and-timeout-policy.md](docs/adr/047-siliconflow-model-and-timeout-policy.md)
- [docs/adr/048-phase3-tiered-model-routing-strategy.md](docs/adr/048-phase3-tiered-model-routing-strategy.md)

3. 当前要实施的新增方案
- [docs/p3-s5-phasee-tiered-model-routing-plan.md](docs/p3-s5-phasee-tiered-model-routing-plan.md)

4. 需求基线
- [docs/prd.md](docs/prd.md)
- [docs/TRD.md](docs/TRD.md)

## 4. 需求记录在哪里

1. 产品需求（是什么、为什么）
- [docs/prd.md](docs/prd.md)

2. 技术需求（怎么做、约束是什么）
- [docs/TRD.md](docs/TRD.md)

3. 阶段目标与验收条目
- [docs/p3-s5-phasec-checklist.md](docs/p3-s5-phasec-checklist.md)
- [docs/p3-s5-phasee-tiered-model-routing-plan.md](docs/p3-s5-phasee-tiered-model-routing-plan.md)

4. 技术选型决策依据
- [docs/adr](docs/adr)

## 5. 远程仓库与分支信息

1. 远程仓库
- https://github.com/2019xiaopeng/KeepAccounts.git

2. 当前交接工作分支
- feat/p3-agent-tools

3. 当前工作目录（重要）
- 使用的是 worktree：F:/code/Android/KeepAccounts/.worktrees/p3-s1

## 6. 修改时必须注意的事项

1. 分支与目录
- 先确认在 worktree 分支 feat/p3-agent-tools，再改代码。
- 避免在根仓库其他分支误改误提。

2. 安全边界不可破
- delete 仍需两阶段确认策略（除显式 transactionId 单条命中例外）。
- 无观察结果不能输出业务结论。
- 高风险场景必须可回退到 Pro 模型。

3. 用户可见层约束
- 不允许向用户泄漏结构化负载（如 NOTE/THINK/调试 JSON）。
- update 文案应保持动态确认能力（事项 + 金额）。

4. 提交范围控制
- 不要把无关文件混入提交：
  - .idea 下本地文件
  - findings.md / progress.md / task_plan.md
- 谨慎处理 local.properties 与构建产物，避免泄漏敏感信息。

5. 回归要求
- 修改 ChatRepository 或路由策略后，至少执行：
  - :app:testDebugUnitTest --tests com.qcb.keepaccounts.data.repository.ChatRepositoryBatchLedgerTest
  - :app:testDebugUnitTest --rerun-tasks

6. 文档同步
- 重大技术策略变化要补 ADR。
- 每次阶段性变更要更新对应 checklist/phase 文档。

## 7. 最近关键提交（帮助快速定位上下文）

1. 337a63f: docs(phase3): add dual-model routing plan and ADR for Lite+Pro strategy
2. 8eb1d60: fix(agent): harden update correction target, remark patching, and update ack text
3. 5721c6b: fix(agent): restore stats insight cards and harden siliconflow config
4. 4768818: fix(chat): improve empathy context and payload rendering
5. 9b5d428: feat(agent): add amount/count/time normalizers for phaseC

## 8. 接手后第一周建议动作

1. 先做 PR-1（仅 DI 与配置接线，行为保持全 Pro）。
2. 再做 PR-2（TieredPlannerRouter shadow 打点，不切流量）。
3. 指标稳定后再开 PR-3（低风险 Lite 灰度执行 + 自动升级 Pro）。
