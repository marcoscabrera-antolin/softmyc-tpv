#!/bin/sh
# Alerta si no hay una copia reciente de la base (por defecto, en las últimas 26 h). Pensado para un cron propio,
# independiente de backup.sh, para detectar también que el cron de la copia se ha parado.
# Variable opcional: BACKUP_MAX_HORAS (26). Código de salida distinto de cero si no hay copia reciente.
set -eu
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."
. ./.env
DIR="${BACKUP_DIR:-$PWD/backups}"
MAX_HORAS="${BACKUP_MAX_HORAS:-26}"
LOG_FILE="$DIR/backup.log"
. "$SCRIPT_DIR/copias-comun.sh"

RECIENTE="$(find "$DIR" -maxdepth 1 -type f -name "${TFG_DB_NAME}-*.dump*" ! -name '*.tmp' \
  -size +0c -mmin -$((MAX_HORAS * 60)) | head -n 1)"

if [ -z "$RECIENTE" ]; then
  notificar "ALERTA: no hay copia de $TFG_DB_NAME de las últimas $MAX_HORAS h en $DIR"
  exit 1
fi
registrar "comprobación correcta: copia reciente $RECIENTE"
