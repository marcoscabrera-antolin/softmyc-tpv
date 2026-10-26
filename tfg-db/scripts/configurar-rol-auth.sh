#!/bin/sh
# Activa el rol tfg_auth (login) en una base ya creada, con la contraseña de TFG_AUTH_PASSWORD (~/tfg-db/.env).
# Si .env no la tiene, la genera y la añade. Idempotente. Uso (en el VPS): ./scripts/configurar-rol-auth.sh
# La contraseña no aparece en argv: viaja en la variable de entorno TFG_AUTH_PW, que docker exec hereda
# del cliente con "-e TFG_AUTH_PW" (sin valor) y psql lee con \getenv.
set -eu
cd "$(dirname "$0")/.."
if ! grep -q '^TFG_AUTH_PASSWORD=' .env; then printf 'TFG_AUTH_PASSWORD=%s\n' "$(openssl rand -hex 24)" >> .env; fi
. ./.env
[ -n "${TFG_AUTH_PASSWORD:-}" ] || { echo "TFG_AUTH_PASSWORD está vacío en .env" >&2; exit 2; }
TFG_AUTH_PW="$TFG_AUTH_PASSWORD"
export TFG_AUTH_PW
docker exec -i -e TFG_AUTH_PW tfg-postgres psql -v ON_ERROR_STOP=1 -q -U "$TFG_OWNER_USER" -d "$TFG_DB_NAME" <<'SQL'
\getenv pw TFG_AUTH_PW
ALTER ROLE tfg_auth LOGIN PASSWORD :'pw';
SQL
echo "Rol tfg_auth activo."
