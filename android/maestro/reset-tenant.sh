#!/usr/bin/env bash
# Borra los datos operativos del tenant de pruebas (clientes, productos, ventas, parcialidades, cobros,
# saldos a favor) en la BD local. NO toca usuarios, settings ni el registro del tenant.
# El estado del teléfono no hace falta reiniciarlo: los flujos arrancan con clearState.
# Uso: ./reset-tenant.sh [slug]      (default: local; pide confirmación)
set -euo pipefail

SLUG="${1:-local}"
DB="${SUMITRACK_DB:-sumitrack_01}"
PSQL="${PSQL:-/opt/homebrew/opt/postgresql@15/bin/psql}"

SCHEMA=$("$PSQL" -d "$DB" -Atc "select schema_name from public.tenants where slug = '$SLUG'")
[ -n "$SCHEMA" ] || { echo "✗ No existe el tenant '$SLUG' en $DB."; exit 1; }

read -r -p "Se borrarán los datos operativos del tenant '$SLUG' ($SCHEMA en $DB). ¿Continuar? [s/N] " ans
[ "$ans" = "s" ] || { echo "Cancelado."; exit 0; }

"$PSQL" -d "$DB" -v ON_ERROR_STOP=1 <<SQL
truncate table
  "$SCHEMA".payments, "$SCHEMA".installments, "$SCHEMA".sale_items, "$SCHEMA".sales,
  "$SCHEMA".credit_balances, "$SCHEMA".product_variants, "$SCHEMA".products, "$SCHEMA".clients
restart identity cascade;
SQL
echo "✓ Datos del tenant '$SLUG' reiniciados."
