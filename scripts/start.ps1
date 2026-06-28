#requires -Version 5.1
<#
  PowerGateway 一键启动脚本（PowerShell 版）
  后台启动 backend(8080) / frontend(5173) / pg-testkit(8081, mock 9999)
  日志写入 logs\，PID 写入 logs\*.pid，配合 stop.ps1 使用。
#>

$ErrorActionPreference = 'Stop'
$root   = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root 'logs'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

function Check-Cmd($exe, $label) {
    if (Get-Command $exe -ErrorAction SilentlyContinue) {
        Write-Host "[OK]   $label 可用"
        return $true
    }
    Write-Host "[缺失] 未找到命令: $label ($exe)" -ForegroundColor Red
    return $false
}

function Test-PortListening($port) {
    $line = netstat -ano | Select-String -Pattern (":$port\s.*LISTENING") -Quiet
    return [bool]$line
}

function Check-Dep($port, $label) {
    if (Test-PortListening $port) {
        Write-Host "[OK]   $label 端口 $port 在监听"
        return $true
    }
    Write-Host "[缺失] 端口 $port 未在监听，请确认 $label 已启动" -ForegroundColor Red
    return $false
}

function Ensure-Service($port, $label, $service) {
    if (Test-PortListening $port) {
        Write-Host "[OK]   $label 端口 $port 在监听"
        return $true
    }
    Write-Host "[起服] $label 端口未监听，尝试 net start $service ..."
    & net start $service 2>$null | Out-Null
    for ($i = 0; $i -lt 10; $i++) {
        Start-Sleep -Seconds 1
        if (Test-PortListening $port) {
            Write-Host "[OK]   $label 已启动，监听 $port"
            return $true
        }
    }
    Write-Host "[FAIL] $label 仍未监听 $port。请以管理员身份重跑脚本，或手动: net start $service" -ForegroundColor Red
    return $false
}

function Check-Free($port, $label) {
    if (-not (Test-PortListening $port)) {
        Write-Host "[OK]   $label 端口空闲"
        return $true
    }
    Write-Host "[占用] $label 端口已被占用，请先停止旧进程或运行 scripts\stop.ps1" -ForegroundColor Red
    return $false
}

Write-Host '====== PowerGateway 启动检查 ======'
$ok  = $true
$ok  = (Check-Cmd 'java' 'JDK')  -and $ok
$ok  = (Check-Cmd 'mvn'  'Maven') -and $ok
$ok  = (Check-Cmd 'node' 'Node.js') -and $ok
$ok  = (Check-Cmd 'npm'  'npm') -and $ok
$ok  = (Ensure-Service 3306 'MySQL' 'MySQL80') -and $ok
$ok  = (Ensure-Service 6379 'Redis' 'Redis')   -and $ok

if (-not (Test-Path (Join-Path $root 'frontend\node_modules'))) {
    Write-Host '[缺失] frontend\node_modules 不存在，请先在 frontend 目录执行: npm install' -ForegroundColor Red
    $ok = $false
}

$ok = (Check-Free 8080 'backend 8080') -and $ok
$ok = (Check-Free 5173 'frontend 5173') -and $ok
$ok = (Check-Free 8081 'pg-testkit 8081') -and $ok
$ok = (Check-Free 9999 'pg-testkit mock 9999') -and $ok

if (-not $ok) {
    Write-Host '启动中止。' -ForegroundColor Red
    exit 1
}

Write-Host '====== 检查通过，开始拉起服务 ======'

function Start-Service($name, $workDir, $exe, $argList, $logFile) {
    $log = Join-Path $logDir $logFile
    if (Test-Path $log) { Remove-Item $log -Force }
    $p = Start-Process -FilePath $exe `
                       -ArgumentList $argList `
                       -WorkingDirectory $workDir `
                       -RedirectStandardOutput $log `
                       -RedirectStandardError  ("$log.err") `
                       -WindowStyle Hidden `
                       -PassThru
    $p.Id | Set-Content -Encoding ASCII -Path (Join-Path $logDir "$name.pid")
    Write-Host ("[起服] {0} PID={1} 日志={2}" -f $name, $p.Id, $log)
}

# Windows 上 mvn / npm 实际是 .cmd，必须经 cmd.exe 启动才能正确解析
Start-Service 'backend'   (Join-Path $root 'backend')   $env:ComSpec '/c mvn spring-boot:run'  'backend.log'
Start-Service 'frontend'  (Join-Path $root 'frontend')  $env:ComSpec '/c npm run dev'          'frontend.log'
Start-Service 'pg-testkit' (Join-Path $root 'pg-testkit') $env:ComSpec '/c mvn spring-boot:run' 'pg-testkit.log'

Write-Host ''
Write-Host "三个服务已后台启动，日志见 $logDir\*.log"
Write-Host 'backend  : http://localhost:8080'
Write-Host 'frontend : http://localhost:5173'
Write-Host 'testkit  : http://localhost:8081  (mock: 9999)'
Write-Host '停服请执行: scripts\stop.ps1'
