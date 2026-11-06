#!/bin/bash
set -e

IDEA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
JAVAC="$IDEA_HOME/bin/javac"
IDEA_LIB="/Applications/IntelliJ IDEA.app/Contents/lib"

# JBR 里没有 jar 工具，用系统 JDK 的；找不到就直接用 zip
JAR="$IDEA_HOME/bin/jar"
if [ ! -x "$JAR" ]; then
  JAR="$(/usr/libexec/java_home -v 17 2>/dev/null)/bin/jar"
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/build"
DIST="$OUT/dist/sftp-quick-upload"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$DIST/lib"

CP=""
for j in "$IDEA_LIB"/*.jar; do CP="$CP$j:"; done
for j in "$ROOT"/lib/*.jar; do CP="$CP$j:"; done

find "$ROOT/src" -name "*.java" > "$OUT/sources.txt"
echo "编译 $(wc -l < "$OUT/sources.txt" | tr -d ' ') 个源文件..."
"$JAVAC" --release 21 -nowarn -encoding UTF-8 -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"

cp -R "$ROOT/src/META-INF" "$OUT/classes/"

"$JAR" --create --file "$DIST/lib/sftp-quick-upload.jar" -C "$OUT/classes" .
cp "$ROOT"/lib/*.jar "$DIST/lib/"

cd "$OUT/dist"
zip -qr "$OUT/sftp-quick-upload-1.0.0.zip" sftp-quick-upload

echo "打包完成: $OUT/sftp-quick-upload-1.0.0.zip"
ls -lh "$OUT/sftp-quick-upload-1.0.0.zip"
