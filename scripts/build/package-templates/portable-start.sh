#!/usr/bin/env bash
# PowerGateway 便携版启动脚本

set -e

BASE="$(cd "$(dirname "$0")" && pwd)"
JAVA="$BASE/jre/bin/java"

if [[ ! -x "$JAVA" ]]; then
    echo "[WARN] 内嵌 JRE 未找到，尝试使用系统 Java"
    JAVA=java
fi

echo "================================================"
echo " PowerGateway 便携版启动中..."
echo " 数据目录: $BASE/data"
echo " 访问地址: http://localhost:8080"
echo " 默认账号: admin / Admin@123"
echo " H2 控制台: http://localhost:8080/h2-console"
echo "================================================"
echo ""
echo " Ctrl+C 停止服务"
echo ""

exec "$JAVA" -Xms256m -Xmx1g -Dfile.encoding=UTF-8 \
    -Dspring.profiles.active=standalone \
    -Dloader.path="$BASE/backend/lib" \
    -jar "$BASE/backend/powergateway.jar"
