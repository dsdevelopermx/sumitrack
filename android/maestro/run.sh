#!/usr/bin/env bash
# Corre los flujos de Maestro en el dispositivo/emulador conectado.
# Uso: ./run.sh                 (todos los flujos)
#      ./run.sh 01-cliente-alta.yaml   (uno solo)
set -euo pipefail
cd "$(dirname "$0")"

# Maestro necesita Java 17+; el JBR de Android Studio 2026.1 es JDK 25 (ver README del proyecto), así que se
# usa el openjdk@21 de Homebrew.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

"$ADB" devices | grep -q "device$" || { echo "✗ No hay dispositivo/emulador conectado (adb devices)."; exit 1; }
"$ADB" reverse tcp:5600 tcp:5600 >/dev/null
curl -s -o /dev/null http://localhost:5600/openapi/v1.json || {
  echo "✗ La API no responde en http://localhost:5600. Levántala con:"
  echo "  cd backend && dotnet run --project src/Sumitrack.Api --urls http://localhost:5600"
  exit 1
}

if [ $# -gt 0 ]; then maestro test "$@"; else maestro test 0*.yaml; fi
