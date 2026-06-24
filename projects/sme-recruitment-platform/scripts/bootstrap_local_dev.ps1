<#
文件速览：
1. 文件职责：在新机器上初始化本地开发数据库，导入仓库内置结构与最小演示数据。
2. 对外入口：仓库根目录执行 `powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap_local_dev.ps1`。
3. 关键结构：参数读取、MySQL 可用性检查、建库、导入结构脚本、导入种子脚本。
4. 阅读建议：先看 Param 默认值，再看底部“下一步提示”。
#>

[CmdletBinding()]
param(
    [string]$MysqlHost = "",
    [string]$MysqlPort = "",
    [string]$DatabaseName = "",
    [string]$MysqlUsername = "",
    [string]$MysqlPassword = "",
    [switch]$SkipSeed
)

$ErrorActionPreference = "Stop"

function Resolve-MySqlExecutable {
    try {
        return (Get-Command mysql -CommandType Application -ErrorAction Stop).Source
    } catch {
        try {
            $mysqldumpExecutable = (Get-Command mysqldump -CommandType Application -ErrorAction Stop).Source
            $mysqlCandidate = Join-Path (Split-Path -Path $mysqldumpExecutable -Parent) "mysql.exe"
            if (Test-Path -LiteralPath $mysqlCandidate) {
                return $mysqlCandidate
            }
        } catch {
            # 保留到统一报错中处理。
        }
    }

    throw "未找到 mysql 可执行文件。请确认 MySQL 客户端已安装，或将 mysql.exe 所在目录加入 PATH。"
}

function Resolve-ConfigValue {
    param(
        [string]$CliValue,
        [string]$EnvValue,
        [string]$DefaultValue
    )

    if (-not [string]::IsNullOrWhiteSpace($CliValue)) {
        return $CliValue
    }
    if (-not [string]::IsNullOrWhiteSpace($EnvValue)) {
        return $EnvValue
    }
    return $DefaultValue
}

function Join-ProcessArguments {
    param(
        [string[]]$Arguments
    )

    return ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + ($_ -replace '"', '\"') + '"'
        } else {
            $_
        }
    }) -join ' '
}

function Invoke-MySqlProcess {
    param(
        [string]$MysqlExecutable,
        [string[]]$Arguments,
        [string]$SqlText = ""
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $MysqlExecutable
    $startInfo.Arguments = Join-ProcessArguments -Arguments $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $null = $process.Start()

    if (-not [string]::IsNullOrEmpty($SqlText)) {
        $writer = New-Object System.IO.StreamWriter($process.StandardInput.BaseStream, (New-Object System.Text.UTF8Encoding($false)))
        $writer.Write($SqlText)
        $writer.Flush()
        $writer.Close()
    } else {
        $process.StandardInput.Close()
    }

    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if (-not [string]::IsNullOrWhiteSpace($standardOutput)) {
        Write-Host ($standardOutput.TrimEnd())
    }

    if ($process.ExitCode -ne 0) {
        if (-not [string]::IsNullOrWhiteSpace($standardError)) {
            Write-Host ($standardError.TrimEnd()) -ForegroundColor Red
        }
        throw "mysql 执行失败，退出码：$($process.ExitCode)"
    }

    if (-not [string]::IsNullOrWhiteSpace($standardError)) {
        Write-Host ($standardError.TrimEnd()) -ForegroundColor Yellow
    }
}

function Invoke-MySqlScriptFile {
    param(
        [string]$MysqlExecutable,
        [string]$SqlFilePath,
        [string]$MysqlHostValue,
        [string]$MysqlPortValue,
        [string]$Username,
        [string]$Password,
        [string]$DatabaseName
    )

    $sqlContent = Get-Content -LiteralPath $SqlFilePath -Raw -Encoding UTF8
    $connectionArguments = @(
        "--default-character-set=utf8mb4",
        "-h$MysqlHostValue",
        "-P$MysqlPortValue",
        "-u$Username"
    )
    if (-not [string]::IsNullOrEmpty($Password)) {
        $connectionArguments += "-p$Password"
    }
    $connectionArguments += $DatabaseName

    Invoke-MySqlProcess -MysqlExecutable $MysqlExecutable -Arguments $connectionArguments -SqlText $sqlContent
}

$resolvedHost = Resolve-ConfigValue -CliValue $MysqlHost -EnvValue $env:MYSQL_HOST -DefaultValue "localhost"
$resolvedPort = Resolve-ConfigValue -CliValue $MysqlPort -EnvValue $env:MYSQL_PORT -DefaultValue "3306"
$resolvedDatabase = Resolve-ConfigValue -CliValue $DatabaseName -EnvValue $env:MYSQL_DB -DefaultValue "sme_recruitment_db"
$resolvedUser = Resolve-ConfigValue -CliValue $MysqlUsername -EnvValue $env:MYSQL_USERNAME -DefaultValue "root"
$resolvedPassword = Resolve-ConfigValue -CliValue $MysqlPassword -EnvValue $env:MYSQL_PASSWORD -DefaultValue ""

$mysqlExecutable = Resolve-MySqlExecutable
$schemaFile = Join-Path $PSScriptRoot "bootstrap/schema_local_dev_20260402.sql"
$seedFile = Join-Path $PSScriptRoot "bootstrap/seed_local_dev_minimal_20260402.sql"

if (-not (Test-Path -LiteralPath $schemaFile)) {
    throw "未找到结构脚本：$schemaFile"
}

if (-not $SkipSeed -and -not (Test-Path -LiteralPath $seedFile)) {
    throw "未找到种子脚本：$seedFile"
}

Write-Host "==> 使用数据库配置：" -ForegroundColor Cyan
Write-Host "    Host     : $resolvedHost"
Write-Host "    Port     : $resolvedPort"
Write-Host "    Database : $resolvedDatabase"
Write-Host "    Username : $resolvedUser"

$createDatabaseSql = "CREATE DATABASE IF NOT EXISTS $resolvedDatabase DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
$createDatabaseArguments = @(
    "--default-character-set=utf8mb4",
    "-h$resolvedHost",
    "-P$resolvedPort",
    "-u$resolvedUser"
)
if (-not [string]::IsNullOrEmpty($resolvedPassword)) {
    $createDatabaseArguments += "-p$resolvedPassword"
}
$createDatabaseArguments += @("-e", $createDatabaseSql)
Invoke-MySqlProcess -MysqlExecutable $mysqlExecutable -Arguments $createDatabaseArguments

Write-Host "==> 导入结构基线：" -ForegroundColor Cyan
Write-Host "    $schemaFile"
Invoke-MySqlScriptFile -MysqlExecutable $mysqlExecutable -SqlFilePath $schemaFile -MysqlHostValue $resolvedHost -MysqlPortValue $resolvedPort -Username $resolvedUser -Password $resolvedPassword -DatabaseName $resolvedDatabase

if (-not $SkipSeed) {
    Write-Host "==> 导入最小演示数据：" -ForegroundColor Cyan
    Write-Host "    $seedFile"
    Invoke-MySqlScriptFile -MysqlExecutable $mysqlExecutable -SqlFilePath $seedFile -MysqlHostValue $resolvedHost -MysqlPortValue $resolvedPort -Username $resolvedUser -Password $resolvedPassword -DatabaseName $resolvedDatabase
} else {
    Write-Host "==> 已跳过种子数据导入（-SkipSeed）。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "数据库初始化完成。下一步建议：" -ForegroundColor Green
Write-Host "1. powershell -ExecutionPolicy Bypass -File .\\scripts\\start_backend_local.ps1"
Write-Host "2. powershell -ExecutionPolicy Bypass -File .\\scripts\\start_frontend_local.ps1"
Write-Host "3. 使用 admin1 / boss1 / app1，统一密码 12345 登录验证"
