### Task 18: 全矩阵验收、游戏内冒烟与整改报告

**Files:**
- Create: `docs/reports/multiversion-api-code-quality-remediation-verification.md`

**Interfaces:**
- Consumes: Task 1–17 的全部交付。
- Produces: 可放入 PR 的验证命令、游戏内结果、受影响版本、参考资料和剩余风险。

- [ ] **Step 1: 执行单元测试与静态闸门**

```bash
./gradlew test jacocoTestReport
grep -R -n '//?' src/main/java/com/riceawa/llm/command src/main/java/com/riceawa/llm/function/impl src/main/java/com/riceawa/llm/template src/main/java/com/riceawa/llm/util
grep -R -n 'System\.out\|System\.err' src/main/java/com/riceawa/llm
```

Expected: Gradle BUILD SUCCESSFUL；两个 grep 均无结果。

- [ ] **Step 2: 执行代表性版本构建**

```bash
./gradlew :1.19:build :1.20.6:build :1.21.11:build
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 在 Java 25 环境执行 26.x**

```bash
java -version
./gradlew :26.1:build :26.2:build
```

Expected: java major 25+；BUILD SUCCESSFUL。若 runner 没有 Java 25，本任务为 BLOCKED，不得把 26.x 记为通过。

- [ ] **Step 4: reset 并验证工作树**

```bash
./gradlew "Reset active project"
git diff --check
git status --short
```

Expected: reset BUILD SUCCESSFUL；`git diff --check` 无输出；状态只包含本任务尚未提交的 verification report。

- [ ] **Step 5: 完成游戏内矩阵**

至少在 1.19、1.21.11、26.2 各验证：普通聊天；context 压缩并在压缩期间继续发消息；普通玩家/OP 模板权限；默认不暴露 execute_command；开启允许列表后只执行允许 root；世界修改 tool；Wiki；Provider 429 重试可从测试日志确认；LLM 日志无正文和 key。

- [ ] **Step 6: 写整改验证报告**

报告固定包含：摘要、动机、报告 H/M/L 映射、受影响版本、每条验证命令及 `通过` 结果、游戏内日志/截图路径、默认配置迁移、安全行为变化、已查阅参考资料、已知剩余风险。所有结果必须来自本任务实际执行，不复制旧报告的“未运行”状态。

- [ ] **Step 7: 提交**

```bash
git add docs/reports/multiversion-api-code-quality-remediation-verification.md
git commit -m "docs(reports): 记录代码质量整改验证结果"
```

- [ ] **Step 8: 最终分支审查**

使用 `superpowers:requesting-code-review` 的 reviewer，对 `MERGE_BASE..HEAD` 生成完整 review package。审查必须逐项核对 Global Constraints 和报告映射；所有 Critical/Important 由一个修复子代理批量修复、运行覆盖测试并复审。通过后使用 `superpowers:finishing-a-development-branch`。

---

## 最终验收矩阵

| 维度 | 闸门 |
|---|---|
| 并发 | permit 不泄漏；active/queued 不为负；timeout/rejection/exception 都有测试 |
| 上下文 | 压缩期间 append 保留；clear/update 使旧快照失效；外部 LLM 不持锁 |
| 线程 | Wiki 在 bounded IO executor；所有 Minecraft 与 context commit 在 server thread |
| 权限 | 非 OP 不可写全局模板、不可 teleport、不可向他人 send_message |
| 命令 | execute_command 默认不存在；启用 + allowlist 后仍用玩家 source |
| SSRF | Wiki 仅 HTTPS 精确 host，禁 IP/userinfo/非 443/redirect |
| 日志 | 默认无 prompt/response/tool args/API key；只有摘要与 hash |
| Provider | manager/health checker 共用 factory；真实 provider name；未知 protocol fail closed |
| 重试 | 429/502/503/504 与网络异常重试；400 不重试；backoff + jitter 有界 |
| Schema | 全函数 additionalProperties=false；类型、required、enum、range、oneOf 运行时生效 |
| compat | 业务四目录 `//?` 为 0；差异只在 compat/mixin/entrypoint |
| 构建 | 1.19、1.20.6、1.21.11、26.1、26.2 全绿 |
| 文档 | 矩阵、2.1.0、权限、日志、Provider、发布依赖与代码一致 |

## 参考资料

- 报告基线：`docs/reports/multiversion-api-code-quality-review.md`
- Minecraft 版本破坏性变更：`docs/api/Notable_Minecraft_changes.md`
- Fabric Commands：`https://docs.fabricmc.net/develop/commands/basics`（`requires` 应放在命令节点，且会影响补全可见性）
- Gradle Java Toolchains：`https://docs.gradle.org/current/userguide/toolchains.html`
- Gradle Java Compatibility：`https://docs.gradle.org/current/userguide/compatibility.html`
- OkHttp Calls：`https://square.github.io/okhttp/features/calls/`
- OkHttp MockWebServer：`https://square.github.io/okhttp/`
- Stonecutter 文档源：`https://codeberg.org/stonecutter/docs/src/branch/main/docs/wiki/`

## 执行完成定义

只有 Task 18 的最终 review package 同时获得“规格符合 ✅”和“代码质量 Approved”，代表性五节点全部构建成功，游戏内三节点冒烟完成，且 `.superpowers/sdd/progress.md` 将 Task 1–18 全部标记 complete，才可宣称本计划完成。
