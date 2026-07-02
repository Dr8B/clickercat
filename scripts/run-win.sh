#!/usr/bin/env bash
#
# Запуск ClickerCat нативно на Windows из WSL.
#
# Зачем: приложение — автокликер с глобальными хуками (jnativehook) и вводом
# через java.awt.Robot. Под WSLg всё это остаётся в виртуальном X-дисплее и на
# реальный рабочий стол Windows не влияет. Поэтому запускаем Windows-джаву
# (java.exe) через WSL-interop — тогда клики и хоткеи работают в самой Windows.
#
# Важный нюанс: jnativehook не умеет распаковывать свою .dll, когда jar лежит на
# UNC-пути (\\wsl.localhost\...). Поэтому jar копируется на локальный диск C:.
#
# Использование:
#   scripts/run-win.sh            # собрать (если jar устарел) и запустить
#   scripts/run-win.sh --build    # принудительно пересобрать (mvn clean package)
#   scripts/run-win.sh --debug    # запустить с JDWP на :5005 (suspend, ждёт отладчик)
#
set -euo pipefail

cd "$(dirname "$0")/.."

BUILD=0
DEBUG=0
for a in "$@"; do
  case "$a" in
    --build) BUILD=1 ;;
    --debug) DEBUG=1 ;;
    *) echo "Неизвестный аргумент: $a" >&2; exit 2 ;;
  esac
done

JAR="target/clickercat.jar"

# Пересобрать, если попросили или если jar отсутствует/устарел относительно исходников.
if [ "$BUILD" -eq 1 ] || [ ! -f "$JAR" ] || [ -n "$(find src pom.xml -newer "$JAR" 2>/dev/null | head -1)" ]; then
  echo ">> Сборка (mvn clean package)…"
  mvn clean package -q -DskipTests
fi

# java.exe с Windows должен быть в PATH (WSL-interop подхватывает Windows PATH).
if ! command -v java.exe >/dev/null 2>&1; then
  echo "Ошибка: java.exe (Windows JDK) не найден в PATH." >&2
  echo "Установи JDK на Windows и убедись, что его bin в системном PATH Windows." >&2
  exit 1
fi

# Копируем jar на локальный диск Windows (обязательно — иначе jnativehook падает на UNC).
WINTMP=$(cmd.exe /c "echo %TEMP%" 2>/dev/null | tr -d '\r')
DEST_U="$(wslpath -u "$WINTMP")/clickercat"
mkdir -p "$DEST_U"
cp "$JAR" "$DEST_U/"
WINJAR=$(wslpath -w "$DEST_U/clickercat.jar")

JVM_ARGS=(--enable-native-access=ALL-UNNAMED)
if [ "$DEBUG" -eq 1 ]; then
  echo ">> Дебаг: JDWP слушает :5005 (suspend=y). Подключись из VSCode «Attach»."
  JVM_ARGS+=(-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005)
fi

echo ">> Запуск на Windows: $WINJAR"
exec java.exe "${JVM_ARGS[@]}" -jar "$WINJAR"
