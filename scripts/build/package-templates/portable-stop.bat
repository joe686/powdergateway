@echo off
chcp 65001 > nul
echo 正在停止 PowerGateway 便携版...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a
    echo 已停止进程 PID: %%a
)
pause
