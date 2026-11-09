#!/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

# 编译平台：默认 2021.1（build 211）。
# 这是“兼容新老 IDEA”的关键——对着最老的平台编译，就不可能用到 2022+ 才有的 API，
# 产物声明 since-build=211 之后，2021.1 到 2025.3 都能装。
# 想用本机安装的 IDEA 快速试编，用 IDEA_VERSION=local（产物不保证兼容旧版）。
IDEA_VERSION="${IDEA_VERSION:-2021.1}"
# 字节码版本：2021.1 跑在 JBR 11 上，必须是 11
RELEASE_LEVEL="${RELEASE_LEVEL:-11}"

if [ -n "${IDEA_HOME:-}" ]; then
  : # 显式指定平台目录（CI 用这个）
elif [ "$IDEA_VERSION" = "local" ]; then
  IDEA_HOME="/Applications/IntelliJ IDEA.app/Contents"
else
  IDEA_HOME="$ROOT/.idea-dist/idea-IC-$IDEA_VERSION"
  if [ ! -d "$IDEA_HOME/lib" ]; then
    bash "$ROOT/scripts/fetch-idea.sh" "$IDEA_VERSION" "$IDEA_HOME"
  fi
fi

IDEA_LIB="$IDEA_HOME/lib"
if [ ! -d "$IDEA_LIB" ]; then
  echo "找不到 IDEA 平台 jar 目录: $IDEA_LIB" >&2
  echo "用 IDEA_HOME 指定，或删掉 .idea-dist 让它重新下载" >&2
  exit 1
fi

if ! command -v zip >/dev/null 2>&1; then
  echo "找不到 zip 命令" >&2
  exit 1
fi

# javac：优先 IDE 自带的 JBR，其次 PATH / 系统 JDK，但必须 >= RELEASE_LEVEL
pick_javac() {
  for c in \
    "$IDEA_HOME/jbr/Contents/Home/bin/javac" \
    "$IDEA_HOME/jbr/bin/javac" \
    "$(command -v javac 2>/dev/null || true)" \
    "$(/usr/libexec/java_home -v 21 2>/dev/null || true)/bin/javac" \
    "$(/usr/libexec/java_home -v 17 2>/dev/null || true)/bin/javac" \
    "$(/usr/libexec/java_home -v 11 2>/dev/null || true)/bin/javac"; do
    [ -n "$c" ] && [ -x "$c" ] || continue
    local major
    major="$("$c" -version 2>&1 | sed -n 's/^javac \([0-9]\{1,\}\).*/\1/p')"
    if [ -n "$major" ] && [ "$major" -ge "$RELEASE_LEVEL" ]; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

JAVAC="$(pick_javac || true)"
if [ -z "$JAVAC" ]; then
  echo "找不到 JDK ${RELEASE_LEVEL}+ 的 javac" >&2
  exit 1
fi

OUT="$ROOT/build"
DIST="$OUT/dist/sftp-quick-upload"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$ROOT/src/META-INF/plugin.xml" | head -1)"
ZIP="$OUT/sftp-quick-upload-$VERSION.zip"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$DIST/lib"

CP=""
for j in "$IDEA_LIB"/*.jar; do CP="$CP$j:"; done
for j in "$ROOT"/lib/*.jar; do CP="$CP$j:"; done

find "$ROOT/src" -name "*.java" > "$OUT/sources.txt"
echo "编译 $(wc -l < "$OUT/sources.txt" | tr -d ' ') 个源文件 (release=$RELEASE_LEVEL)"
echo "  平台: $IDEA_LIB"
"$JAVAC" --release "$RELEASE_LEVEL" -nowarn -encoding UTF-8 -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"

cp -R "$ROOT/src/META-INF" "$OUT/classes/"

# jar 就是个 zip，用 zip 打可以避开本机 JDK 版本的坑
(cd "$OUT/classes" && zip -qr "$DIST/lib/sftp-quick-upload.jar" .)
cp "$ROOT"/lib/*.jar "$DIST/lib/"

cd "$OUT/dist"
zip -qr "$ZIP" sftp-quick-upload

echo "打包完成: $ZIP"
ls -lh "$ZIP"
