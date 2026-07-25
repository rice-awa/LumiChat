### Task 17: 清理发布元数据并同步用户文档

**Files:**
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `build.gradle.kts:105-122`
- Modify: `README.md`
- Modify: `CLAUDE.md`
- Modify: `multiversionbuild.md`
- Modify: `docs/CONFIGURATION_GUIDE.md`
- Modify: `docs/COMMANDS_GUIDE.md`
- Modify: `docs/features/TOOL_CALL_SECURITY.md`
- Modify: `docs/features/FUNCTION_DEMO.md`
- Modify: `docs/features/LOGGING_AND_HISTORY.md`
- Modify: `docs/examples/example-config-with-logging.json`
- Modify: `docs/examples/llm-logging-config-example.md`

**Interfaces:**
- Produces: 发布元数据中的 Fabric API 依赖由版本节点属性展开；删除 `suggests.another-mod`。
- Produces: 文档与 Task 1–16 的真实默认值、权限和矩阵一致。

- [ ] **Step 1: 让 fabric.mod.json 展开 Fabric API 最低版本**

增加 `fabric_api` processResources input/props，把：

```json
"fabric-api": "*"
```

改为：

```json
"fabric-api": ">=${fabric_api}"
```

删除整个 `suggests` 模板块。

- [ ] **Step 2: 同步版本矩阵**

README、CLAUDE、multiversionbuild 只声明 1.19–1.21.11 与条件注册的 26.1/26.2；删除“恢复 1.16.5–1.18”描述；产物版本统一 2.1.0。

- [ ] **Step 3: 同步安全配置与命令文档**

精确记录：模板写权限、execute_command 双开关/允许列表/玩家身份、send_message/teleport 权限、Wiki host allowlist、Schema 拒绝行为、游戏内验证步骤。删除“黑名单足以保护控制台命令”“已有 PermissionHelperTest”等不实陈述。

- [ ] **Step 4: 同步日志与 Provider 文档**

记录 raw logging 默认关闭、摘要字段、隐私风险；Provider `protocol` 和不支持协议错误；示例配置不得包含可用 key。

- [ ] **Step 5: 增加已查阅参考资料小节**

至少列出：Fabric Commands requirements、Gradle toolchains、OkHttp Calls/MockWebServer、Stonecutter versions/reset、`docs/api/Notable_Minecraft_changes.md`。

- [ ] **Step 6: 资源展开和文档静态检查**

```bash
./gradlew :1.19:processResources :1.21.11:processResources
grep -R -n 'another-mod\|build/libs/2\.0\.0\|支持 1\.16\.5' README.md CLAUDE.md multiversionbuild.md docs src/main/resources/fabric.mod.json
```

Expected: processResources 成功；grep 只允许历史 changelog/report 中的明确历史描述，不允许当前支持声明或模板残留。

- [ ] **Step 7: 提交**

```bash
git add \
  src/main/resources/fabric.mod.json \
  build.gradle.kts \
  README.md \
  CLAUDE.md \
  multiversionbuild.md \
  docs/CONFIGURATION_GUIDE.md \
  docs/COMMANDS_GUIDE.md \
  docs/features/TOOL_CALL_SECURITY.md \
  docs/features/FUNCTION_DEMO.md \
  docs/features/LOGGING_AND_HISTORY.md \
  docs/examples/example-config-with-logging.json \
  docs/examples/llm-logging-config-example.md
git commit -m "docs: 同步多版本与安全边界说明"
```

---

