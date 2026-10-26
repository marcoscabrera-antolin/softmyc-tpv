#!/bin/sh
# Funciones comunes de las copias de seguridad. No se ejecuta sola: backup.sh, restaurar.sh,
# comprobar-copias.sh y copiar-fuera.sh la incluyen con `. "$SCRIPT_DIR/copias-comun.sh"` tras fijar LOG_FILE.
# Los secretos (BACKUP_GPG_PASSPHRASE, URL del webhook) nunca van como argumento de un comando: se pasan por stdin.

# registrar <texto>: línea con fecha UTC en pantalla y en el registro de copias.
registrar() {
  printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG_FILE"
}

# notificar <texto>: registra y, si BACKUP_ALERT_WEBHOOK está definido, envía un POST JSON {"text": ...}.
# La URL se pasa a curl por stdin (-K -) para que no aparezca en ps.
notificar() {
  registrar "$*"
  [ -n "${BACKUP_ALERT_WEBHOOK:-}" ] || return 0
  aviso="$(mktemp)"
  printf '{"text":"%s"}' "$(printf '%s' "$*" | tr '"\\' "''")" > "$aviso"
  if ! printf 'url = "%s"\n' "$BACKUP_ALERT_WEBHOOK" \
       | curl -fsS -m 20 -H 'Content-Type: application/json' -K - --data-binary "@$aviso" >/dev/null; then
    registrar "AVISO: no se pudo enviar la alerta al webhook"
  fi
  rm -f "$aviso"
}

# fallar <motivo>: termina con código 1; el trap EXIT de cada script notifica el motivo.
fallar() {
  ULTIMO_ERROR="$*"
  exit 1
}

# cifrar <claro> <destino>: cifra según BACKUP_CIFRADO (age|gpg) y borra el claro.
# Con BACKUP_CIFRADO vacío solo renombra. Cada orden va en línea propia para que set -e corte ante un fallo.
cifrar() {
  case "$CIFRADO" in
    "") mv "$1" "$2" ;;
    age)
      age -r "$BACKUP_AGE_RECIPIENT" -o "$2.tmp" "$1"
      rm -f "$1"
      mv "$2.tmp" "$2"
      ;;
    gpg)
      printf '%s' "$BACKUP_GPG_PASSPHRASE" \
        | gpg --batch --yes --quiet --pinentry-mode loopback --symmetric --cipher-algo AES256 \
              --passphrase-fd 0 -o "$2.tmp" "$1"
      rm -f "$1"
      mv "$2.tmp" "$2"
      ;;
  esac
}

# descifrar <origen> <destino>: inverso de cifrar, según la extensión. age necesita AGE_IDENTITY
# (ruta a la clave privada, fuera del repositorio); gpg usa BACKUP_GPG_PASSPHRASE.
descifrar() {
  case "$1" in
    *.age)
      age -d -i "${AGE_IDENTITY:?define AGE_IDENTITY (clave privada age) para restaurar copias .age}" -o "$2" "$1"
      ;;
    *.gpg)
      printf '%s' "${BACKUP_GPG_PASSPHRASE:?define BACKUP_GPG_PASSPHRASE para restaurar copias .gpg}" \
        | gpg --batch --yes --quiet --pinentry-mode loopback --decrypt --passphrase-fd 0 -o "$2" "$1"
      ;;
    *) cp "$1" "$2" ;;
  esac
}
