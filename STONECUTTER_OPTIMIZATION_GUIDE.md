# Stonecutter 重构优化落地指南（可跟踪任务清单）

更新时间：2026-02-23  
适用仓库：`LumiChat`

## 1. 目标与完成标准

本指南用于将当前项目重构到更符合 Stonecutter 多版本开发最佳实践的状态，并提供可逐步执行、可跟踪的任务清单。

完成标准：

1. 版本矩阵在配置、目录、CI、文档中保持一致。
2. PR 检查覆盖所有支持的 Minecraft 版本，而非仅活动版本。
3. 版本差异逻辑集中到兼容层，业务代码中条件编译显著减少。
4. 提交流程包含 `resetActiveVersion` 校验，避免提交活动版本噪音。
5. 文档命令可直接执行，无无效命令。

## 2. 执行约定

1. 每完成一个任务，直接勾选任务清单。
2. 每完成一个阶段，执行该阶段验收命令。
3. 每个阶段单独提交，提交信息遵循 Conventional Commits（中文）。

推荐提交信息示例：

- `chore(stonecutter): 统一版本矩阵定义`
- `refactor(compat): 抽离多版本兼容适配层`
- `ci: 统一多版本构建与PR校验流程`
- `docs(stonecutter): 修正多版本开发文档`

## 3. 阶段总览

| 阶段 | 目标 | 预计工作量 | 状态 |
|---|---|---:|---|
| P0 | 建立基线与分支保护 | 0.5 天 | [ ] |
| P1 | 统一版本矩阵单一真相 | 0.5 天 | [ ] |
| P2 | 精简 Stonecutter/Gradle 配置 | 0.5-1 天 | [ ] |
| P3 | 抽离兼容层并收敛条件编译 | 1-2 天 | [ ] |
| P4 | CI 改造为全版本校验 | 0.5-1 天 | [ ] |
| P5 | 增加提交流程守护脚本 | 0.5 天 | [ ] |
| P6 | 文档修正与统一 | 0.5 天 | [ ] |
| P7 | 最终验收与发布前检查 | 0.5 天 | [ ] |

## 4. 可跟踪任务清单

### P0 基线与保护

- [ ] `P0-1` 创建优化分支：`git switch -c chore/stonecutter-optimization`
- [ ] `P0-2` 记录当前工作区状态：`git status --short --branch > /tmp/lumichat-status-baseline.txt`
- [ ] `P0-3` 确认本轮优化范围只涉及 Stonecutter/构建/文档相关文件

阶段验收命令：

```bash
git branch --show-current
git status --short --branch
```

阶段通过标准：

1. 当前分支已切换到优化分支。
2. 基线状态已留档。

---

### P1 统一版本矩阵单一真相

目标：只保留一套“支持版本”的事实来源，消除配置/文档不一致。

- [ ] `P1-1` 选择并确认目标支持版本集合（建议按“长期支持组 + 最新组”组织，例如 `1.16.5`、`1.17`、`1.18`、`1.19`、`1.20.x`、`1.21.x`）
- [ ] `P1-2` 对齐 `settings.gradle.kts` 中 `versions(...)` 与 `vcsVersion`
- [ ] `P1-3` 清理不再支持版本的 `versions/<mc-version>/gradle.properties`（如不再支持）
- [ ] `P1-4` 修正文档中版本描述（`mulitversionbuild.md`、`README.md`）
- [ ] `P1-5` 修正多版本构建命令示例（确保 `./gradlew :1.21.11:build` 格式正确）

阶段验收命令：

```bash
find versions -maxdepth 2 -type f | sort
rg -n "versions\\(|1\\.20\\.1|1\\.21\\.1|1\\.21\\.10|1\\.21\\.11" settings.gradle.kts README.md mulitversionbuild.md
```

阶段通过标准：

1. 配置、目录、文档描述同一版本矩阵。
2. 文档中不存在无效 Gradle 命令格式。

---

### P2 精简 Stonecutter/Gradle 配置

目标：保留“正在使用”的配置项，提升可维护性与可读性。

- [ ] `P2-1` 审核 `stonecutter.gradle.kts` 的 `parameters`，删除或注释未使用项
- [ ] `P2-2` 明确 `stonecutter active` 与 `vcsVersion` 的关系和约束（加注释）
- [ ] `P2-3` 评估 `mavenLocal()` 是否保留；若无必要则移除以提高可复现性
- [ ] `P2-4` 评估 `fabric-loom-remap` SNAPSHOT 版本是否替换为稳定版本（若可行）
- [ ] `P2-5` 确认 `buildAndCollect` 任务行为与产物目录符合发布流程

阶段验收命令：

```bash
rg -n "stonecutter active|vcsVersion|parameters|mavenLocal|fabric-loom-remap|buildAndCollect" settings.gradle.kts stonecutter.gradle.kts build.gradle.kts
```

阶段通过标准：

1. 配置中不存在无意义或未使用项。
2. 核心参数含义有清晰注释。

---

### P3 抽离兼容层并收敛条件编译

目标：把版本差异聚合在 `compat` 层，减少业务代码中的 `//?` 分支。

- [ ] `P3-1` 新建兼容包，例如：`src/main/java/com/riceawa/llm/compat/`
- [ ] `P3-2` 提取权限检查兼容逻辑（PermissionCheck 与旧 API 差异）
- [ ] `P3-3` 提取 `Identifier` 构造兼容逻辑
- [ ] `P3-4` 提取命令执行路径兼容逻辑（`execute` / `parseAndExecute`）
- [ ] `P3-5` 替换调用方，减少业务类中的 Stonecutter 条件分支
- [ ] `P3-6` 删除当前支持矩阵下的死分支代码

优先改造文件（建议顺序）：

1. `src/main/java/com/riceawa/llm/util/EntityHelper.java`
2. `src/main/java/com/riceawa/llm/function/impl/ServerInfoFunction.java`
3. `src/main/java/com/riceawa/llm/function/impl/ExecuteCommandFunction.java`
4. `src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java`
5. `src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java`
6. `src/main/java/com/riceawa/llm/command/LogCommand.java`
7. `src/main/java/com/riceawa/llm/command/HistoryCommand.java`

阶段验收命令：

```bash
rg -n "//\\?" src/main/java src/client/java
```

阶段通过标准：

1. 条件编译主要集中在 `compat` 层而非业务实现层。
2. 当前支持版本内不存在不可达分支。

---

### P4 CI 改造为全版本校验

目标：PR 阶段即覆盖全版本构建，减少回归风险。

- [ ] `P4-1` 合并重复工作流职责（`build.yml` 与 `build-matrix.yml`）
- [ ] `P4-2` 将 `pr-check.yml` 从“活动版本”改为“全支持版本矩阵”
- [ ] `P4-3` 统一构建产物上传规则与命名
- [ ] `P4-4` 对齐触发分支策略（包含实际开发主分支，如 `dev`）
- [ ] `P4-5` 增加失败摘要输出，方便快速定位失败版本

阶段验收命令：

```bash
rg -n "name:|on:|matrix|Build Version|Run Checks|branches|buildAndCollect|:${{ matrix.version }}:build" .github/workflows/*.yml
```

阶段通过标准：

1. PR 检查必跑所有支持版本构建。
2. 工作流不重复、职责清晰。

---

### P5 增加提交流程守护

目标：避免 Stonecutter 活动版本造成的提交噪音。

- [ ] `P5-1` 新增脚本：`scripts/check-stonecutter.sh`
- [ ] `P5-2` 脚本内固定执行：全版本构建 + `resetActiveVersion` + `git diff --exit-code`
- [ ] `P5-3` 在 `README.md` 或开发文档增加“提交前检查”步骤

建议脚本最小内容：

```bash
#!/usr/bin/env bash
set -euo pipefail

./gradlew :1.16.5:build
./gradlew :1.17:build
./gradlew :1.18:build
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
./gradlew resetActiveVersion
git diff --exit-code
```

阶段通过标准：

1. 一条命令可完成提交前核心校验。
2. `resetActiveVersion` 后无多余脏变更。

---

### P6 文档修正与统一

目标：文档即执行手册，确保新成员按文档可复现开发流程。

- [ ] `P6-1` 修正 `mulitversionbuild.md` 中错误命令格式（去除错误冒号）
- [ ] `P6-2` 文档内版本矩阵与配置完全一致
- [ ] `P6-3` 补充“推荐开发流”：`setActiveVersion -> 开发 -> 全版本构建 -> resetActiveVersion -> 提交`
- [ ] `P6-4` 检查 `README.md` 链接与文件命名一致性（可选：修正 `mulitversionbuild.md` 命名拼写）

阶段验收命令：

```bash
rg -n "./gradlew:|setActiveVersion|resetActiveVersion|1\\.20\\.1|1\\.21\\.1|1\\.21\\.10|1\\.21\\.11" README.md mulitversionbuild.md docs/*.md
```

阶段通过标准：

1. 文档命令可复制即执行。
2. 版本描述无冲突。

---

### P7 最终验收与发布前检查

目标：确认重构后产物稳定、流程闭环。

- [ ] `P7-1` 执行全版本构建
- [ ] `P7-2` 执行 `buildAndCollect` 验证产物归集
- [ ] `P7-3` 执行 `resetActiveVersion` 后检查工作区
- [ ] `P7-4` 游戏内做最小冒烟（命令注册、基础对话、关键函数调用）
- [ ] `P7-5` 输出最终“优化完成报告”

阶段验收命令：

```bash
./gradlew :1.16.5:build
./gradlew :1.17:build
./gradlew :1.18:build
./gradlew :1.19:build
./gradlew :1.20.6:build
./gradlew :1.21.11:build
./gradlew buildAndCollect
./gradlew resetActiveVersion
git status --short
```

阶段通过标准：

1. 全版本构建通过。
2. 产物路径正确。
3. Reset 后无异常变更。

## 5. 风险与回滚策略

| 风险 | 表现 | 处理策略 |
|---|---|---|
| 版本矩阵切换导致功能回归 | 某版本编译失败或运行异常 | 先恢复 `settings.gradle.kts` 版本定义，再按兼容层逐步迁移 |
| CI 改造引入误报 | PR 全红但本地可构建 | 拆分为“构建矩阵变更”和“业务重构变更”两个提交排查 |
| `resetActiveVersion` 流程不稳定 | 提交前反复出现噪音 diff | 固化脚本并在 PR 模板中强制勾选 |

## 6. 进度记录

可在每次推进后更新：

| 日期 | 阶段 | 结果 | 备注 |
|---|---|---|---|
| 2026-02-23 | 初始化 | 已创建指南 | 待开始 P0 |

