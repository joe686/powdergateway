@echo off
setlocal EnableDelayedExpansion

REM PowerGateway one-shot stop (cmd / .bat)
REM Locates LISTENING pids via netstat and taskkill the whole tree.

set ROOT=%~dp0..
set LOG_DIR=%ROOT%\logs

call :kill_by_port backend       8080
call :kill_by_port frontend      5173
call :kill_by_port pg-testkit    8081
call :kill_by_port testkit-mock  9999

echo Stop done.
exit /b 0

:kill_by_port
set NAME=%~1
set PORT=%~2
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
    echo [stop] %NAME% PID=%%P (port %PORT%)
    taskkill /PID %%P /T /F >nul 2>nul
)
if exist "%LOG_DIR%\%NAME%.port" del "%LOG_DIR%\%NAME%.port"
exit /b 0
