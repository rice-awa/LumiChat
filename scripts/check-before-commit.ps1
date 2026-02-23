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

# 2. 全版本构建
if (-not $SkipBuild) {
    Write-Host "[2/4] 执行全版本构建..." -ForegroundColor Yellow
    
    Write-Host "  构建 1.21.10..." -NoNewline
    & ./gradlew :1.21.10:build --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host " OK" -ForegroundColor Green
    } else {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host "错误: 1.21.10 构建失败" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "  构建 1.21.11..." -NoNewline
    & ./gradlew :1.21.11:build --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host " OK" -ForegroundColor Green
    } else {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host "错误: 1.21.11 构建失败" -ForegroundColor Red
        exit 1
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
if ($LASTEXITCODE -eq 0) {
    Write-Host "  工作区干净，无多余变更" -ForegroundColor Green
} else {
    Write-Host "  警告: stonecutterReset 后仍有变更" -ForegroundColor Yellow
    Write-Host "  请检查以下文件:" -ForegroundColor Yellow
    git diff --name-only
    Write-Host ""
    Write-Host "  这些变更可能是 Stonecutter 生成的临时文件，请确认是否需要提交" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== 检查完成 ===" -ForegroundColor Green
Write-Host "可以安全提交代码了" -ForegroundColor Green
