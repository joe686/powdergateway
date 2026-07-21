#!/usr/bin/env bash
BASE="$(cd "$(dirname "$0")/.." && pwd)"

if [[ ! -f "$BASE/powergateway.pid" ]]; then
    echo "未找到 PID 文件，尝试按端口 kill"
    PID=$(lsof -ti:8080 2>/dev/null)
    if [[ -n "$PID" ]]; then
        kill -TERM $PID
        echo "已停止进程 PID: $PID"
    fi
    exit 0
fi

PID=$(cat "$BASE/powergateway.pid")
kill -TERM $PID 2>/dev/null && echo "已发送 TERM 信号到 PID: $PID"
rm -f "$BASE/powergateway.pid"
