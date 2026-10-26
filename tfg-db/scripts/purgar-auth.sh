#!/bin/sh
# Purga diaria de sesiones caducadas (familias enteras de más de 30 días) y de auditoría de más de 180 días. Lo ejecuta cron.
set -eu
cd "$(dirname "$0")/.."
. ./.env
RES="$(docker exec tfg-postgres psql -At -F ' ' -U "$TFG_OWNER_USER" -d "$TFG_DB_NAME" -c 'SELECT sesiones_borradas, auditoria_borrada FROM auth_purgar()')"
set -- $RES
echo "$(date -u +%FT%TZ) purga: $1 sesiones y $2 filas de auditoria borradas"
