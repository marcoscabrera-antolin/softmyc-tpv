# Base de datos del TFG: `tfg_softmyc_tpv`

> `legacy/` contiene los scripts Node/PostgreSQL de la migración antigua desde MongoDB (`db.js`, `db_config.js`, `migrate_mongo_to_postgres.js`, `seed_ingredients_recipes.js`, `schema*.sql`). No forman parte de la API ni del despliegue actual.

PostgreSQL 16 en el VPS (`ubuntu@137.74.168.247`, carpeta `~/tfg-db`), aislada de la base de producción `softmyc-postgres`.

| Elemento | Valor |
|---|---|
| Contenedor / volumen / red | `tfg-postgres` / `tfg_postgres_data` / `tfg_net` |
| Puerto | `127.0.0.1:5433` (solo loopback; no es accesible desde Internet) |
| Base de datos | `tfg_softmyc_tpv` |
| Roles | `tfg_owner` (propietario, migraciones) y `tfg_app` (solo SELECT/INSERT/UPDATE/DELETE según tabla; sin DDL) |
| Contraseñas | `~/tfg-db/.env` en el VPS (modo 600). No están en el repositorio |

## Esquema
`tenants`, `productos`, `extras`, `salsas`, `ventas` (con `items jsonb`) y las vistas `venta_lineas` y `ventas_diarias`. Equivale a las entidades Room de la app, con `tenant_id` en lugar de una base SQLite por negocio. `ventas` tiene `UNIQUE (tenant_id, id_local, fecha)` para que reenviar una venta no la duplique.

## Uso
```bash
ssh -L 5433:127.0.0.1:5433 ubuntu@137.74.168.247     # túnel desde tu PC
psql -h 127.0.0.1 -p 5433 -U tfg_app tfg_softmyc_tpv  # o desde DBeaver/pgAdmin

ssh ubuntu@137.74.168.247 'docker exec -it tfg-postgres psql -U tfg_owner -d tfg_softmyc_tpv'
ssh ubuntu@137.74.168.247 'cd ~/tfg-db && ./scripts/backup.sh'   # copia manual en el VPS (ver "Copias de seguridad")
```
Los scripts de `initdb/` solo se ejecutan al crear el volumen. Para rehacer la base desde cero hay que borrar el volumen (`docker compose down -v`), lo que destruye los datos.

## Cuentas de usuario (fases 1 y 1b de la autenticación)
`initdb/04-auth.sql` y `initdb/05-auth-endurecimiento.sql` (o las migraciones `2026-10-16-auth-fase1*.sql` en una base ya creada) añaden `usuarios`, `usuario_negocios`, `sesiones` y `auditoria_auth` y 10 funciones `SECURITY DEFINER`, propiedad de un rol sin privilegios. Hay dos roles de aplicación: `tfg_auth` (login, refresco, sesiones; contraseña `TFG_AUTH_PASSWORD`, se activa con `scripts/configurar-rol-auth.sh`) y `tfg_app` (solo valida el token y accede a los datos de negocio). Ninguno de los dos lee las tablas. `scripts/purgar-auth.sh` (cron, 02:45 UTC) purga sesiones y auditoría antiguas. Tests: `api/test/auth-db.test.js` (necesita `TEST_OWNER_URL`, `TEST_APP_URL` y `TEST_AUTH_URL`). Plan, decisiones y revisión de seguridad: Anexo B.8 de la memoria. ## Acceso y administración de cuentas (fases 2 y 3, desplegadas)
- `POST /api/tpv/auth/login`, `/refresh`, `/logout` y `GET /api/tpv/auth/me`: tokens opacos (acceso 30 min, refresco rotatorio de 7 días), scrypt, respuesta idéntica en todos los fallos, bloqueo de cuenta, limitación por IP.
- `/api/admin/*` (solo administradores de plataforma): negocios, cambio de negocio, usuarios (crear, modificar, contraseña, desbloquear). Crear o promover administradores y cambiar la contraseña de otro exige reconfirmar la contraseña.
- Gestión desde el servidor, sin API: `docker exec -it tfg-api node scripts/usuarios.js <crear|listar|password|desbloquear|desactivar|activar> --username u ...` (la contraseña se pide por teclado o por stdin; nunca como argumento). Primer administrador: `ssh -t ubuntu@137.74.168.247 'docker exec -it tfg-api node scripts/usuarios.js crear --username <u> --nombre "<N>" --admin'`.
- Variables: `DATABASE_URL` (tfg_app), `AUTH_DATABASE_URL` (tfg_auth), `ACCESS_TTL_MIN`, `REFRESH_TTL_DAYS` y `TRUST_PROXY`.
- `ALLOW_API_KEY_AUTH` (SEG-API-04): la clave heredada `x-api-key` está **desactivada por defecto** (`false`, también en `docker-compose.yml`). **Al desplegar este cambio, cualquier tablet que aún use solo la clave dejará de funcionar** hasta que use login (`/api/tpv/auth/login`) o se defina `ALLOW_API_KEY_AUTH=true` de forma explícita en `~/tfg-db/.env` (reiniciar `tfg-api`). Si se activa, el arranque escribe un aviso en el log: la clave no caduca y da acceso de operador a su negocio. Solo se admiten `true` o `false` (cualquier otro valor impide arrancar). Conviene rotar o retirar las claves existentes (`api_keys.activa`). Los tests con base de datos (`api.test.js`, `auth-api.test.js`) construyen la app con `createApp()` sin pasar la opción y la siguen usando activa; en producción manda `config.allowApiKey`.
- `TRUST_PROXY` (INFRA-04): número de saltos de proxy de confianza delante de la API. Por defecto `1` (el nginx de softmyc, que fija `X-Forwarded-For` con la IP real del cliente). `0` o `false` ignoran la cabecera (p. ej. si la API se expusiera sin proxy). Afecta al límite de peticiones y a la limitación de login por IP.
- Tests: `npm test` en `api/` con `TEST_OWNER_URL`, `TEST_APP_URL` y `TEST_AUTH_URL` (90). Detalle en el Anexo B.8 de la memoria.

## Copias de seguridad
`scripts/backup.sh` (cron de `ubuntu` en el VPS, 02:30 UTC) hace un `pg_dump -Fc` **con propietarios y privilegios** (sin `--no-owner`) y un `pg_dumpall --globals-only` con los roles (incluye los hashes de contraseña). Verifica el índice del volcado (`pg_restore --list`, debe contener `ventas`), cifra si está configurado, envía las copias fuera del VPS si está configurado y borra las de más de `KEEP_DAYS` días. Todo queda en `backups/backup.log`. Cualquier fallo termina con código distinto de cero y avisa (webhook, si está configurado).

Variables de `~/tfg-db/.env` (modo 600; no se suben al repositorio):

| Variable | Uso |
|---|---|
| `BACKUP_DIR` | Carpeta de copias (por defecto `~/tfg-db/backups`, modo 700) |
| `KEEP_DAYS` | Días de retención (14) |
| `BACKUP_MIN_FREE_MB` | Espacio libre mínimo antes de volcar (2048) |
| `BACKUP_CIFRADO` | `age`, `gpg` o vacío (sin cifrar) |
| `BACKUP_AGE_RECIPIENT` | Clave **pública** de age (`age1…`). La privada no debe estar en el VPS |
| `BACKUP_GPG_PASSPHRASE` | Solo si `BACKUP_CIFRADO=gpg`. Quien tenga `.env` puede descifrar: age es más seguro |
| `BACKUP_OFFSITE` | `scp` o `rclone`: envía cada copia fuera del VPS |
| `BACKUP_OFFSITE_DESTINO` | `usuario@host:/ruta/` (scp) o `remoto:ruta` (rclone) |
| `BACKUP_OFFSITE_CLAVE` | Opcional, solo scp: clave SSH sin contraseña usada únicamente para la copia |
| `BACKUP_ALERT_WEBHOOK` | URL de un webhook que acepte `POST` JSON con campo `text` (p. ej. Slack). Sin él, el fallo solo queda en el registro |
| `BACKUP_MAX_HORAS` | Antigüedad máxima que admite `scripts/comprobar-copias.sh` (26) |

Cifrado con age (recomendado): en **tu equipo** genera el par con `age-keygen -o copia-tfg.key`, pon la línea `public key` en `BACKUP_AGE_RECIPIENT` y guarda `copia-tfg.key` fuera del VPS (gestor de contraseñas). Sin esa clave privada no hay restauración.

Programación recomendada (`crontab -e` del usuario `ubuntu`, sustituye la línea actual de la copia):
```bash
30 2 * * * /home/ubuntu/tfg-db/scripts/backup.sh >> /home/ubuntu/tfg-db/backups/cron.log 2>&1
45 8 * * * /home/ubuntu/tfg-db/scripts/comprobar-copias.sh >> /home/ubuntu/tfg-db/backups/cron.log 2>&1
```
`comprobar-copias.sh` avisa si no hay copia reciente, aunque el cron de `backup.sh` esté parado.

Sin WAL ni archivado continuo: el punto de recuperación es la última copia (hasta 24 h).
### Restauración

No usar `pg_restore --no-owner`: los objetos quedarían a nombre de `tfg_owner` (superusuario) y las funciones `auth_*` `SECURITY DEFINER` se ejecutarían con sus privilegios en lugar de con los de `tfg_auth_owner`.

```bash
# En el VPS, desde ~/tfg-db. Destructivo: sustituye los objetos de la base.
RESTAURAR_CONFIRMAR=SI ./scripts/restaurar.sh backups/<copia>.dump[.age|.gpg] backups/<copia>.globals.sql[.age|.gpg]
```

Qué hace, en orden: verifica el volcado; para `tfg-api`; aplica los roles globales y crea los que falten (`tfg_app`, `tfg_auth`, `tfg_auth_owner`, siempre `NOLOGIN` si se crean aquí); restaura con `--clean --if-exists --single-transaction` y propietarios originales; comprueba que existen los roles, que las tablas y secuencias son de `tfg_owner` o `tfg_auth_owner`, y que todas las funciones `SECURITY DEFINER` son de `tfg_auth_owner`; arranca `tfg-api` aunque falle algo. Si la comprobación falla, la transacción se revierte y la base queda como estaba.

- Instancia nueva: `docker compose up -d tfg-postgres` (ejecuta `initdb/` y crea `tfg_owner`/`tfg_app`), después el comando anterior. Si `tfg_app` se crea aquí sin contraseña, hay que fijarla antes de usar la API (`ALTER ROLE tfg_app PASSWORD '…'`).
- Copia cifrada con age: exporta `AGE_IDENTITY=/ruta/copia-tfg.key` en la sesión (la clave privada no se guarda en el VPS). Con gpg, `BACKUP_GPG_PASSPHRASE` debe estar en `.env`.
- Pendiente: la restauración con propietarios no se ha probado todavía en un Postgres temporal. Hacer una prueba antes de confiar en ella.
Las copias locales protegen de borrados y corrupción, no de la pérdida del VPS: por eso `BACKUP_OFFSITE` debe estar configurado. `scripts/copiar-fuera.sh <fichero>...` envía ficheros a mano con la misma configuración.

## Aislamiento de red y endurecimiento del contenedor (pendiente de desplegar)

Cambios ya escritos en `docker-compose.yml` y **no aplicados en el VPS**:

- `tfg-postgres`: `read_only` con tmpfs (`/tmp`, `/var/run/postgresql`), `cap_drop: ALL` con solo `CHOWN, DAC_OVERRIDE, FOWNER, SETGID, SETUID` (las que usa el entrypoint de la imagen para preparar `PGDATA` y bajar a `postgres`), `cpus: 1.0`, `pids_limit: 200` y logs json-file rotados (10 MB × 5). Si el contenedor no arranca por una capacidad, el log lo indicará; revertir quitando estas líneas.
- `tfg-api`: deja `softmyc_softmyc-net` (red compartida con producción) y entra solo en `tfg_edge`, red que comparte únicamente con el nginx de softmyc. Así, un contenedor comprometido de softmyc ya no llega a `tfg-api:3100`. Logs rotados.

Orden de despliegue. Sin el paso 3 el nginx pierde la ruta a la API:

1. `./scripts/backup.sh` antes de tocar nada.
2. `docker network create tfg_edge`
3. `docker network connect tfg_edge <contenedor-nginx-softmyc>` (nombre con `docker ps --format '{{.Names}}'`). La conexión no sobrevive a una recreación del nginx: hay que añadir `tfg_edge` (externa) a su compose, en el repositorio de softmyc.
4. `docker exec <contenedor-nginx-softmyc> nginx -s reload`
5. `cd ~/tfg-db && docker compose up -d`. Recrea `tfg-postgres` (corte breve) y `tfg-api`.
6. Comprobaciones: `docker exec tfg-api wget -qO- http://127.0.0.1:3100/health`; `curl -s https://api.example.com/health`; y desde un contenedor de softmyc, `docker run --rm --network softmyc_softmyc-net busybox wget -qO- -T 3 http://tfg-api:3100/health` debe **fallar** (no resuelve).

`trust proxy` de la API sigue confiando en `X-Forwarded-For`; con el aislamiento solo nginx puede enviarlo (ajustar en `api/` es trabajo aparte).

## Endurecimiento pendiente de aplicar en el VPS

Comandos que el administrador ejecuta a mano en el VPS. No están ejecutados ni automatizados. Antes de cada cambio de SSH o firewall, deja abierta una segunda sesión SSH.

1. **SSH solo con clave.** Confirma antes que la clave funciona. Ubuntu lee `sshd_config.d/` en orden, así que el prefijo `10-` gana a `50-cloud-init.conf`:
   ```bash
   printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin prohibit-password\n' | sudo tee /etc/ssh/sshd_config.d/10-solo-clave.conf >/dev/null
   sudo sshd -t && sudo systemctl reload ssh
   sudo sshd -T | grep -iE 'passwordauthentication|permitrootlogin'   # comprobar
   ```
2. **Firewall (ufw).** Antes, lista lo que escucha y lo que publica Docker (`sudo ss -tlnp`, `docker ps`): los puertos publicados por Docker se saltan ufw, por eso los de Postgres van ligados a `127.0.0.1`.
   ```bash
   sudo ufw default deny incoming
   sudo ufw default allow outgoing
   sudo ufw allow OpenSSH        # antes de activar: si no, pierdes el acceso
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable
   sudo ufw status verbose
   ```
   Añade los puertos que necesite softmyc si los hay.
3. **fail2ban** contra fuerza bruta SSH:
   ```bash
   sudo apt-get update && sudo apt-get install -y fail2ban
   printf '[sshd]\nenabled = true\nmaxretry = 5\nbantime = 1h\n' | sudo tee /etc/fail2ban/jail.d/sshd-local.conf >/dev/null
   sudo systemctl enable --now fail2ban
   sudo fail2ban-client status sshd
   ```
4. **Actualizaciones de seguridad automáticas:**
   ```bash
   sudo apt-get install -y unattended-upgrades
   sudo dpkg-reconfigure -plow unattended-upgrades
   ```
5. **Permisos de la base y las copias:** `chmod 600 ~/tfg-db/.env && chmod 700 ~/tfg-db/backups`.
6. **Cron y copia externa:** aplicar la programación de arriba, configurar `BACKUP_OFFSITE`/`BACKUP_ALERT_WEBHOOK` en `.env` y probar una vez `./scripts/backup.sh` y `./scripts/comprobar-copias.sh`.

## Notas de seguridad y pendientes
- Rotar contraseñas: `ALTER ROLE tfg_app PASSWORD '...'` y actualizar `.env` (los scripts `initdb/` no se repiten).
- Los tenants se desactivan (`activo = false`); no se borran. Las ventas no referencian `productos` a propósito (histórico).
- Pendiente cuando exista la API: RLS por `tenant_id` (`SET LOCAL app.tenant`), UUID de venta generado en la tablet para la deduplicación (el `id_local` de Room se reinicia al reinstalar), TLS o túnel si la API no está en el mismo VPS y fijar la imagen por digest.

## API (`api/`)
Node 22 + Express en el contenedor `tfg-api`, publicada por el nginx de softmyc en `https://api.example.com` (fichero `nginx/tfg-api.*.conf` → `softmyc/nginx/conf.d/tfg-api.conf`). Endpoints: `POST /api/tpv/orders/sync`, `GET /api/tpv/products`, `PUT /api/tpv/products/:codigo/stock`, `GET /health`. Cada petición lleva `x-api-key`; el negocio sale de la clave.

```bash
./scripts/crear-clave.sh fruteria "Tablet cocina"            # en el VPS: imprime la clave una sola vez
docker compose up -d --build tfg-api                         # redespliegue
cd api && TEST_OWNER_URL=postgres://… TEST_APP_URL=postgres://… npm test   # 13 tests de integración (Postgres con initdb/)
```
En la app: *Ajustes → Servidor*, URL `https://api.example.com` y la clave. La migración `migrations/` se aplica con `psql` como `tfg_owner`.

El registro de decisiones (qué, dónde y por qué) está en el Anexo B de la memoria (`Documentacion TFG.md`) y la evidencia de las pruebas en `evidencias/pruebas_tablet_2026-10-15.md`.

## Contrato cliente-servidor (app ↔ API)
Todas las rutas cuelgan de `/api/tpv` salvo `/health` y `/api/admin`. El negocio sale siempre de la sesión (o de la clave de API), nunca del cuerpo. Rol mínimo entre paréntesis (`op` = `tpv_operator`).

| Ruta | La API | La usa la app | Nota |
|---|---|---|---|
| `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `GET /auth/me` | Sí | Sí | Sesión con tokens opacos |
| `GET /api/admin/restaurants`, `POST /api/admin/switch-restaurant` | Sí (admin) | Sí | Selector de negocio |
| `GET/POST/PATCH /api/admin/users…` | Sí (admin) | No | Gestión de cuentas (solo por API) |
| `POST /orders/sync` (op) | Sí | Sí | Idempotente por `uuid`; responde `syncedIds` y `rejected` |
| `GET /products` (op) | Sí | Sí | Catálogo, extras, salsas, `unidadMedida` y `stock` |
| `POST /products` (encargado) | **Sí** | Sí | Alta/edición por código; sin `stock`/`unidadMedida` conserva los existentes |
| `DELETE /products/:codigo` (encargado) | **Sí** | Sí | 404 si no existe |
| `PUT /products/:codigo/stock` (encargado) | Sí | No | La app aún no sincroniza el stock (trabajo futuro) |
| `GET /orders`, `PUT /orders/:id/status`, `GET /tenants`, `GET/POST /config/wait-time` | **No** | Sí (`PedidosWebActivity`, tiempo de espera) | Pedidos web y cocina: fuera del alcance del TFG; esas pantallas fallan con este servidor |

Catálogo: la tablet anota sus cambios en una cola local (`CatalogOutbox`) y los envía con `POST`/`DELETE /products`. Mientras quede alguno sin enviar (sin red, sin permiso de encargado) la app **no** sustituye el catálogo local por el del servidor, para no perder la edición. Con la clave de API heredada (`op`) las escrituras dan 403.
