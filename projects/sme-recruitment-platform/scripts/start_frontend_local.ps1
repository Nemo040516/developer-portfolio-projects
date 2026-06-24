<#
文件速览：
1. 文件职责：统一从仓库根目录启动前端 Vite 开发服务，并在依赖缺失时自动安装。
2. 对外入口：仓库根目录执行 `powershell -ExecutionPolicy Bypass -File .\scripts\start_frontend_local.ps1`。
3. 关键结构：Node/npm 可用性检查、依赖安装、Vite 启动。
4. 阅读建议：若后端不在默认 8080，请先在 frontend/.env.local 中覆盖 VITE_APP_BASE_URL。
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$frontendDir = Join-Path $PSScriptRoot "..\\frontend"
$nodeModulesDir = Join-Path $frontendDir "node_modules"

Get-Command npm -ErrorAction Stop | Out-Null

Push-Location $frontendDir
try {
    if (-not (Test-Path -LiteralPath $nodeModulesDir)) {
        Write-Host "==> 未检测到 node_modules，先安装前端依赖" -ForegroundColor Yellow
        & npm install
    }

    Write-Host "==> 启动前端（默认端口 5173）" -ForegroundColor Cyan
    & npm run dev -- --host 0.0.0.0
} finally {
    Pop-Location
}
