#!/usr/bin/env bash
# REL-1 · 标准版组装脚本（前后端分离）
#
# 用法：scripts/build/build-standard.sh --version 0.1.0
#
# 产出：dist/powergateway-standard-{version}.zip
#   包含：
#     backend/powergateway-backend.jar
#     backend/config/application-prod.yml.example
#     backend/lib/{MySQL 驱动预置；Oracle/OceanBase 由用户放入}
#     backend/scripts/install-schema-{mysql,oracle,oceanbase}.sh + start.sh/start.bat
#     backend/init-sql/init-{mysql,oracle,oceanbase}.sql
#     frontend/dist/                前端静态资源，客户部署到 Nginx
#     frontend/nginx.conf.example
#     docs/部署手册.md + 常见问题.md + 数据库准备.md
#
# 前置：JDK 17 / Maven / Node.js 18

set -euo pipefail

VERSION=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --version) VERSION="$2"; shift 2 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

if [[ -z "$VERSION" ]]; then
    echo "用法：$0 --version <ver>"
    exit 1
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="$ROOT/dist"
STAGE="$DIST/staging/standard-$VERSION"

echo "==== REL-1 标准版组装 v$VERSION ===="

rm -rf "$STAGE"
mkdir -p "$STAGE/backend/config" "$STAGE/backend/lib" "$STAGE/backend/scripts" \
         "$STAGE/backend/init-sql" "$STAGE/frontend" "$STAGE/docs"

# ── 后端 build（默认 profile，无 H2） ────────────────
echo "==== [1/4] 后端 build ===="
cd "$ROOT/backend"
mvn clean package -DskipTests
cp "$ROOT/backend/target/backend-1.0.0-SNAPSHOT.jar" "$STAGE/backend/powergateway-backend.jar"

# lib/ 预置 MySQL 驱动（已在 fat-jar 内，此处是备份 & Oracle/OceanBase 说明）
cat > "$STAGE/backend/lib/README-drivers.txt" <<'EOF'
本目录用于存放额外的 JDBC 驱动 jar：
  - MySQL:      已包含在 fat-jar 内
  - Oracle:     ojdbc11 / ojdbc8 因 License 限制不预置，客户从 Oracle 官网下载放入
  - OceanBase:  oceanbase-client 类似
  - 达梦/金仓等国产库：放入对应厂商 JDBC 驱动 jar

启动脚本已用 -Dloader.path 参数加载本目录
EOF

# ── 前端 build ───────────────────────────────────────
echo "==== [2/4] 前端 build ===="
cd "$ROOT/frontend"
if [[ ! -d node_modules ]]; then
    npm ci
fi
npm run build
cp -r "$ROOT/frontend/dist" "$STAGE/frontend/"

# ── 模板 + 文档 ───────────────────────────────────────
echo "==== [3/4] 模板 + 文档 ===="
TEMPLATES="$ROOT/scripts/build/package-templates"
cp "$TEMPLATES/application-prod.yml.example" "$STAGE/backend/config/"
cp "$TEMPLATES/nginx.conf.example" "$STAGE/frontend/"
cp "$TEMPLATES/standard-start.sh" "$STAGE/backend/scripts/start.sh"
cp "$TEMPLATES/standard-start.bat" "$STAGE/backend/scripts/start.bat"
cp "$TEMPLATES/standard-stop.sh" "$STAGE/backend/scripts/stop.sh"
cp "$TEMPLATES/install-schema-mysql.sh" "$STAGE/backend/scripts/"
cp "$TEMPLATES/README-standard.md" "$STAGE/README.md"

chmod +x "$STAGE/backend/scripts/"*.sh 2>/dev/null || true

# 复制 DDL
cp "$ROOT/backend/src/main/resources/db/init.sql" "$STAGE/backend/init-sql/init-mysql.sql"
cp "$ROOT/backend/src/main/resources/db/init-audit.sql" "$STAGE/backend/init-sql/init-audit-mysql.sql" 2>/dev/null || true

# ── 打 zip ────────────────────────────────────────────
echo "==== [4/4] 打 zip ===="
ZIP_NAME="powergateway-standard-${VERSION}.zip"
# 优先探测真实 python（避开 Windows Store 的 stub）
choose_python() {
    for candidate in python python3; do
        local path
        path=$(command -v "$candidate" 2>/dev/null) || continue
        if [[ "$path" == *"WindowsApps"* ]]; then continue; fi
        if "$path" --version >/dev/null 2>&1; then echo "$path"; return 0; fi
    done
    return 1
}
PYTHON=$(choose_python) || { echo "错误：未找到可用的 python 解释器"; exit 1; }
to_native_path() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}
DIST_NATIVE=$(to_native_path "$DIST")
STAGE_ROOT_NATIVE=$(to_native_path "$DIST/staging")
"$PYTHON" -c "
import shutil
shutil.make_archive(r'${DIST_NATIVE}\\${ZIP_NAME%.zip}', 'zip', r'${STAGE_ROOT_NATIVE}', r'standard-$VERSION')
print('产物: ${DIST_NATIVE}\\${ZIP_NAME}')
"

if command -v shasum &>/dev/null; then
    shasum -a 256 "$DIST/$ZIP_NAME" > "$DIST/${ZIP_NAME}.sha256"
elif command -v sha256sum &>/dev/null; then
    sha256sum "$DIST/$ZIP_NAME" > "$DIST/${ZIP_NAME}.sha256"
fi

echo ""
echo "==== 完成 ===="
ls -la "$DIST/$ZIP_NAME"* 2>&1 | head -3
