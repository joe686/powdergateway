#requires -Version 5.1
<#
  PowerGateway 一键停止脚本（PowerShell 版）
  优先用 logs\*.pid 终止进程树，再按端口兜底清理。
#>

$ErrorActionPreference = 'SilentlyContinue'
$root   = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root 'logs'

function Stop-ByPid($name) {
    $pidFile = Join-Path $logDir "$name.pid"
    if (-not (Test-Path $pidFile)) { return }
    $pidValue = (Get-Content $pidFile | Select-Object -First 1).Trim()
    if ($pidValue) {
        # 终止进程树（包括 mvn/npm 拉起来的子进程）
        & taskkill /PID $pidValue /T /F | Out-Null
        Write-Host "[stop] $name PID=$pidValue"
    }
    Remove-Item $pidFile -Force
}

function Stop-ByPort($label, $port) {
    $rows = netstat -ano | Select-String -Pattern (":$port\s.*LISTENING")
    foreach ($row in $rows) {
        $procId = ($row.ToString().Trim() -split '\s+')[-1]
        if ($procId -match '^\d+$') {
            & taskkill /PID $procId /T /F | Out-Null
            Write-Host "[stop] $label port=$port PID=$procId"
        }
    }
}

Stop-ByPid 'backend'
Stop-ByPid 'frontend'
Stop-ByPid 'pg-testkit'

# 端口兜底（防止 PID 文件丢失或孙子进程残留）
Stop-ByPort 'backend'      8080
Stop-ByPort 'frontend'     5173
Stop-ByPort 'pg-testkit'   8081
Stop-ByPort 'testkit-mock' 9999

Write-Host '停止完成。'
