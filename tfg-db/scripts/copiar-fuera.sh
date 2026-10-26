#!/bin/sh
# Copia ficheros de backup fuera del VPS. Uso: ./scripts/copiar-fuera.sh <fichero>...
# (lo llama backup.sh si BACKUP_OFFSITE está definido; también se puede ejecutar a mano).
# Variables de .env:
#   BACKUP_OFFSITE=scp | rclone
#   BACKUP_OFFSITE_DESTINO   scp: usuario@host:/ruta/    rclone: remoto:ruta
#   BACKUP_OFFSITE_CLAVE     (solo scp, opcional) clave SSH sin contraseña, usada solo para esta copia
# Con rclone, las credenciales del destino viven en la configuración de rclone del VPS (rclone config), no aquí.
set -eu
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."
. ./.env
DIR="${BACKUP_DIR:-$PWD/backups}"
LOG_FILE="$DIR/backup.log"
mkdir -p "$DIR"
. "$SCRIPT_DIR/copias-comun.sh"

[ $# -ge 1 ] || fallar "uso: $0 <fichero>..."
DESTINO="${BACKUP_OFFSITE_DESTINO:?define BACKUP_OFFSITE_DESTINO en .env}"

case "${BACKUP_OFFSITE:-}" in
  scp)
    if [ -n "${BACKUP_OFFSITE_CLAVE:-}" ]; then
      scp -q -o BatchMode=yes -i "$BACKUP_OFFSITE_CLAVE" "$@" "$DESTINO"
    else
      scp -q -o BatchMode=yes "$@" "$DESTINO"
    fi
    ;;
  rclone)
    for fichero in "$@"; do
      rclone copyto "$fichero" "$DESTINO/$(basename "$fichero")"
    done
    ;;
  *) fallar "BACKUP_OFFSITE debe ser scp o rclone" ;;
esac

registrar "copia enviada fuera del VPS: $# fichero(s) a $DESTINO"
