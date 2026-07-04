# LumiChat GitHub Actions 工作流评估：是否应改用 `./gradlew buildAndCollect`

> 评估日期：2026-07-04
> 评估范围：`.github/workflows/` 下 4 个工作流（`build.yml`、`dev-build.yml`、`pr-check.yml`、`release.yml`）的多版本构建策略。
> 评估方式：只读评估，未修改源码，未运行构建。依据 Stonecutter 官方文档（Codeberg wiki 源码）、官方 Fabric 模板、社区推荐模板对比。
> 当前状态：环境无法构建，结论基于文档与配置静态分析。

---

## 1. 摘要 / TL;DR

- 当前 4 个工作流都采用 **「矩阵并行 + `./gradlew :<version>:build`」** 模式：每个 Minecraft 版本起一个独立 job，自行 checkout、装 JDK、下载依赖、构建，再由 `package-artifacts` job 下载所有产物合并上传。
- 官方 Fabric 模板与 Stonecutter 文档明确推荐 **`buildAndCollect`** 任务作为发布/收集手段，把所有版本的产物汇聚到 `build/libs/<mod.version>/`。
- 社区推荐（Stonecutter 文档 "Community templates" 一节点名）的 `rotgruengelb/stonecutter-mod-template` 的 CI 用的就是 **单 job + `./gradlew buildAndCollect --no-daemon`**，随后一次性 `upload-artifact path: build/libs/`。
- **结论：可以改，且建议改**。改用 `buildAndCollect` 能消除脆弱的版本探测脚本、砍掉 N 个并行 job 的重复依赖下载、统一产物路径、显著降低 CI 复杂度；代价是失去版本级并行带来的速度，需要用 toolchain + 并行/缓存来补偿。
- 同时发现 LumiChat 的 `buildAndCollect` 实现与官方模板存在一处语义偏差（多了 `dependsOn("build")`），改造建议一并修正。

---

## 2. 资料来源（已查阅参考资料）

| 来源 | 用途 |
|------|------|
| Stonecutter 官方文档 — Getting Started（`codeberg.org/stonecutter/docs` `wiki/start/index.md`） | 版本选择、模板列表、"Community templates" 指向 rotgruengelb |
| Stonecutter 官方文档 — Build Config（`wiki/config/build.md`） | `build.gradle(.kts)` API、版本比较、`sc.process` |
| Stonecutter 官方文档 — Settings Config（`wiki/config/settings.md`） | 树/分支/节点模型、data-driven 设置、构建脚本分配 |
| 官方 Fabric 模板（`github.com/stonecutter-versioning/stonecutter-template-fabric` `main` 分支） | `build.gradle.kts` 中 `buildAndCollect` 的权威实现；README 明确写 "Use `buildAndCollect` Gradle task to store mod releases in `build/libs/`" |
| 官方 NeoForge / multiloader 模板（同上 README 列表） | 一致性确认：`buildAndCollect` 在各官方模板中都存在 |
| 社区模板 rotgruengelb（`github.com/rotgruengelb/stonecutter-mod-template` `main` 分支） | `.github/workflows/build.yml` + `build_reusable.yml`：CI 端 `buildAndCollect` 单 job 实践样本 |
| LumiChat 现有源码：`settings.gradle.kts`、`stonecutter.gradle.kts`、`build.gradle.kts`、`.github/workflows/*.yml` | 现状评估依据 |

> 注：`stonecutter.kikugie.dev` 官网对 AI 爬虫返回干扰内容（"bee movie" 文本），故改从 Codeberg 上的文档源码仓库 `stonecutter/docs` 直接获取原始 markdown，确保引用准确。

---

## 3. 现状梳理

### 3.1 版本矩阵

`settings.gradle.kts:15-28` 定义了 23 个版本节点（1.19.4、1.20–1.20.6、1.21–1.21.11，以及 JDK 25 可用时再加 26.1.2、26.2），`vcsVersion = "1.21.11"`。

CI 中通过 `detect-versions` job 用 `grep + awk + jq` 解析 `settings.gradle.kts`，把版本列表组装成 JSON 矩阵（`build.yml:41-78` 等四个文件几乎逐字符复用了同一段 30+ 行脚本）。

### 3.2 四个 workflow 的共同结构

每个 workflow 都是：

```
detect-versions (grep 解析 settings.gradle.kts)
        │
        ▼
build-version × N (matrix, fail-fast: false)
   每个 job: checkout → setup-java(21 或 25) → ./gradlew :<v>:build
   → upload-artifact versions/<v>/build/libs/*.jar
        │
        ▼
build-summary + package-artifacts
   (download-artifact 全部 → find/cp 合并 → 再 upload 一个聚合 artifact)
```

差异点：

| workflow | 触发 | 特殊点 |
|----------|------|--------|
| `build.yml` | push/pr (main/master) + dispatch | 产物保留 90/120 天 |
| `dev-build.yml` | 手动，可指定分支和额外 Gradle 参数 | 产物保留 30 天，多一个 `build_timestamp` |
| `pr-check.yml` | pull_request | 不收集产物，先 `:check` 再 `:build` |
| `release.yml` | 手动，带 draft/tag/release_name/since_ref 输入 | 多一个 `publish-release` job 生成中文 changelog 并发布 GitHub Release |

### 3.3 `buildAndCollect` 当前实现

`build.gradle.kts:122-129`：

```kotlin
register<Copy>("buildAndCollect") {
    group = "build"
    val mainJarTask = named<Jar>(if (isUnobfuscated) "jar" else "remapJar")
    val sourceJarTask = named<Jar>(if (isUnobfuscated) "sourcesJar" else "remapSourcesJar")
    from(mainJarTask.map { it.archiveFile }, sourceJarTask.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    dependsOn("build")
}
```

注意两点：
1. 多了官方模板没有的 `dependsOn("build")` —— 官方模板依赖 `from(...)` 对 jar 任务的隐式依赖关系即可触发构建，`dependsOn("build")` 会额外拉起 `check`、`test` 等 `build` 聚合任务下的所有工作。
2. 用 `named<Jar>("remapJar")` 手动按 `isUnobfuscated` 取任务名；官方模板用 `loomx.modJar` / `loomx.modSourcesJar` 由 loom-back-compat 插件统一返回正确任务，不需要自己判断分支。

---

## 4. 官方/社区最佳实践对照

### 4.1 官方模板的 `buildAndCollect`（权威实现）

`stonecutter-versioning/stonecutter-template-fabric` 的 `build.gradle.kts`：

```kotlin
register("buildAndCollect") {
    group = "build"
    description = "Builds mod jars and copies results to `build/libs/{mod version}/`"
    inputs.property("version", project.property("mod.version"))
    from(loomx.modJar.flatMap { it.archiveFile },
         loomx.modSourcesJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
}
```

要点：
- **没有 `dependsOn("build")`**：`from(...)` 引用 jar 任务的 `archiveFile`，Gradle 会自动建立隐式依赖，触发对应 jar（及其上游 remap）任务，**不会**额外触发 `test`/`check`/`javadoc`。
- 用 `loomx.modJar` / `loomx.modSourcesJar` 适配 loom 变体（obfuscated/remap vs 26.1+ unobfuscated），由 `dev.kikugie.loom-back-compat` 插件提供统一入口。
- 产物落到各 node 的 `build/libs/<mod.version>/`；在根项目执行 `./gradlew buildAndCollect` 会聚合到所有 node 子项目（Gradle 默认行为：根调用任务名会运行所有定义了该任务的子项目）。

### 4.2 官方模板的 CI 观

官方 Fabric 模板仓库**本身没有 `.github/workflows`**。README 只在"Usage"里写："Use `buildAndCollect` Gradle task to store mod releases in `build/libs/`"。即官方把 CI 形态交给用户/社区模板，但把 `buildAndCollect` 定位为「收集发布产物」的标准指令。

### 4.3 社区推荐模板的 CI（rotgruengelb）

Stonecutter 官方 Getting Started 文档"Community templates"一节点名 `rotgruengelb/stonecutter-mod-template`，并注明其特性包含 "GitHub actions and commit validation"。其 `build_reusable.yml` 核心片段：

```yaml
- name: Setup Gradle
  uses: gradle/actions/setup-gradle@v4
  with:
    gradle-version: wrapper

- name: Cache Gradle packages
  uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
    restore-keys: ${{ runner.os }}-gradle-

- name: Build with Gradle
  run: ./gradlew buildAndCollect --no-daemon

- name: Upload artifacts
  uses: actions/upload-artifact@v4
  with:
    name: build-artifacts
    path: build/libs/
```

特征：
- **单 job**，无矩阵、无版本探测脚本。
- 一次 `./gradlew buildAndCollect` 构建并收集**所有**版本。
- 显式 `setup-gradle` + `cache`，用缓存弥补"串行构建多版本"的速度损失。
- 产物上传 `build/libs/`（即所有版本的收集目录）一次搞定。

### 4.4 对照表

| 维度 | LumiChat 现状 | 官方/社区推荐 | 评价 |
|------|--------------|--------------|------|
| 构建指令 | `:<v>:build` 逐版本 | `buildAndCollect` 一次 | 现状冗余、脆弱 |
| 版本探测 | 30+ 行 grep/awk/jq × 4 文件 | 无需 | 现状高风险、难维护 |
| Job 数 | 23+ 矩阵 + summary + package | 1 个 build job | 现状占用 runner 分钟多 |
| 依赖下载 | 每 job 全量下 Minecraft+mappings+FAPI | 一次，命中缓存 | 现状带宽/时间浪费 |
| 产物路径 | `versions/<v>/build/libs/*.jar` + 二次聚合 | `build/libs/<mod.version>/` | 现状需额外聚合 job |
| 产物收集 | 额外 `package-artifacts` job | `buildAndCollect` 内置 | 现状可完全删除 |
| JDK 切换 | 矩阵条件 + setup-java 双步 | toolchain 自动 | 现状硬编码、26.x 条件散落 |
| 并行度 | 版本级并行（快但贵） | 串行+缓存（省但慢） | 见第 6 节权衡 |

---

## 5. 能否改成 `./gradlew buildAndCollect`？逐项评估

### 5.1 技术可行性：✅ 可行

- LumiChat 已注册 `buildAndCollect` 任务，`build.gradle.kts:122-129`。
- Stonecutter node 即 Gradle 子项目，根项目 `./gradlew buildAndCollect` 会聚合到所有 node（与官方/rotgruengelb 模板同行径）。
- 产物统一落到 `build/libs/<mod.version>/`，`upload-artifact path: build/libs/` 一次上传。
- 23 个版本中 `26.x` 需 JDK 25，其余需 JDK 8–21。单 job 模式下 JDK 切换方案见 6.3。

### 5.2 与各 workflow 的适配

| workflow | 改造影响 | 建议 |
|----------|---------|------|
| `build.yml` | 删除 `detect-versions`/`build-version` matrix/`package-artifacts`，改为单 job `buildAndCollect` → 上传 `build/libs/`。`build-summary` 可保留（用 `gradle/actions` 的 build-scan 或简单 echo）。 | 改 |
| `pr-check.yml` | PR 检查可以仍走 `:build`（或 `buildAndCollect`），但更关键的是**只跑 check/test**而非产 jar。建议：`./gradlew check`（不跑 `buildAndCollect` 收集），更快。或保留 `buildAndCollect` 但去掉上传。 | 部分改（保留 check 路径） |
| `dev-build.yml` | 与 `build.yml` 同构，附加 `additional_args` 直接拼到 `gradlew` 后即可（`./gradlew buildAndCollect $EXTRA_ARGS`）。 | 改 |
| `release.yml` | 改为单 job `buildAndCollect` 后，`publish-release` job 直接从该 job 的 artifact 下载 jar 上传到 Release。`files: release-artifacts/**/*.jar` 路径不变。 | 改 |

### 5.3 阻碍因素

1. **本地 `buildAndCollect` 实现的 `dependsOn("build")`**：会让 CI 多跑 `test`/`check`。生产发布时可接受（甚至期望），但 PR 检查想"只构建"时会拖慢。建议对齐官方模板去掉 `dependsOn("build")`，让 `buildAndCollect` 只触发 jar 链路；需要测试时单独 `./gradlew test`。
2. **JDK 切换**（见 6.3）：必须配合 toolchain 让 Gradle 自动按 `requiredJava` 解析对应 JDK，否则单 job 下只有一个 JDK 时低版本节点会编译失败。
3. **串行构建时间**：无缓存时 23 版本串行可能很慢（见第 6 节权衡与缓解）。

### 5.4 不建议照搬的一点

`pr-check.yml:101-105` 先跑 `:check` 再跑 `:build`，两步都会触发编译上链。若统一改 `buildAndCollect`，PR 检查会变成"产 jar + 收集"——对 PR 验证是浪费。PR 检查应优先 `./gradlew :<v>:check` 或干脆 `./gradlew test`。此处不必强行统一到 `buildAndCollect`。

---

## 6. 取舍与权衡

### 6.1 时间 vs 成本

- **现状（矩阵）**：23 个 job 并行，~3–6 分钟完成，但消耗 23× runner 分钟 + 23× 全量依赖下载。GitHub 免费 runner 分钟有限，私有仓库计费更敏感。
- **`buildAndCollect`（单 job）**：串行 23 版本可能 ~15–30 分钟（视 Gradle daemon、缓存命中），但只算 1× runner 分钟，且依赖/Minecraft 下载只发生一次，后续版本命中 Gradle 缓存。

### 6.2 缓解串行慢的关键措施

1. **`gradle/actions/setup-gradle`**（rotgruengelb 用法）：官方 Gradle action 内建缓存与 build-scan，比手写 `actions/cache` 更稳；
2. **`org.gradle.parallel=true`**（`gradle.properties:3` 已有 ✅）、`-Xmx6G`（已有 ✅）——多版本并行编译，单 job 内仍能部分并行；
3. **`--no-daemon`** 在 CI 上一致（rotgruengelb 用了），但若想最大化缓存命中可去掉；
4. **只对需要 jar 的 workflow 跑 `buildAndCollect`**（build/release/dev-build），PR 检查只跑 `check`。

### 6.3 JDK 切换（26.x 需 JDK 25）

单 job 下不能再用矩阵的 `setup-java` 条件切换。两种方案：

- **方案 A（推荐）：Gradle toolchain 自动下载**
  在 `build.gradle.kts` 仿官方模板补：
  ```kotlin
  java {
      toolchain {
          vendor = JvmVendorSpec.ADOPTIUM
          languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
      }
  }
  ```
  仓库里设 `org.gradle.java.installations.auto-download=true`（或依赖 Foojay toolchain resolver），Gradle 会按每个版本的 `requiredJava` 自动拉对应 JDK，单 job 只需一个基础 JDK 21（或 25）即可。

- **方案 B：CI 装多个 JDK + 环境变量**
  `setup-java` 串行装 JDK 21 和 JDK 25，配合 toolchain `auto-download=false`、`auto-detect=true` 让 Gradle 发现本机已装的 JDK。

任一方案都要求 LumiChat 当前的 `targetCompatibility/sourceCompatibility` 配置升级为 toolchain（官方模板做法），否则单 JDK 下 26.x 节点无法编译。

### 6.4 何时保持现状（不建议改）

- 若仓库是**私有**且 runner 分钟很充裕，矩阵并行的"快"更值钱。
- 若 CI 需要对单版本做**差异化产物/测试**（例如某版本单独跑 gametest），矩阵更好挂条件。
- 若团队明确要"每个版本独立可失败"的细粒度状态徽章（矩阵天然每版本一个 job 状态）。可改造为「单 job `buildAndCollect` 后，在 summary 里解析 Gradle 输出」但不如矩阵直观。

对 LumiChat 当前规模（公开仓库、22 版本、无明显单版本差异化需求），**改造收益 > 成本**。

---

## 7. 具体改造建议（可执行草案）

### 7.1 修正 `build.gradle.kts` 的 `buildAndCollect`

对齐官方模板（去掉 `dependsOn("build")`，引入 loom 变体统一入口；注意 LumiChat 用的是裸 `fabric-loom` / `fabric-loom-remap` 而非 `loom-back-compat`，需保留按 `isUnobfuscated` 取任务的逻辑，但去掉 `dependsOn`）：

```kotlin
register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds mod jars and copies results to `build/libs/{mod version}/`"
    inputs.property("version", project.property("mod.version"))
    val mainJarTask = named<Jar>(if (isUnobfuscated) "jar" else "remapJar")
    val sourceJarTask = named<Jar>(if (isUnobfuscated) "sourcesJar" else "remapSourcesJar")
    from(mainJarTask.map { it.archiveFile }, sourceJarTask.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    // 去掉 dependsOn("build")，让 from() 的隐式依赖只触发 jar/remap 链路
}
```

同时补 toolchain（见 6.3 方案 A）。

### 7.2 `build.yml` 目标形态（草案）

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: gradle/actions/wrapper-validation@v5
      - uses: actions/setup-java@v5
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v5
        with:
          gradle-version: wrapper
          cache: true
      - run: chmod +x ./gradlew
      - run: ./gradlew buildAndCollect --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: lumichat-${{ <mod_version> }}-all
          path: build/libs/
          if-no-files-found: error
          retention-days: 90
```

> `mod_version` 可在单 job 内直接 `grep gradle.properties` 取一次，无需矩阵网关。或改为从 `buildAndCollect` 输出读取。

### 7.3 `release.yml`

构建 job 同 `build.yml`；`publish-release` job `download-artifact` 后 `files: release-artifacts/**/*.jar` 原样可用（`buildAndCollect` 产物即 jar）。

### 7.4 `pr-check.yml`

**不要**改用 `buildAndCollect`，保持 `./gradlew check`（或 `test`），追求快。仅删除 `detect-versions` matrix、改为单 job 一次 `check` 所有版本即可。如需版本粒度失败定位，可保留矩阵但只跑 `check`。

### 7.5 `dev-build.yml`

单 job `./gradlew buildAndCollect --stacktrace $EXTRA_ARGS`，`additional_args` 直接拼尾。

### 7.6 删除

- 所有 workflow 中的 `detect-versions` job 和 `package-artifacts` job（除非保留单体聚合 zip 作为发布物，可改为对 `build/libs/` 打 zip）。

---

## 8. 优先级与风险

| 项 | 优先级 | 风险 |
|----|-------|------|
| 去掉 `buildAndCollect` 的 `dependsOn("build")` | 高 | 低：`from()` 已建立隐式依赖；需回归确认 jar 仍会触发 |
| 补 toolchain 配置 | 高 | 中：需验证各版本 JDK 解析；26.x JDK 25 自动下载需联网 |
| `build.yml`/`dev-build.yml`/`release.yml` 改单 job `buildAndCollect` | 高 | 中：构建时间变长，需缓存到位 |
| `pr-check.yml` 改单 job `check`（不收 jar） | 中 | 低 |
| 删除 `detect-versions`/`package-artifacts`、版本探测 grep 脚本 | 中 | 低：减少 4 处重复脚本，去掉一处脆弱解析 |
| 保留矩阵作为可选「诊断」workflow | 低 | — |

---

## 9. 总结

当前工作流"能跑、能产出正确产物"，但**不是 Stonecutter 多版本构建的最佳实践**：

1. 与官方模板 + Stonecutter 文档点名的社区模板相比，主流做法是**单 job `./gradlew buildAndCollect` + 一次 `upload-artifact path: build/libs/`**。
2. 现状的"矩阵 + 逐版本 `:build` + 二次聚合"带来约 30 行脆弱的 grep/awk/jq 版本探测脚本、N 次重复依赖下载、额外聚合 job，**复杂度与收益不成正比**。
3. 建议：在补齐 toolchain、去掉 `dependsOn("build")` 之后，把 `build`/`dev-build`/`release` 三个产 jar 的工作流改为单 job `buildAndCollect`；`pr-check` 改为单 job `check`，不收 jar。配合 `gradle/actions/setup-gradle` 缓存以弥补串行时间。
4. 风险可控，且与仓库 `AGENTS.md` 中"文档优先工作流 / 多版本最佳实践" 一致：参考了 Stonecutter 官方 wiki、官方 Fabric 模板、官方推荐的社区模板三处来源。

唯一需在改造前**实际验证**的点（本环境无法构建）：toolchain 在各版本节点能否正确解析 JDK（尤其 1.19.4 的 Java 8、26.x 的 Java 25 自动下载）。验证命令（在可构建环境执行）：

```
./gradlew :1.19:build :1.20.1:build :1.21.11:build ``` 

（若 toolchain 自动下载 25 在受限网络下不可行，退回 6.3 方案 B：CI 预装多 JDK。）

---

## 附：参考实现对照（`buildAndCollect` 一处差异）

| | LumiChat `build.gradle.kts:122-129` | 官方模板 `build.gradle.kts` |
|---|---|---|
| `dependsOn("build")` | 有 | **无** |
| 取 jar 任务方式 | `named<Jar>(if (isUnobfuscated) "jar" else "remapJar")` 手动分支 | `loomx.modJar` / `loomx.modSourcesJar`（loom-back-compat 统一） |
| `description` | 无 | 有 |
| `inputs.property("version", ...)` | 无 | 有（利于缓存键） |

这是改造的最小、最高优先级修正点。