#!/bin/sh
# Copia de seguridad diaria de la base del TFG: pg_dump -Fc con propietarios y privilegios (sin --no-owner)
# más los roles globales (pg_dumpall --globals-only). Cron en el VPS, 02:30 UTC.
# Flujo: espacio libre -> volcado -> verificación (pg_restore --list) -> cifrado opcional
#        -> copia fuera del VPS opcional -> retención.
# Cualquier fallo termina con código distinto de cero y avisa (registro + webhook opcional).
# Variables de ~/tfg-db/.env (detalle en README, "Copias de seguridad"):
#   BACKUP_DIR, KEEP_DAYS (14), BACKUP_MIN_FREE_MB (2048),
#   BACKUP_CIFRADO (age | gpg | vacío), BACKUP_AGE_RECIPIENT (clave pública age),
#   BACKUP_GPG_PASSPHRASE (solo gpg simétrico), BACKUP_OFFSITE (scp | rclone), BACKUP_ALERT_WEBHOOK.
set -eu
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."
. ./.env
DIR="${BACKUP_DIR:-$PWD/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
MIN_FREE_MB="${BACKUP_MIN_FREE_MB:-2048}"
CIFRADO="${BACKUP_CIFRADO:-}"
umask 077
mkdir -p "$DIR"
LOG_FILE="$DIR/backup.log"
. "$SCRIPT_DIR/copias-comun.sh"

ULTIMO_ERROR="error inesperado"
STAMP="$(date -u +%Y%m%d-%H%M%S)"
BASE="$DIR/${TFG_DB_NAME}-$STAMP"
on_exit() {
  codigo=$?
  rm -f "$BASE".*.tmp
  if [ "$codigo" -ne 0 ]; then
    notificar "FALLO copia de $TFG_DB_NAME (código $codigo): $ULTIMO_ERROR"
  fi
}
trap on_exit EXIT

case "$CIFRADO" in
  "") EXT="" ;;
  age)
    EXT=".age"
    [ -n "${BACKUP_AGE_RECIPIENT:-}" ] || fallar "falta BACKUP_AGE_RECIPIENT para el cifrado age"
    ;;
  gpg)
    EXT=".gpg"
    [ -n "${BACKUP_GPG_PASSPHRASE:-}" ] || fallar "falta BACKUP_GPG_PASSPHRASE para el cifrado gpg"
    ;;
  *) fallar "BACKUP_CIFRADO no soportado: '$CIFRADO' (usa age, gpg o vacío)" ;;
esac

LIBRE_MB="$(df -Pm "$DIR" | awk 'NR == 2 {print $4}')"
[ "$LIBRE_MB" -ge "$MIN_FREE_MB" ] || fallar "espacio libre insuficiente: ${LIBRE_MB} MB (mínimo ${MIN_FREE_MB})"

# Todo se escribe con sufijo .tmp y solo se renombra al final: un fallo a medias no deja una copia aparentemente válida.
registrar "volcando $TFG_DB_NAME"
docker exec tfg-postgres pg_dump -U "$TFG_OWNER_USER" -d "$TFG_DB_NAME" -Fc > "$BASE.dump.tmp"
docker exec tfg-postgres pg_dumpall -U "$TFG_OWNER_USER" --globals-only > "$BASE.globals.sql.tmp"
[ -s "$BASE.dump.tmp" ] || fallar "el volcado de la base está vacío"
[ -s "$BASE.globals.sql.tmp" ] || fallar "el volcado de roles globales está vacío"

# Verificación: el índice del volcado es legible y contiene la tabla de ventas.
docker exec -i tfg-postgres pg_restore --list < "$BASE.dump.tmp" > "$BASE.list.tmp"
grep -q "TABLE DATA public ventas" "$BASE.list.tmp" || fallar "el volcado no contiene public.ventas"
rm -f "$BASE.list.tmp"

DUMP_FINAL="$BASE.dump$EXT"
GLOBALES_FINAL="$BASE.globals.sql$EXT"
cifrar "$BASE.dump.tmp" "$DUMP_FINAL"
cifrar "$BASE.globals.sql.tmp" "$GLOBALES_FINAL"

if [ -n "${BACKUP_OFFSITE:-}" ]; then
  "$SCRIPT_DIR/copiar-fuera.sh" "$DUMP_FINAL" "$GLOBALES_FINAL" || fallar "copia fuera del VPS fallida"
fi

find "$DIR" -maxdepth 1 -type f -name "${TFG_DB_NAME}-*" -mtime +"$KEEP_DAYS" -delete
registrar "copia correcta: $DUMP_FINAL ($(du -h "$DUMP_FINAL" | cut -f1)) y roles en $GLOBALES_FINAL"
