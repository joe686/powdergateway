#!/usr/bin/env bash
# PowerGateway 一键停止脚本（Git Bash 版）
# 优先用 logs/*.pid 终止进程树，再按端口兜底清理。

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT/logs"

stop_by_pid() {
    local name="$1"
    local pid_file="$LOG_DIR/${name}.pid"
    [ -f "$pid_file" ] || return 0
    local pid_value
    pid_value="$(head -n1 "$pid_file" | tr -d '[:space:]')"
    if [ -n "$pid_value" ]; then
        # Git Bash 启动的子进程是 Windows 进程，用 taskkill 终止整棵进程树
        taskkill //PID "$pid_value" //T //F >/dev/null 2>&1
        echo "[stop] $name PID=$pid_value"
    fi
    rm -f "$pid_file"
}

stop_by_port() {
    local label="$1" port="$2"
    local pids
    pids="$(netstat -ano 2>/dev/null | grep -E ":${port}\b.*LISTENING" | awk '{print $NF}' | sort -u)"
    for pid in $pids; do
        if [[ "$pid" =~ ^[0-9]+$ ]]; then
            taskkill //PID "$pid" //T //F >/dev/null 2>&1
            echo "[stop] $label port=$port PID=$pid"
        fi
    done
}

stop_by_pid backend
stop_by_pid frontend
stop_by_pid pg-testkit

stop_by_port backend      8080
stop_by_port frontend     5173
stop_by_port pg-testkit   8081
stop_by_port testkit-mock 9999

echo '停止完成。'
