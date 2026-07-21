#!/usr/bin/env bash
# REL-1 · 冒烟测试：解压产物 → 启动 → curl 健康检查 → 停止
#
# 用法：verify-artifacts.sh <zip 路径>

set -euo pipefail

ZIP="${1:?用法：$0 <zip 路径>}"

if [[ ! -f "$ZIP" ]]; then
    echo "错误：产物不存在: $ZIP"
    exit 1
fi

TMP=$(mktemp -d)
echo "==== 解压到 $TMP ===="
if command -v unzip &>/dev/null; then
    unzip -q "$ZIP" -d "$TMP"
else
    python3 -c "import zipfile; zipfile.ZipFile('$ZIP').extractall('$TMP')"
fi

# 找到解压后的顶层目录（首个子目录）
STAGE=$(find "$TMP" -mindepth 1 -maxdepth 1 -type d | head -1)
if [[ -z "$STAGE" ]]; then
    echo "错误：解压后未找到目录"
    exit 1
fi
echo "解压根: $STAGE"

# 判断是便携版还是标准版（便携有 start.sh 在根，标准在 backend/scripts/）
if [[ -f "$STAGE/start.sh" ]]; then
    START="$STAGE/start.sh"
    IS_PORTABLE=true
elif [[ -f "$STAGE/backend/scripts/start.sh" ]]; then
    START="$STAGE/backend/scripts/start.sh"
    IS_PORTABLE=false
else
    echo "错误：找不到 start.sh"
    exit 1
fi

echo "==== 启动: $START ===="
chmod +x "$START"

if [[ "$IS_PORTABLE" == "true" ]]; then
    # 便携版 前台运行，后台跑
    nohup "$START" > "$TMP/app.log" 2>&1 &
    APP_PID=$!
else
    # 标准版 已经 nohup 后台
    "$START" > "$TMP/app.log" 2>&1 || true
fi

# 等待启动（最多 90s）
echo "==== 等待启动（最多 90s） ===="
for i in $(seq 1 45); do
    if curl -sf http://localhost:8080/actuator/health &>/dev/null; then
        echo "启动成功（$((i*2))s）"
        break
    fi
    sleep 2
done

# 验证 health 端点
echo "==== curl /actuator/health ===="
if ! curl -sf http://localhost:8080/actuator/health; then
    echo ""
    echo "错误：健康检查失败，日志尾部："
    tail -30 "$TMP/app.log"
    if [[ -n "${APP_PID:-}" ]]; then kill -TERM $APP_PID 2>/dev/null || true; fi
    exit 1
fi
echo ""

# 关闭
echo "==== 停止 ===="
if [[ -n "${APP_PID:-}" ]]; then
    kill -TERM $APP_PID 2>/dev/null || true
elif [[ -f "$STAGE/backend/scripts/stop.sh" ]]; then
    "$STAGE/backend/scripts/stop.sh"
fi
sleep 3

# 清理
rm -rf "$TMP"
echo "==== 冒烟测试通过 ===="
