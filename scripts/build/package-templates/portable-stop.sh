#!/usr/bin/env bash
# PowerGateway 便携版停止脚本

echo "正在停止 PowerGateway 便携版..."
PID=$(lsof -ti:8080 2>/dev/null || netstat -tnlp 2>/dev/null | grep :8080 | awk '{print $7}' | cut -d/ -f1)
if [[ -n "$PID" ]]; then
    kill -TERM $PID
    echo "已停止进程 PID: $PID"
else
    echo "未发现监听 8080 端口的进程"
fi
