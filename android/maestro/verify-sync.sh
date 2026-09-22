#!/usr/bin/env bash
# Verifica que lo creado en el teléfono llega al servidor: corre sync-orden.yaml con nombres únicos y consulta
# PostgreSQL por cliente, producto, venta, ítem, parcialidades (3) y cobro (1).
# Uso: ./verify-sync.sh        (mismos prerrequisitos que run.sh)
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
DB="${SUMITRACK_DB:-sumitrack_01}"
PSQL="${PSQL:-/opt/homebrew/opt/postgresql@15/bin/psql}"
SLUG="${1:-local}"

"$ADB" devices | grep -q "device$" || { echo "✗ No hay dispositivo/emulador conectado."; exit 1; }
"$ADB" reverse tcp:5600 tcp:5600 >/dev/null
SCHEMA=$("$PSQL" -d "$DB" -Atc "select schema_name from public.tenants where slug = '$SLUG'")
[ -n "$SCHEMA" ] || { echo "✗ No existe el tenant '$SLUG'."; exit 1; }

STAMP=$(date +%s)
CLIENTE="Cliente Sync $STAMP"
PRODUCTO="Producto Sync $STAMP"
maestro test -e CLIENTE="$CLIENTE" -e PRODUCTO="$PRODUCTO" sync-orden.yaml

q() { "$PSQL" -d "$DB" -Atc "$1"; }
S="\"$SCHEMA\""
CID=$(q "select id from $S.clients where name = '$CLIENTE'")
check() { # nombre esperado obtenido
  if [ "$2" = "$3" ]; then echo "✓ $1: $3"; else echo "✗ $1: esperado $2, obtenido '$3'"; FAIL=1; fi
}
FAIL=0
check "clientes"       1 "$(q "select count(*) from $S.clients where name = '$CLIENTE'")"
check "productos"      1 "$(q "select count(*) from $S.products where name = '$PRODUCTO'")"
check "ventas"         1 "$(q "select count(*) from $S.sales where fk_client = '${CID:-00000000-0000-0000-0000-000000000000}'")"
check "ítems de venta" 1 "$(q "select count(*) from $S.sale_items i join $S.sales s on s.id = i.fk_sale where s.fk_client = '${CID:-00000000-0000-0000-0000-000000000000}'")"
check "parcialidades"  3 "$(q "select count(*) from $S.installments i join $S.sales s on s.id = i.fk_sale where s.fk_client = '${CID:-00000000-0000-0000-0000-000000000000}'")"
check "cobros"         1 "$(q "select count(*) from $S.payments p join $S.sales s on s.id = p.fk_sale where s.fk_client = '${CID:-00000000-0000-0000-0000-000000000000}'")"
exit $FAIL
