#!/bin/sh
# Uso (en el VPS, desde ~/tfg-db): ./scripts/crear-clave.sh <tenant_id> "<nombre del dispositivo>"
# Imprime la clave en claro UNA vez; en la base solo queda su SHA-256.
# La clave no aparece en argv: viaja en la variable de entorno TFG_CLAVE_NUEVA (docker exec -e sin valor) y psql la lee con \getenv.
set -eu
[ $# -eq 2 ] || { echo "uso: $0 <tenant_id> <nombre>" >&2; exit 2; }
cd "$(dirname "$0")/.."
. ./.env
KEY="tfg_$(openssl rand -hex 32)"
TFG_CLAVE_NUEVA="$KEY"
export TFG_CLAVE_NUEVA
docker exec -i -e TFG_CLAVE_NUEVA tfg-postgres psql -v ON_ERROR_STOP=1 -q -U "$TFG_OWNER_USER" -d "$TFG_DB_NAME" \
  -v tenant="$1" -v nombre="$2" <<'SQL'
\getenv key TFG_CLAVE_NUEVA
INSERT INTO api_keys (tenant_id, nombre, key_hash)
VALUES (:'tenant', :'nombre', sha256(convert_to(:'key', 'UTF8')));
SQL
echo "Clave para $1 ($2): $KEY"
