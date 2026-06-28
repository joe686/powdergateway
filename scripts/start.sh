#!/usr/bin/env bash
# PowerGateway 一键启动脚本（Git Bash 版）
# 后台启动 backend(8080) / frontend(5173) / pg-testkit(8081, mock 9999)
# 日志写入 logs/，PID 写入 logs/*.pid，配合 stop.sh 使用。

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT/logs"
mkdir -p "$LOG_DIR"

ok=true

check_cmd() {
    local exe="$1" label="$2"
    if command -v "$exe" >/dev/null 2>&1; then
        echo "[OK]   $label 可用"
    else
        echo "[缺失] 未找到命令: $label ($exe)"
        ok=false
    fi
}

# Git Bash 下 netstat 来自 Windows，匹配 LISTENING
port_listening() {
    local port="$1"
    netstat -ano 2>/dev/null | grep -E ":${port}\b.*LISTENING" >/dev/null
}

check_dep_port() {
    local port="$1" label="$2"
    if port_listening "$port"; then
        echo "[OK]   $label 端口 $port 在监听"
    else
        echo "[缺失] 端口 $port 未在监听，请确认 $label 已启动"
        ok=false
    fi
}

# ensure_service PORT LABEL SERVICE_NAME -- 端口未监听则 net start，最多等 10s
ensure_service() {
    local port="$1" label="$2" service="$3"
    if port_listening "$port"; then
        echo "[OK]   $label 端口 $port 在监听"
        return 0
    fi
    echo "[起服] $label 端口未监听，尝试 net start $service ..."
    net start "$service" >/dev/null 2>&1
    local i=0
    while [ $i -lt 10 ]; do
        sleep 1
        if port_listening "$port"; then
            echo "[OK]   $label 已启动，监听 $port"
            return 0
        fi
        i=$((i+1))
    done
    echo "[FAIL] $label 仍未监听 $port。请以管理员身份重跑脚本，或手动: net start $service"
    ok=false
    return 1
}

check_free_port() {
    local port="$1" label="$2"
    if port_listening "$port"; then
        echo "[占用] $label 端口已被占用，请先停止旧进程或运行 scripts/stop.sh"
        ok=false
    else
        echo "[OK]   $label 端口空闲"
    fi
}

echo '====== PowerGateway 启动检查 ======'
check_cmd java 'JDK'
check_cmd mvn  'Maven'
check_cmd node 'Node.js'
check_cmd npm  'npm'
ensure_service 3306 'MySQL' 'MySQL80'
ensure_service 6379 'Redis' 'Redis'

if [ ! -d "$ROOT/frontend/node_modules" ]; then
    echo '[缺失] frontend/node_modules 不存在，请先在 frontend 目录执行: npm install'
    ok=false
fi

check_free_port 8080 'backend 8080'
check_free_port 5173 'frontend 5173'
check_free_port 8081 'pg-testkit 8081'
check_free_port 9999 'pg-testkit mock 9999'

if [ "$ok" != true ]; then
    echo '启动中止。'
    exit 1
fi

echo '====== 检查通过，开始拉起服务 ======'

start_service() {
    local name="$1" workdir="$2"; shift 2
    local log="$LOG_DIR/${name}.log"
    : > "$log"
    (
        cd "$workdir" || exit 1
        # 用 nohup 避免父 shell 退出时被挂掉；输出全部丢日志
        nohup "$@" >"$log" 2>&1 &
        echo $! > "$LOG_DIR/${name}.pid"
    )
    echo "[起服] $name PID=$(cat "$LOG_DIR/${name}.pid") 日志=$log"
}

# Git Bash 下 mvn/npm 实际是 .cmd，需要走 cmd //c 才能正确解析
start_service backend    "$ROOT/backend"    cmd //c "mvn spring-boot:run"
start_service frontend   "$ROOT/frontend"   cmd //c "npm run dev"
start_service pg-testkit "$ROOT/pg-testkit" cmd //c "mvn spring-boot:run"

echo ''
echo "三个服务已后台启动，日志见 $LOG_DIR/*.log"
echo 'backend  : http://localhost:8080'
echo 'frontend : http://localhost:5173'
echo 'testkit  : http://localhost:8081  (mock: 9999)'
echo '停服请执行: scripts/stop.sh'
