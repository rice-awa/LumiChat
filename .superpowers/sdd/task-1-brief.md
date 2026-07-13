### Task 1: 固化构建基线与代表性提交前矩阵

**Files:**
- Modify: `scripts/check-before-commit.ps1:1-79`
- Modify: `multiversionbuild.md:1-57`
- Test: `scripts/check-before-commit.ps1`（PowerShell 自检）

**Interfaces:**
- Consumes: `settings.gradle.kts` 的项目名 `1.19`、`1.20.6`、`1.21.11`、`26.1`、`26.2`。
- Produces: 提交前代表性构建矩阵；reset 后存在 diff 时退出码为 1。

- [ ] **Step 1: 复核文档与当前 Gradle 项目**

Run:

```bash
./gradlew projects
./gradlew -q javaToolchains
```

Expected: 项目至少列出 `:1.19`、`:1.20.6`、`:1.21.11`；Java 25 运行 Gradle 时额外列出 `:26.1`、`:26.2`，toolchain 输出包含 17、21、25 中当前已安装或可解析的版本。

- [ ] **Step 2: 将 PowerShell 构建段改成数据驱动矩阵**

将两个硬编码构建块替换为：

```powershell
$representativeVersions = @("1.19", "1.20.6", "1.21.11")
$javaVersionOutput = (& java -version 2>&1 | Out-String)
$javaMajorMatch = [regex]::Match($javaVersionOutput, 'version "(?:1\.)?(\d+)')
$javaMajor = if ($javaMajorMatch.Success) { [int]$javaMajorMatch.Groups[1].Value } else { 0 }
if ($javaMajor -ge 25) {
    $representativeVersions += @("26.1", "26.2")
}

foreach ($version in $representativeVersions) {
    Write-Host "  构建 $version..." -NoNewline
    & ./gradlew ":${version}:build" --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host "错误: $version 构建失败" -ForegroundColor Red
        exit 1
    }
    Write-Host " OK" -ForegroundColor Green
}
```

- [ ] **Step 3: reset 后存在差异必须失败**

把当前 warning 分支改为：

```powershell
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: Stonecutter reset 后仍有源码差异" -ForegroundColor Red
    git diff --name-only
    exit 1
}
```

只有 clean 分支才输出“可以安全提交代码了”。

- [ ] **Step 4: 同步构建指南的当前事实**

在 `multiversionbuild.md` 明确：26.1/26.2 仅在运行 Gradle 的 JVM 为 Java 25+ 时注册；产物目录为 `build/libs/2.1.0/`；代表性矩阵与脚本一致；旧 1.16.5–1.18 不支持。

- [ ] **Step 5: 验证脚本语法与快速路径**

Run:

```bash
pwsh -NoProfile -Command '$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile("scripts/check-before-commit.ps1", [ref]$null, [ref]$errors) > $null; if ($errors.Count) { $errors; exit 1 }'
pwsh -NoProfile -File scripts/check-before-commit.ps1 -SkipBuild
```

Expected: 语法检查退出 0；工作区在 reset 后无额外差异时快速路径退出 0，故意制造已跟踪源码差异时退出 1。验证完恢复该临时差异。

- [ ] **Step 6: 提交**

```bash
git add scripts/check-before-commit.ps1 multiversionbuild.md
git commit -m "fix(build): 补齐提交前代表性版本验证"
```

---

