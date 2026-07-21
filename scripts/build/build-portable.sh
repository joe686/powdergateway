#!/usr/bin/env bash
# REL-1 · 便携版组装脚本
#
# 用法：
#   scripts/build/build-portable.sh --platform win-x64  --version 0.1.0
#   scripts/build/build-portable.sh --platform linux-x64 --version 0.1.0
#
# 前置：
#   - JDK 17（用于 jlink 裁剪 JRE，必须与目标平台一致 —— 打 Win 版需在 Win JDK 上跑，不能跨平台）
#   - Maven 3.6+
#   - Node.js 18 + npm
#
# 产出：dist/powergateway-portable-{platform}-{version}.zip

set -euo pipefail

PLATFORM=""
VERSION=""
WITH_TESTKIT=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --platform) PLATFORM="$2"; shift 2 ;;
        --version) VERSION="$2"; shift 2 ;;
        --with-testkit) WITH_TESTKIT=true; shift ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

if [[ -z "$PLATFORM" || -z "$VERSION" ]]; then
    echo "用法：$0 --platform <win-x64|linux-x64> --version <ver> [--with-testkit]"
    exit 1
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="$ROOT/dist"
STAGE="$DIST/staging/portable-$PLATFORM-$VERSION"

echo "==== REL-1 便携版组装 ===="
echo "平台: $PLATFORM   版本: $VERSION   含 pg-testkit: $WITH_TESTKIT"
echo "工作根目录: $ROOT"

# ── 1. 清理 stage 目录 ────────────────────────────────
rm -rf "$STAGE"
mkdir -p "$STAGE/backend" "$STAGE/data/h2" "$STAGE/data/logs"

# ── 2. 前端 build ─────────────────────────────────────
echo "==== [1/5] 前端 build ===="
cd "$ROOT/frontend"
if [[ ! -d node_modules ]]; then
    npm ci
fi
npm run build

# 把前端 dist 复制到 backend static 供 fat-jar 打包
mkdir -p "$ROOT/backend/src/main/resources/static"
cp -r "$ROOT/frontend/dist/"* "$ROOT/backend/src/main/resources/static/"

# ── 3. 后端 build fat-jar（激活 portable profile 带 H2） ────────
echo "==== [2/5] 后端 build fat-jar (profile=portable) ===="
cd "$ROOT/backend"
mvn -Pportable clean package -DskipTests
cp "$ROOT/backend/target/backend-1.0.0-SNAPSHOT.jar" "$STAGE/backend/powergateway.jar"

# lib/ 目录预放 MySQL 驱动（可选，便携版默认 H2，用户加 MySQL 时无需再下）
mkdir -p "$STAGE/backend/lib"
cat > "$STAGE/backend/lib/README-drivers.txt" <<'EOF'
本目录用于存放额外的 JDBC 驱动 jar：
  - MySQL:      mysql-connector-j-8.0.33.jar（已默认打包在 fat-jar 里）
  - Oracle:     ojdbc11 / ojdbc8 —— 因 License 限制不预置，用户自行到 Oracle 官网下载放入本目录
  - OceanBase:  oceanbase-client —— 同上
启动脚本 start.bat/start.sh 已用 -Dloader.path 参数加载本目录
EOF

# ── 4. jlink 裁剪 JRE ─────────────────────────────────
echo "==== [3/5] jlink 裁剪 JRE ===="
if ! command -v jlink &>/dev/null; then
    echo "警告：找不到 jlink 命令。请安装 JDK 17 并确保 jlink 在 PATH，或跳过内嵌 JRE 步骤"
    echo "跳过 JRE 裁剪 —— 便携包将依赖用户环境的 Java"
else
    "$ROOT/scripts/build/jlink-jre.sh" --output "$STAGE/jre" || echo "警告：jlink 失败，继续"
fi

# ── 5. 复制启动脚本模板 + README ─────────────────────
echo "==== [4/5] 复制启动脚本 + README ===="
TEMPLATES="$ROOT/scripts/build/package-templates"
if [[ "$PLATFORM" == "win-x64" ]]; then
    cp "$TEMPLATES/portable-start.bat" "$STAGE/start.bat"
    cp "$TEMPLATES/portable-stop.bat" "$STAGE/stop.bat"
    cp "$TEMPLATES/open-console.bat" "$STAGE/open-console.bat"
else
    cp "$TEMPLATES/portable-start.sh" "$STAGE/start.sh"
    cp "$TEMPLATES/portable-stop.sh" "$STAGE/stop.sh"
    chmod +x "$STAGE/start.sh" "$STAGE/stop.sh"
fi
cp "$TEMPLATES/README-portable.txt" "$STAGE/README.txt"

# 可选：pg-testkit
if [[ "$WITH_TESTKIT" == true ]]; then
    echo "==== [4.5] 打包 pg-testkit（内部版专用） ===="
    cd "$ROOT/pg-testkit"
    mvn clean package -DskipTests
    mkdir -p "$STAGE/pg-testkit"
    cp "$ROOT/pg-testkit/target/pg-testkit-1.0.0-SNAPSHOT.jar" "$STAGE/pg-testkit/pg-testkit.jar"
    cp -r "$ROOT/pg-testkit/data" "$STAGE/pg-testkit/data" 2>/dev/null || mkdir -p "$STAGE/pg-testkit/data"
fi

# ── 6. 打 zip ─────────────────────────────────────────
echo "==== [5/5] 打 zip ===="
NAME_PREFIX=$([[ "$WITH_TESTKIT" == true ]] && echo "INTERNAL-powergateway-portable" || echo "powergateway-portable")
ZIP_NAME="${NAME_PREFIX}-${PLATFORM}-${VERSION}.zip"

# 选 python 解释器 —— Windows Store 的 python3 stub 会跳到应用商店无法用，
# 优先探测真实 python 命令：先 python，后 python3；且验证 --version 是否是可用的解释器
choose_python() {
    for candidate in python python3; do
        local path
        path=$(command -v "$candidate" 2>/dev/null) || continue
        # 排除 Windows Store 的 stub（位于 WindowsApps 且执行会跳应用商店）
        if [[ "$path" == *"WindowsApps"* ]]; then continue; fi
        # 验证是可用的 python
        if "$path" --version >/dev/null 2>&1; then
            echo "$path"; return 0
        fi
    done
    return 1
}
PYTHON=$(choose_python) || { echo "错误：未找到可用的 python 解释器"; exit 1; }

# Git Bash 上把 POSIX 路径转 Windows 风格，供 Windows Python 识别
to_native_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        echo "$1"
    fi
}
DIST_NATIVE=$(to_native_path "$DIST")
STAGE_ROOT_NATIVE=$(to_native_path "$DIST/staging")

# 直接用 stage 目录名打包
STAGE_NAME="portable-$PLATFORM-$VERSION"
"$PYTHON" -c "
import shutil
shutil.make_archive(r'${DIST_NATIVE}\\${ZIP_NAME%.zip}', 'zip', r'${STAGE_ROOT_NATIVE}', r'${STAGE_NAME}')
print('产物: ${DIST_NATIVE}\\${ZIP_NAME}')
"

# 计算 sha256
if command -v shasum &>/dev/null; then
    shasum -a 256 "$DIST/$ZIP_NAME" > "$DIST/${ZIP_NAME}.sha256"
elif command -v sha256sum &>/dev/null; then
    sha256sum "$DIST/$ZIP_NAME" > "$DIST/${ZIP_NAME}.sha256"
fi

echo ""
echo "==== 完成 ===="
ls -la "$DIST/$ZIP_NAME"* 2>&1 | head -3
