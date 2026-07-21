#!/usr/bin/env bash
# REL-1 · jlink 裁剪 JRE 17
#
# 用法：scripts/build/jlink-jre.sh --output <dir>
# 产出：约 40MB 的 JRE 目录（不含 debug/man 文档，已 compress=2）
#
# 注意：jlink 不支持跨平台裁剪 —— 打 Win 版需在 Win JDK 上跑，Linux 版需在 Linux JDK 上跑

set -euo pipefail

OUTPUT=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output) OUTPUT="$2"; shift 2 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

if [[ -z "$OUTPUT" ]]; then
    echo "用法：$0 --output <output_dir>"
    exit 1
fi

if ! command -v jlink &>/dev/null; then
    echo "错误：找不到 jlink 命令，请确认 JDK 17 已安装且在 PATH"
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "错误：JAVA_HOME 未设置"
    exit 1
fi

rm -rf "$OUTPUT"
jlink \
    --module-path "$JAVA_HOME/jmods" \
    --add-modules java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.httpserver,jdk.jdwp.agent,jdk.jfr,jdk.localedata,jdk.management,jdk.management.agent,jdk.naming.dns,jdk.naming.rmi,jdk.net,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.zipfs \
    --output "$OUTPUT" \
    --strip-debug \
    --compress=2 \
    --no-header-files \
    --no-man-pages

echo "==== jlink 完成 ===="
du -sh "$OUTPUT"
