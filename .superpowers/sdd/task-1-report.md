# Task 1 实施报告：固化构建基线与代表性提交前矩阵

## 状态

DONE_WITH_CONCERNS

最终提交：`02a920f fix(build): 补齐提交前代表性版本验证`

## 实施内容

- 将 `scripts/check-before-commit.ps1` 的两个硬编码构建块替换为数据驱动矩阵：始终构建 1.19、1.20.6、1.21.11；从 `java -version` 解析主版本，Java 25+ 时追加 26.1、26.2。
- 将 Stonecutter reset 后的源码差异从 warning 改为 error，打印差异文件名并以退出码 1 终止；只有无差异路径会输出“可以安全提交代码了”。
- 更新 `multiversionbuild.md`：明确 26.1/26.2 仅在 Gradle JVM 为 Java 25+ 时注册、收集目录为 `build/libs/2.1.0/`、代表性矩阵与脚本一致、1.16.5-1.18 不受支持。

## 已查阅参考资料与本地事实

- 任务上下文已确认查阅官方 Gradle toolchain 指南，并要求 Gradle 构建 JVM 使用 Java 25。
- 本地 `settings.gradle.kts` 确认 Java 25 条件注册 26.1/26.2；`gradle.properties` 确认 `mod.version=2.1.0`；`build.gradle.kts` 确认收集路径来自 `build/libs/${mod.version}`。

## 验证命令与结果

所有 Gradle 命令均使用：

```bash
export JAVA_HOME=/tmp/lumichat-jdks/jdk-25.0.3+9
export PATH="$JAVA_HOME/bin:$PATH"
```

1. Gradle 项目基线：

```bash
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9 --max-workers=1 projects
```

结果：退出码 0，`BUILD SUCCESSFUL in 8s`；包含 `:1.19`、`:1.20.6`、`:1.21.11`、`:26.1`、`:26.2`。

2. Java toolchain 基线：

```bash
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9 --max-workers=1 -q javaToolchains
```

结果：退出码 0；识别 Eclipse Temurin JDK 17、21、25，Java 25 标记为 Current JVM。

3. PowerShell 可用性诊断：

```bash
for command_name in pwsh powershell powershell.exe; do command -v "$command_name" || true; done
find /usr/local /opt /tmp -maxdepth 4 -type f \( -name pwsh -o -name powershell \) -print 2>/dev/null | head -20
```

结果：退出码 0，无输出；环境未安装或暴露 PowerShell。因此 brief 中以下三个运行时检查未执行：

```bash
pwsh -NoProfile -Command '$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile("scripts/check-before-commit.ps1", [ref]$null, [ref]$errors) > $null; if ($errors.Count) { $errors; exit 1 }'
pwsh -NoProfile -File scripts/check-before-commit.ps1 -SkipBuild
# 故意制造已跟踪源码差异后的同一 -SkipBuild 命令
```

4. 修改前静态 RED（使用 `grep`，因为环境也没有 `rg`）：

```bash
status=0
grep -F '$representativeVersions = @("1.19", "1.20.6", "1.21.11")' scripts/check-before-commit.ps1 >/dev/null || { echo 'RED: missing representative version matrix'; status=1; }
grep -F '错误: Stonecutter reset 后仍有源码差异' scripts/check-before-commit.ps1 >/dev/null || { echo 'RED: dirty-tree condition is still warning-only'; status=1; }
exit "$status"
```

结果：预期退出码 1；同时报告缺少代表性矩阵、dirty-tree 仍为 warning-only。

5. 修改后静态 GREEN：检查基础矩阵、Java 25 追加矩阵、Java 25 guard、dirty-tree 分支中的 `exit 1`、2.1.0 产物目录及旧版本不支持声明。

结果：退出码 0，`Final static requirement assertions: PASS`。

6. 代表性构建矩阵：

```bash
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdks/jdk-17.0.19+10,/tmp/lumichat-jdks/jdk-21.0.11+10,/tmp/lumichat-jdks/jdk-25.0.3+9 --max-workers=1 :1.19:build :1.20.6:build :1.21.11:build :26.1:build :26.2:build
```

结果：退出码 0，`BUILD SUCCESSFUL in 38s`；78 个任务（24 executed、54 up-to-date），0 失败。

7. 补丁与暂存范围：

```bash
git diff --check
git add scripts/check-before-commit.ps1 multiversionbuild.md
git diff --cached --name-only
git diff --cached --check
```

结果：所有命令退出码 0；暂存列表严格为 `multiversionbuild.md`、`scripts/check-before-commit.ps1`。

## 修改文件

- `scripts/check-before-commit.ps1`
- `multiversionbuild.md`

报告文件位于忽略目录 `.superpowers/sdd/`，未纳入提交。

## 自审

- 数据驱动矩阵、Java 25 检测正则和失败信息逐项匹配 brief。
- dirty-tree 分支在打印 `git diff --name-only` 后立即 `exit 1`，不会落入安全提交提示。
- 文档矩阵同时更新了顶部命令示例、日常开发流程和脚本说明，避免同一文档内部互相矛盾。
- 未修改或暂存 Task 1 之外的仓库文件；构建后工作区只出现两个预期文件。

## Concerns

- 当前环境没有 `pwsh`/`powershell`，无法执行 PowerShell parser、clean `-SkipBuild` 快速路径和故意制造 dirty tree 后的退出码 1 运行时验证。实现采用 brief 提供的精确 PowerShell 代码，且已做静态 RED/GREEN 断言，但仍建议在装有 PowerShell 的环境补跑 brief Step 5。

## Review fix

原审查修复提交已按仓库“一任务一提交”要求压缩进最终提交 `02a920f`。

### 修复内容

- Java 检测现在遵循 Gradle Wrapper 的选择优先级：`JAVA_HOME` 非空时使用其 `bin/java`（Windows 为 `bin/java.exe`），否则从 PATH 解析 `java`。
- 对 `JAVA_HOME` 下可执行文件不存在、PATH 中无 Java、版本命令抛错/非零退出、版本字符串无法解析分别给出明确错误并退出 1；只有成功解析的主版本达到 25 时才追加 26.1/26.2。
- 构建指南新增“Gradle 项目节点（集合）/实际编译目标/发布兼容范围”表，明确 `1.19 -> 1.19.4` 与 `26.1 -> 26.1.2`，并在目录树中列出 Java 25+ 条件节点 26.1/26.2。

### 精确测试命令与结果

1. 修改前聚焦 RED：

```bash
status=0
grep -F '$env:JAVA_HOME' scripts/check-before-commit.ps1 >/dev/null || { echo 'RED: script does not honor JAVA_HOME'; status=1; }
grep -F '$javaVersionExitCode' scripts/check-before-commit.ps1 >/dev/null || { echo 'RED: Java version command failure is not checked'; status=1; }
grep -F '无法解析所选 Java 的主版本' scripts/check-before-commit.ps1 >/dev/null || { echo 'RED: unparseable Java version does not fail clearly'; status=1; }
grep -F '| `1.19` | `1.19.4` |' multiversionbuild.md >/dev/null || { echo 'RED: 1.19 node mapping is not explicit in current matrix'; status=1; }
grep -F '| `26.1` | `26.1.2` |' multiversionbuild.md >/dev/null || { echo 'RED: 26.1 node mapping is missing'; status=1; }
grep -F '发布兼容范围' multiversionbuild.md >/dev/null || { echo 'RED: publication compatibility range is not distinguished'; status=1; }
grep -F '├── 26.1/' multiversionbuild.md >/dev/null || { echo 'RED: conditional 26.x nodes are absent from structure'; status=1; }
exit "$status"
```

结果：预期退出码 1；七项断言全部报告 RED，确认测试针对 review 指出的缺失行为。

2. 修改后聚焦 GREEN：

```bash
status=0
grep -F '$env:JAVA_HOME' scripts/check-before-commit.ps1 >/dev/null || status=1
grep -F '$javaExecutableName = if ($IsWindows) { "java.exe" } else { "java" }' scripts/check-before-commit.ps1 >/dev/null || status=1
grep -F 'Test-Path -LiteralPath $javaExecutable -PathType Leaf' scripts/check-before-commit.ps1 >/dev/null || status=1
grep -F 'Get-Command "java" -CommandType Application' scripts/check-before-commit.ps1 >/dev/null || status=1
grep -F '$javaVersionExitCode -ne 0' scripts/check-before-commit.ps1 >/dev/null || status=1
grep -F '无法解析所选 Java 的主版本' scripts/check-before-commit.ps1 >/dev/null || status=1
if grep -F '& java -version' scripts/check-before-commit.ps1 >/dev/null; then status=1; fi
grep -F '| `1.19` | `1.19.4` | `1.19`-`1.19.4` |' multiversionbuild.md >/dev/null || status=1
grep -F '| `26.1` | `26.1.2` | `26.1`-`26.1.2` |' multiversionbuild.md >/dev/null || status=1
grep -F '`mod.mc_targets` 只描述发布平台上的兼容版本范围' multiversionbuild.md >/dev/null || status=1
grep -F '├── 26.1/' multiversionbuild.md >/dev/null || status=1
grep -F '└── 26.2/' multiversionbuild.md >/dev/null || status=1
exit "$status"
```

结果：退出码 0，`Final focused review assertions: PASS`。

3. 补丁与提交范围：

```bash
git diff --check
git diff --cached --name-only
git diff --cached --check
```

结果：均退出 0；暂存文件严格为 `multiversionbuild.md` 和 `scripts/check-before-commit.ps1`。

4. PowerShell 可用性复核：

```bash
command -v pwsh
```

结果：未找到 `pwsh`。本次按 review 要求运行聚焦静态检查，未重复完整 Gradle 矩阵；PowerShell 运行时验证限制仍与前述 Concerns 相同。

### Review fix 自审

- 保留代表性矩阵为 1.19、1.20.6、1.21.11，以及成功识别 Java 25+ 后的 26.1、26.2；未增加 Gradle discovery 构建。
- 所有 Java 检测失败路径均先打印清晰错误再退出 1，不会把未知版本误当成旧 JVM。
- 文档明确将项目节点、实际编译目标与 `mod.mc_targets` 发布范围分开，并同步顶部当前矩阵、目录结构和版本组说明。

### Controller runtime verification

使用经官方 SHA-256 校验的 PowerShell 7.6.2 Linux x64 二进制补跑运行时验证：

- Parser：退出码 0，无语法错误。
- Clean `-SkipBuild`：退出码 0，输出 `工作区干净` 与 `可以安全提交代码了`。
- Dirty `-SkipBuild`：临时修改已跟踪的 `multiversionbuild.md` 后退出码为 1，输出 `错误: Stonecutter reset 后仍有源码差异` 和文件名；临时改动已用反向补丁恢复。
- 验证后 `git diff --check` 无输出，工作树干净。
