#!/bin/sh
# gradlew minimo: descarrega o Gradle indicado em gradle/wrapper/gradle-wrapper.properties e corre-o.
# (o Loom 1.14 precisa de Gradle 9.2+, o Gradle 8.x do GitHub nao serve)
set -e
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
URL=$(grep '^distributionUrl=' "$PROPS" | cut -d= -f2- | sed 's/\\//g')
NAME=$(basename "$URL" .zip)
DIR="${NAME%-bin}"
DEST="${GRADLE_USER_HOME:-$HOME/.gradle}/plegumo-gradle"
if [ ! -x "$DEST/$DIR/bin/gradle" ]; then
  mkdir -p "$DEST"
  echo "A descarregar $URL ..."
  curl -fsSL "$URL" -o "$DEST/$NAME.zip"
  unzip -q -o "$DEST/$NAME.zip" -d "$DEST"
  rm -f "$DEST/$NAME.zip"
fi
exec "$DEST/$DIR/bin/gradle" "$@"
