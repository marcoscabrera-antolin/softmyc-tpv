#!/bin/sh
# Restaura la base del TFG a partir de una copia de backup.sh. DESTRUCTIVO: sustituye los objetos de la base.
# Uso (en el VPS, desde ~/tfg-db):
#   RESTAURAR_CONFIRMAR=SI ./scripts/restaurar.sh backups/<copia>.dump[.age|.gpg] [backups/<copia>.globals.sql[.age|.gpg]]
# Orden: 1) descifra y verifica el volcado, 2) para tfg-api, 3) aplica los roles globales y crea los que falten,
# 4) pg_restore con propietarios y privilegios originales (sin --no-owner), en una sola transacción,
# 5) comprueba roles, propietarios y funciones SECURITY DEFINER, 6) arranca tfg-api (también si falla).
# Cifrado: age -> AGE_IDENTITY=<clave privada, fuera del repo>; gpg -> BACKUP_GPG_PASSPHRASE en .env.
set -eu
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."
. ./.env
DIR="${BACKUP_DIR:-$PWD/backups}"
umask 077
mkdir -p "$DIR"
LOG_FILE="$DIR/backup.log"
. "$SCRIPT_DIR/copias-comun.sh"

[ "${RESTAURAR_CONFIRMAR:-}" = "SI" ] || { echo "Operación destructiva: exporta RESTAURAR_CONFIRMAR=SI para continuar." >&2; exit 2; }
[ $# -ge 1 ] && [ $# -le 2 ] || { echo "uso: $0 <volcado> [<roles.sql>]" >&2; exit 2; }
[ -s "$1" ] || { echo "no existe o está vacío: $1" >&2; exit 2; }
[ "$(docker inspect -f '{{.State.Running}}' tfg-postgres 2>/dev/null || true)" = "true" ] \
  || { echo "tfg-postgres no está en marcha" >&2; exit 2; }

TRABAJO="$(mktemp -d "$DIR/.restauracion.XXXXXX")"
PARADA=""
on_exit() {
  codigo=$?
  rm -rf "$TRABAJO"
  if [ -n "$PARADA" ]; then
    docker start tfg-api >/dev/null 2>&1 || echo "AVISO: no se pudo arrancar tfg-api; arráncalo a mano (docker start tfg-api)" >&2
  fi
  if [ "$codigo" -ne 0 ]; then registrar "FALLO restauración de $1 (código $codigo)"; fi
}
trap on_exit EXIT

DUMP="$TRABAJO/volcado.dump"
descifrar "$1" "$DUMP"
docker exec -i tfg-postgres pg_restore --list < "$DUMP" > "$TRABAJO/indice.txt"
grep -q "TABLE DATA public ventas" "$TRABAJO/indice.txt" || { echo "el volcado no contiene public.ventas" >&2; exit 1; }

if [ -n "${2:-}" ]; then
  GLOBALES="$TRABAJO/roles.sql"
  descifrar "$2" "$GLOBALES"
fi

# Parar la API evita conexiones abiertas durante el DROP; se vuelve a arrancar en el trap.
if docker stop tfg-api >/dev/null 2>&1; then PARADA=si; fi

if [ -n "${2:-}" ]; then
  echo "Aplicando roles globales (los 'already exists' son esperables)..."
  docker exec -i tfg-postgres psql -q -U "$TFG_OWNER_USER" -d postgres < "$GLOBALES" || true
fi

# Roles que el volcado necesita para los GRANT y los ALTER OWNER. Si ya existen, no se tocan.
docker exec -i tfg-postgres psql -v ON_ERROR_STOP=1 -q -U "$TFG_OWNER_USER" -d postgres <<'SQL'
DO $$
DECLARE rol text;
BEGIN
  FOREACH rol IN ARRAY ARRAY['tfg_app', 'tfg_auth', 'tfg_auth_owner'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = rol) THEN
      EXECUTE format('CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE', rol);
    END IF;
  END LOOP;
END
$$;
SQL

# Sin --no-owner: los objetos recuperan su propietario (tfg_auth_owner para las funciones SECURITY DEFINER).
# --single-transaction: si algo falla, la base queda como estaba.
docker exec -i tfg-postgres pg_restore -U "$TFG_OWNER_USER" -d "$TFG_DB_NAME" \
  --clean --if-exists --single-transaction --exit-on-error < "$DUMP"

# Comprobación: propietarios esperados y funciones SECURITY DEFINER en manos del rol sin login.
docker exec -i tfg-postgres psql -v ON_ERROR_STOP=1 -q -U "$TFG_OWNER_USER" -d "$TFG_DB_NAME" <<'SQL'
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM pg_roles WHERE rolname IN ('tfg_app', 'tfg_auth', 'tfg_auth_owner');
  IF n <> 3 THEN RAISE EXCEPTION 'faltan roles de aplicación (% de 3)', n; END IF;

  SELECT count(*) INTO n FROM pg_class c JOIN pg_namespace s ON s.oid = c.relnamespace
   WHERE s.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
     AND pg_get_userbyid(c.relowner) NOT IN ('tfg_owner', 'tfg_auth_owner');
  IF n > 0 THEN RAISE EXCEPTION '% tablas, vistas o secuencias con propietario inesperado', n; END IF;

  SELECT count(*) INTO n FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
   WHERE s.nspname = 'public' AND p.prosecdef AND pg_get_userbyid(p.proowner) <> 'tfg_auth_owner';
  IF n > 0 THEN RAISE EXCEPTION '% funciones SECURITY DEFINER no pertenecen a tfg_auth_owner', n; END IF;
END
$$;
SQL

docker exec tfg-postgres psql -At -U "$TFG_OWNER_USER" -d "$TFG_DB_NAME" -c "SELECT 'ventas restauradas: ' || count(*) FROM ventas"
registrar "restauración verificada desde $1"
