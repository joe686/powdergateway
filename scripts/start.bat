@echo off
setlocal EnableDelayedExpansion

REM PowerGateway one-shot launcher (cmd / .bat)
REM Starts backend(8080) / frontend(5173) / pg-testkit(8081, mock 9999) in background.
REM Logs go to logs\, paired with stop.bat.

set ROOT=%~dp0..
set LOG_DIR=%ROOT%\logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ====== PowerGateway preflight ======

call :check_cmd java  JDK
if errorlevel 1 goto :fail
call :check_cmd mvn   Maven
if errorlevel 1 goto :fail
call :check_cmd node  Node.js
if errorlevel 1 goto :fail
call :check_cmd npm   npm
if errorlevel 1 goto :fail

call :ensure_service 3306 MySQL MySQL80
if errorlevel 1 goto :fail
call :ensure_service 6379 Redis Redis
if errorlevel 1 goto :fail

if not exist "%ROOT%\frontend\node_modules" (
    echo [MISS] frontend\node_modules not found. Run: cd frontend ^&^& npm install
    goto :fail
)

call :check_free_port 8080 "backend 8080"
if errorlevel 1 goto :fail
call :check_free_port 5173 "frontend 5173"
if errorlevel 1 goto :fail
call :check_free_port 8081 "pg-testkit 8081"
if errorlevel 1 goto :fail
call :check_free_port 9999 "pg-testkit mock 9999"
if errorlevel 1 goto :fail

echo ====== preflight OK, launching services ======

pushd "%ROOT%\backend"
start "" /B cmd /c "mvn spring-boot:run > "%LOG_DIR%\backend.log" 2>&1"
popd
echo 8080 > "%LOG_DIR%\backend.port"

pushd "%ROOT%\frontend"
start "" /B cmd /c "npm run dev > "%LOG_DIR%\frontend.log" 2>&1"
popd
echo 5173 > "%LOG_DIR%\frontend.port"

pushd "%ROOT%\pg-testkit"
start "" /B cmd /c "mvn spring-boot:run > "%LOG_DIR%\pg-testkit.log" 2>&1"
popd
echo 8081 > "%LOG_DIR%\pg-testkit.port"

echo.
echo Services started in background. Logs: %LOG_DIR%\*.log
echo   backend  : http://localhost:8080
echo   frontend : http://localhost:5173
echo   testkit  : http://localhost:8081  (mock: 9999)
echo To stop: scripts\stop.bat
exit /b 0

REM ---- helpers ----
REM IMPORTANT: never put a literal ')' inside an if/for block's echo -- cmd will
REM treat it as the block terminator. Use '^)' to escape or rephrase the message.

:check_cmd
where %1 >nul 2>nul
if errorlevel 1 (
    echo [MISS] command not found: %~2 [%1]
    exit /b 1
)
echo [ OK ] %~2
exit /b 0

:check_dep_port
netstat -ano | findstr /R /C:":%~1 .*LISTENING" >nul
if errorlevel 1 (
    echo [MISS] %~2 port %~1 not listening -- is %~2 running?
    exit /b 1
)
echo [ OK ] %~2 listening on %~1
exit /b 0

REM ensure_service PORT LABEL SERVICE_NAME -- try to net start the service if port idle
:ensure_service
netstat -ano | findstr /R /C:":%~1 .*LISTENING" >nul
if not errorlevel 1 (
    echo [ OK ] %~2 listening on %~1
    exit /b 0
)
echo [WARN] %~2 port %~1 not listening, trying: net start %~3
net start %~3 >nul 2>nul
REM wait up to 10 seconds for the port to come up
set /a _t=0
:_es_wait
netstat -ano | findstr /R /C:":%~1 .*LISTENING" >nul
if not errorlevel 1 (
    echo [ OK ] %~2 started, listening on %~1
    exit /b 0
)
set /a _t+=1
if !_t! GEQ 10 (
    echo [FAIL] %~2 still not listening on %~1. Start manually as admin: net start %~3
    exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto :_es_wait

:check_free_port
netstat -ano | findstr /R /C:":%~1 .*LISTENING" >nul
if errorlevel 1 (
    echo [ OK ] %~2 port free
    exit /b 0
)
echo [BUSY] %~2 port already in use. Run scripts\stop.bat first.
exit /b 1

:fail
echo.
echo Launch aborted.
exit /b 1
