<#
文件速览：
1. 文件职责：统一从仓库根目录启动后端 Spring Boot 服务，优先复用项目自带 Maven Wrapper。
2. 对外入口：仓库根目录执行 `powershell -ExecutionPolicy Bypass -File .\scripts\start_backend_local.ps1`。
3. 关键结构：本地 .env.local 加载、基础路径检查、Maven Wrapper 启动、控制台提示。
4. 阅读建议：如数据库配置不同，先配好 MYSQL_* 环境变量或根目录 .env.local；若未设置 APP_JWT_SECRET，脚本会为当前本地会话自动生成临时密钥。
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $repoRoot "backend"
$mavenWrapper = Join-Path $backendDir "mvnw.cmd"

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "未找到 Maven Wrapper：$mavenWrapper"
}

function Import-LocalEnvFile {
    param(
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            return
        }

        $parts = $line -split "=", 2
        if ($parts.Count -ne 2) {
            return
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ([string]::IsNullOrWhiteSpace($name)) {
            return
        }

        if ($value.Length -ge 2) {
            $startsWithDoubleQuote = $value.StartsWith('"') -and $value.EndsWith('"')
            $startsWithSingleQuote = $value.StartsWith("'") -and $value.EndsWith("'")
            if ($startsWithDoubleQuote -or $startsWithSingleQuote) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, "Process"))) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }

    Write-Host "==> 已加载本地环境变量文件：$Path" -ForegroundColor DarkGray
}

Import-LocalEnvFile -Path (Join-Path $repoRoot ".env.local")
Import-LocalEnvFile -Path (Join-Path $backendDir ".env.local")

Push-Location $backendDir
try {
    if ([string]::IsNullOrWhiteSpace($env:APP_JWT_SECRET)) {
        $env:APP_JWT_SECRET = "codex-local-demo-$([guid]::NewGuid().ToString('N'))$([guid]::NewGuid().ToString('N'))"
        Write-Host "==> 未检测到 APP_JWT_SECRET，已为当前本地会话自动生成临时 JWT 密钥" -ForegroundColor Yellow
    }

    if ([string]::IsNullOrWhiteSpace($env:APP_JWT_EXPIRE_MS)) {
        $env:APP_JWT_EXPIRE_MS = "14400000"
        Write-Host "==> 未检测到 APP_JWT_EXPIRE_MS，已使用本地演示默认值 14400000ms（4小时）" -ForegroundColor Yellow
    }

    Write-Host "==> 启动后端（默认端口 8080）" -ForegroundColor Cyan
    & $mavenWrapper spring-boot:run
} finally {
    Pop-Location
}
