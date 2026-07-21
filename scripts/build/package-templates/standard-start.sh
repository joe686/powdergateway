#!/usr/bin/env bash
# PowerGateway 标准版启动脚本

set -e

BASE="$(cd "$(dirname "$0")/.." && pwd)"

if [[ ! -f "$BASE/config/application-prod.yml" ]]; then
    echo "错误：$BASE/config/application-prod.yml 不存在"
    echo "请先复制 application-prod.yml.example 为 application-prod.yml 并填写数据库连接信息"
    exit 1
fi

JVM_OPTS="${JVM_OPTS:--Xms512m -Xmx2g -Dfile.encoding=UTF-8}"
LOG_DIR="${LOG_DIR:-$BASE/logs}"
mkdir -p "$LOG_DIR"

# Git Bash 场景下把 POSIX 路径转 Windows 风格，让 Windows JVM 识别
to_native() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}
BASE_NATIVE=$(to_native "$BASE")

echo "================================================"
echo " PowerGateway 标准版启动中..."
echo " 配置文件: $BASE/config/application-prod.yml"
echo " 日志目录: $LOG_DIR"
echo "================================================"

nohup java $JVM_OPTS \
    -Dspring.profiles.active=prod \
    -Dspring.config.location="file:$BASE_NATIVE/config/application-prod.yml" \
    -Dloader.path="$BASE_NATIVE/lib" \
    -jar "$BASE/powergateway-backend.jar" \
    > "$LOG_DIR/powergateway.log" 2>&1 &

echo "$!" > "$BASE/powergateway.pid"
echo "PID: $(cat $BASE/powergateway.pid)"
echo "日志: tail -f $LOG_DIR/powergateway.log"
