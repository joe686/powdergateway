#!/usr/bin/env bash
# 在指定 MySQL 上初始化 PowerGateway 配置库 + 审计库
#
# 用法：install-schema-mysql.sh <host> <port> <user> <password> [config_db_name] [audit_db_name]

set -e

HOST="${1:-localhost}"
PORT="${2:-3306}"
USER="${3:-root}"
PASS="${4:?}"
CONFIG_DB="${5:-powergateway_config}"
AUDIT_DB="${6:-powergateway_audit}"

BASE="$(cd "$(dirname "$0")/.." && pwd)"
SQL_DIR="$BASE/init-sql"

echo "==== 建库 ===="
mysql -h "$HOST" -P "$PORT" -u "$USER" -p"$PASS" -e "
CREATE DATABASE IF NOT EXISTS $CONFIG_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS $AUDIT_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
"

echo "==== 初始化配置库 ===="
mysql -h "$HOST" -P "$PORT" -u "$USER" -p"$PASS" "$CONFIG_DB" < "$SQL_DIR/init-mysql.sql"

if [[ -f "$SQL_DIR/init-audit-mysql.sql" ]]; then
    echo "==== 初始化审计库 ===="
    mysql -h "$HOST" -P "$PORT" -u "$USER" -p"$PASS" "$AUDIT_DB" < "$SQL_DIR/init-audit-mysql.sql"
fi

echo "==== 完成 ===="
