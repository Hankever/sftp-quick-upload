#!/bin/bash
# 一键下载某个 IDEA 版本的平台 jar（只要 lib/*.jar），用于编译插件。
# 用法：fetch-idea.sh <IDEA 版本> <目标目录>
set -e

VERSION="${1:?用法: fetch-idea.sh <版本，例如 2021.1> <目标目录>}"
DEST="${2:?用法: fetch-idea.sh <版本> <目标目录>}"

if [ -d "$DEST/lib" ] && [ -n "$(ls -A "$DEST/lib" 2>/dev/null)" ]; then
  echo "已缓存: $DEST/lib（$(ls "$DEST"/lib/*.jar | wc -l | tr -d ' ') 个 jar）"
  exit 0
fi

URL="https://download.jetbrains.com/idea/ideaIC-${VERSION}.tar.gz"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "下载 $URL ..."
curl -fsSL --retry 3 -o "$TMP/idea.tar.gz" "$URL"

mkdir -p "$TMP/x" "$DEST"
tar -xzf "$TMP/idea.tar.gz" -C "$TMP/x"
# 只留平台 jar：javac 用本地/CI 的 JDK，jbr 和 plugins 都不要
mv "$TMP"/x/*/lib "$DEST/lib"

echo "已就绪: $DEST/lib（$(ls "$DEST"/lib/*.jar | wc -l | tr -d ' ') 个 jar）"
