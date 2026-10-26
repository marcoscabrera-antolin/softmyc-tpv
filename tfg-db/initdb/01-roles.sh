#!/bin/sh
# Crea el rol de la aplicación (nombre fijo tfg_app: lo usan los GRANT de 02-schema.sql) (solo DML, sin DDL). Se ejecuta una sola vez, al inicializar el volumen.
set -eu
psql -v ON_ERROR_STOP=1 -v app_user=tfg_app -v app_pass="$TFG_APP_PASSWORD" \
     -v db="$POSTGRES_DB" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_pass' NOSUPERUSER NOCREATEDB NOCREATEROLE;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
REVOKE TEMP ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app_user";
ALTER ROLE :"app_user" CONNECTION LIMIT 10;
ALTER ROLE :"app_user" SET statement_timeout = '5s';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL

# Rol de autenticación (login, refresco y sesiones): solo si se facilita su contraseña; si no, queda NOLOGIN (lo crea 05-auth-endurecimiento.sql).
if [ -n "${TFG_AUTH_PASSWORD:-}" ]; then
psql -v ON_ERROR_STOP=1 -v auth_pass="$TFG_AUTH_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE tfg_auth LOGIN PASSWORD :'auth_pass' NOSUPERUSER NOCREATEDB NOCREATEROLE;
SQL
fi
