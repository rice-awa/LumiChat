#!/usr/bin/env pwsh
# Stonecutter 提交前检查脚本
# 用于验证多版本构建和 Stonecutter 状态

param(
    [switch]$SkipBuild,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

Write-Host "=== LumiChat 提交前检查 ===" -ForegroundColor Cyan
Write-Host ""

# 切换到项目根目录
Set-Location $ProjectRoot

# 1. 检查工作区状态
Write-Host "[1/4] 检查工作区状态..." -ForegroundColor Yellow
$gitStatus = git status --short
if ($gitStatus) {
    Write-Host "当前有以下未提交的更改:" -ForegroundColor Yellow
    Write-Host $gitStatus
    Write-Host ""
}

# 2. 代表性版本构建
if (-not $SkipBuild) {
    Write-Host "[2/4] 执行代表性版本构建..." -ForegroundColor Yellow

    $representativeVersions = @("1.19", "1.20.6", "1.21.11")
    $javaExecutable = "java"
    if (-not [string]::IsNullOrEmpty($env:JAVA_HOME)) {
        $javaExecutableName = if ($IsWindows) { "java.exe" } else { "java" }
        $javaExecutable = Join-Path (Join-Path $env:JAVA_HOME "bin") $javaExecutableName
        if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
            Write-Host "错误: JAVA_HOME 指定的 Java 可执行文件不存在: $javaExecutable" -ForegroundColor Red
            exit 1
        }
    } elseif ($null -eq (Get-Command "java" -CommandType Application -ErrorAction SilentlyContinue)) {
        Write-Host "错误: JAVA_HOME 未设置，PATH 中找不到 java" -ForegroundColor Red
        exit 1
    }

    try {
        $javaVersionOutput = (& $javaExecutable -version 2>&1 | Out-String)
        $javaVersionExitCode = $LASTEXITCODE
    } catch {
        Write-Host "错误: 无法执行所选 Java: $javaExecutable" -ForegroundColor Red
        exit 1
    }
    if ($javaVersionExitCode -ne 0) {
        Write-Host "错误: 所选 Java 的版本命令失败（退出码 $javaVersionExitCode）: $javaExecutable" -ForegroundColor Red
        exit 1
    }

    $javaMajorMatch = [regex]::Match($javaVersionOutput, 'version "(?:1\.)?(\d+)')
    if (-not $javaMajorMatch.Success) {
        Write-Host "错误: 无法解析所选 Java 的主版本: $javaExecutable" -ForegroundColor Red
        exit 1
    }
    $javaMajor = [int]$javaMajorMatch.Groups[1].Value
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
} else {
    Write-Host "[2/4] 跳过构建检查 (-SkipBuild)" -ForegroundColor Yellow
}

# 3. Stonecutter Reset
Write-Host "[3/4] 执行 Stonecutter Reset..." -ForegroundColor Yellow
& ./gradlew "Reset active project" --quiet 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: Stonecutter Reset 失败" -ForegroundColor Red
    exit 1
}
Write-Host "  Stonecutter Reset 完成" -ForegroundColor Green

# 4. 检查是否有 Stonecutter 生成的变更
Write-Host "[4/4] 检查 Stonecutter 状态..." -ForegroundColor Yellow
$diffStatus = git diff --exit-code 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: Stonecutter reset 后仍有源码差异" -ForegroundColor Red
    git diff --name-only
    exit 1
}
Write-Host "  工作区干净，无多余变更" -ForegroundColor Green

Write-Host ""
Write-Host "=== 检查完成 ===" -ForegroundColor Green
Write-Host "可以安全提交代码了" -ForegroundColor Green
