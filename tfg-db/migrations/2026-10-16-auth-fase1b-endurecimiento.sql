-- Autenticación, fase 1b: endurecimiento tras la revisión de seguridad de 04-auth.sql.
--  * Las tablas y funciones dejan de pertenecer al superusuario del contenedor (rol tfg_auth_owner, sin privilegios).
--  * Rol tfg_auth: único que ejecuta login, refresco, cierre de sesión y alta de sesiones. tfg_app (endpoints de negocio)
--    solo conserva auth_validar_acceso: un fallo en un endpoint de negocio ya no permite crear sesiones ni leer hashes.
--  * Acceso y rol se recalculan en cada validación; TTL acotados; bloqueo sin desbloqueo por acierto; longitudes y purga.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tfg_auth') THEN
        CREATE ROLE tfg_auth NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;   -- LOGIN y contraseña: 01-roles.sh o configurar-rol-auth.sh
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tfg_auth_owner') THEN
        CREATE ROLE tfg_auth_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO tfg_auth', current_database());
END $$;
GRANT USAGE ON SCHEMA public TO tfg_auth, tfg_auth_owner;
GRANT SELECT, REFERENCES ON tenants TO tfg_auth_owner;
ALTER ROLE tfg_auth CONNECTION LIMIT 10;
ALTER ROLE tfg_auth SET statement_timeout = '5s';

-- Restricciones adicionales (la API no puede producir sesiones incoherentes ni filas de auditoría gigantes).
ALTER TABLE sesiones ADD CONSTRAINT sesiones_tenant_o_admin CHECK (tenant_id IS NOT NULL OR rol = 'admin');
ALTER TABLE sesiones ADD CONSTRAINT sesiones_caducidad_valida CHECK (familia_expira_en > creada_en);
ALTER TABLE sesiones ADD CONSTRAINT sesiones_dispositivo_longitud CHECK (dispositivo IS NULL OR length(dispositivo) <= 100);
ALTER TABLE auditoria_auth ADD CONSTRAINT auditoria_auth_longitudes CHECK (
    length(evento) <= 40 AND length(coalesce(username, '')) <= 64 AND length(coalesce(tenant_id, '')) <= 40
    AND length(coalesce(ip, '')) <= 45 AND length(coalesce(user_agent, '')) <= 200);

-- Funciones sin cambios de lógica: solo el search_path (pg_catalog primero).
ALTER FUNCTION auth_rol_para(bigint, text) SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION auth_credencial(text) SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION auth_negocios_usuario(bigint) SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION auth_revocar_sesion(bytea) SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION auth_revocar_usuario(bigint) SET search_path = pg_catalog, public, pg_temp;

-- Un acierto no levanta un bloqueo vigente (los intentos en paralelo no burlan el límite) y el exponente tiene tope.
-- La API debe denegar el acceso si esta función devuelve un valor no nulo, aunque la contraseña fuera correcta.
CREATE OR REPLACE FUNCTION auth_registrar_intento(p_username text, p_usuario_id bigint, p_ok boolean, p_ip text, p_user_agent text)
RETURNS timestamptz LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE u usuarios%ROWTYPE; v_bloqueo timestamptz; v_n integer;
BEGIN
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito, ip, user_agent)
    VALUES (p_usuario_id, left(p_username, 64), 'login', p_ok, left(p_ip, 45), left(p_user_agent, 200));
    IF p_usuario_id IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO u FROM usuarios WHERE id = p_usuario_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    IF u.bloqueado_hasta IS NOT NULL AND u.bloqueado_hasta > now() THEN RETURN u.bloqueado_hasta; END IF;
    IF p_ok THEN
        UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = p_usuario_id;
        RETURN NULL;
    END IF;
    v_n := u.intentos_fallidos + 1;
    IF v_n >= 5 THEN
        v_bloqueo := now() + make_interval(mins => least(15 * power(2, least((v_n - 5) / 5, 7))::int, 1440));
    END IF;
    UPDATE usuarios SET intentos_fallidos = v_n, bloqueado_hasta = v_bloqueo WHERE id = p_usuario_id;
    RETURN v_bloqueo;
END $$;

-- TTL acotados dentro de la base (acceso: 1 min a 1 h; refresco: 1 h a 30 días) y máximo de 10 sesiones activas por usuario.
CREATE OR REPLACE FUNCTION auth_crear_sesion(p_usuario_id bigint, p_tenant_id text, p_access_hash bytea, p_refresh_hash bytea,
                                             p_access_ttl interval, p_refresh_ttl interval, p_dispositivo text)
RETURNS TABLE (sesion_id uuid, rol text, access_expira_en timestamptz, refresh_expira_en timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_rol text; v_id uuid; v_ae timestamptz; v_fe timestamptz;
        v_acc interval := least(greatest(p_access_ttl, interval '1 minute'), interval '1 hour');
        v_ref interval := least(greatest(p_refresh_ttl, interval '1 hour'), interval '30 days');
BEGIN
    v_rol := auth_rol_para(p_usuario_id, p_tenant_id);
    IF v_rol IS NULL THEN
        INSERT INTO auditoria_auth (usuario_id, tenant_id, evento, exito) VALUES (p_usuario_id, left(p_tenant_id, 40), 'sesion_denegada', false);
        RAISE EXCEPTION 'acceso_denegado' USING ERRCODE = '42501';
    END IF;
    INSERT INTO sesiones (familia, usuario_id, tenant_id, rol, access_hash, refresh_hash, access_expira_en, familia_expira_en, dispositivo)
    VALUES (gen_random_uuid(), p_usuario_id, p_tenant_id, v_rol, p_access_hash, p_refresh_hash,
            now() + v_acc, now() + v_ref, left(p_dispositivo, 100))
    RETURNING id, sesiones.access_expira_en, sesiones.familia_expira_en INTO v_id, v_ae, v_fe;
    UPDATE sesiones SET revocada_en = now()
     WHERE revocada_en IS NULL AND familia IN (
        SELECT familia FROM (SELECT familia, min(creada_en) AS c FROM sesiones
                              WHERE usuario_id = p_usuario_id AND revocada_en IS NULL AND reemplazada_por IS NULL
                              GROUP BY familia ORDER BY c DESC, familia OFFSET 10) viejas);
    RETURN QUERY SELECT v_id, v_rol, v_ae, v_fe;
END $$;

CREATE OR REPLACE FUNCTION auth_rotar_sesion(p_refresh_hash bytea, p_nuevo_access_hash bytea, p_nuevo_refresh_hash bytea,
                                             p_access_ttl interval, p_ip text, p_user_agent text)
RETURNS TABLE (estado text, sesion_id uuid, usuario_id bigint, tenant_id text, rol text,
               access_expira_en timestamptz, refresh_expira_en timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE s sesiones%ROWTYPE; v_rol text; v_nueva sesiones%ROWTYPE;
        v_acc interval := least(greatest(p_access_ttl, interval '1 minute'), interval '1 hour');
BEGIN
    SELECT * INTO s FROM sesiones WHERE refresh_hash = p_refresh_hash FOR UPDATE;
    IF NOT FOUND THEN RETURN QUERY SELECT 'invalido', NULL::uuid, NULL::bigint, NULL::text, NULL::text, NULL::timestamptz, NULL::timestamptz; RETURN; END IF;
    IF s.reemplazada_por IS NOT NULL THEN
        UPDATE sesiones SET revocada_en = now() WHERE familia = s.familia AND revocada_en IS NULL;
        INSERT INTO auditoria_auth (usuario_id, tenant_id, evento, exito, ip, user_agent)
        VALUES (s.usuario_id, s.tenant_id, 'refresh_reutilizado', false, left(p_ip, 45), left(p_user_agent, 200));
        RETURN QUERY SELECT 'reutilizado', s.id, s.usuario_id, s.tenant_id, s.rol, NULL::timestamptz, NULL::timestamptz; RETURN;
    END IF;
    IF s.revocada_en IS NOT NULL THEN RETURN QUERY SELECT 'invalido', NULL::uuid, NULL::bigint, NULL::text, NULL::text, NULL::timestamptz, NULL::timestamptz; RETURN; END IF;
    IF s.familia_expira_en <= now() THEN RETURN QUERY SELECT 'caducado', s.id, s.usuario_id, s.tenant_id, s.rol, NULL::timestamptz, NULL::timestamptz; RETURN; END IF;
    v_rol := auth_rol_para(s.usuario_id, s.tenant_id);
    IF v_rol IS NULL THEN
        UPDATE sesiones SET revocada_en = now() WHERE familia = s.familia AND revocada_en IS NULL;
        INSERT INTO auditoria_auth (usuario_id, tenant_id, evento, exito, ip, user_agent)
        VALUES (s.usuario_id, s.tenant_id, 'refresh_sin_acceso', false, left(p_ip, 45), left(p_user_agent, 200));
        RETURN QUERY SELECT 'inactivo', s.id, s.usuario_id, s.tenant_id, s.rol, NULL::timestamptz, NULL::timestamptz; RETURN;
    END IF;
    INSERT INTO sesiones (familia, usuario_id, tenant_id, rol, access_hash, refresh_hash, access_expira_en, familia_expira_en, dispositivo)
    VALUES (s.familia, s.usuario_id, s.tenant_id, v_rol, p_nuevo_access_hash, p_nuevo_refresh_hash,
            now() + v_acc, s.familia_expira_en, s.dispositivo)
    RETURNING * INTO v_nueva;
    UPDATE sesiones SET reemplazada_por = v_nueva.id WHERE id = s.id;
    RETURN QUERY SELECT 'ok', v_nueva.id, v_nueva.usuario_id, v_nueva.tenant_id, v_nueva.rol, v_nueva.access_expira_en, v_nueva.familia_expira_en;
END $$;

-- El rol sale de los permisos actuales (no del guardado en la sesión): retirar un negocio, cambiar el rol o quitar es_admin
-- surte efecto en la siguiente petición. El último uso se anota como máximo una vez por minuto y sin esperar bloqueos.
CREATE OR REPLACE FUNCTION auth_validar_acceso(p_access_hash bytea)
RETURNS TABLE (sesion_id uuid, usuario_id bigint, tenant_id text, rol text, es_admin boolean)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE s sesiones%ROWTYPE; v_rol text; v_admin boolean;
BEGIN
    SELECT se.* INTO s FROM sesiones se
     WHERE se.access_hash = p_access_hash AND se.revocada_en IS NULL AND se.reemplazada_por IS NULL
       AND se.access_expira_en > now() AND se.familia_expira_en > now();
    IF NOT FOUND THEN RETURN; END IF;
    v_rol := auth_rol_para(s.usuario_id, s.tenant_id);
    IF v_rol IS NULL THEN RETURN; END IF;
    UPDATE sesiones SET ultimo_uso = now() WHERE id IN (
        SELECT id FROM sesiones WHERE id = s.id AND (ultimo_uso IS NULL OR ultimo_uso < now() - interval '1 minute')
        FOR UPDATE SKIP LOCKED);
    SELECT u.es_admin INTO v_admin FROM usuarios u WHERE u.id = s.usuario_id;
    RETURN QUERY SELECT s.id, s.usuario_id, s.tenant_id, v_rol, v_admin;
END $$;

-- Mantenimiento (lo ejecuta el superusuario desde cron, no la API): borra familias de sesiones enteramente antiguas y auditoría vieja.
CREATE FUNCTION auth_purgar(p_sesiones_dias integer DEFAULT 30, p_auditoria_dias integer DEFAULT 180)
RETURNS TABLE (sesiones_borradas bigint, auditoria_borrada bigint)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE a bigint; b bigint;
BEGIN
    DELETE FROM sesiones WHERE familia IN (
        SELECT familia FROM sesiones GROUP BY familia
        HAVING bool_and(coalesce(revocada_en, familia_expira_en) < now() - make_interval(days => p_sesiones_dias)));
    GET DIAGNOSTICS a = ROW_COUNT;
    DELETE FROM auditoria_auth WHERE ts < now() - make_interval(days => p_auditoria_dias);
    GET DIAGNOSTICS b = ROW_COUNT;
    RETURN QUERY SELECT a, b;
END $$;
REVOKE ALL ON FUNCTION auth_purgar(integer, integer) FROM PUBLIC;

-- Propietario sin privilegios para tablas y funciones.
ALTER TABLE usuarios OWNER TO tfg_auth_owner;
ALTER TABLE usuario_negocios OWNER TO tfg_auth_owner;
ALTER TABLE sesiones OWNER TO tfg_auth_owner;
ALTER TABLE auditoria_auth OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_rol_para(bigint, text) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_credencial(text) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_registrar_intento(text, bigint, boolean, text, text) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_negocios_usuario(bigint) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_crear_sesion(bigint, text, bytea, bytea, interval, interval, text) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_rotar_sesion(bytea, bytea, bytea, interval, text, text) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_validar_acceso(bytea) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_revocar_sesion(bytea) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_revocar_usuario(bigint) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_purgar(integer, integer) OWNER TO tfg_auth_owner;

-- Reparto de permisos: tfg_auth ejecuta el flujo de acceso; tfg_app solo valida el token de cada petición.
REVOKE EXECUTE ON FUNCTION auth_credencial(text), auth_registrar_intento(text, bigint, boolean, text, text),
    auth_negocios_usuario(bigint), auth_crear_sesion(bigint, text, bytea, bytea, interval, interval, text),
    auth_rotar_sesion(bytea, bytea, bytea, interval, text, text), auth_revocar_sesion(bytea), auth_revocar_usuario(bigint)
    FROM tfg_app;
GRANT EXECUTE ON FUNCTION auth_credencial(text), auth_registrar_intento(text, bigint, boolean, text, text),
    auth_negocios_usuario(bigint), auth_crear_sesion(bigint, text, bytea, bytea, interval, interval, text),
    auth_rotar_sesion(bytea, bytea, bytea, interval, text, text), auth_validar_acceso(bytea),
    auth_revocar_sesion(bytea), auth_revocar_usuario(bigint) TO tfg_auth;

COMMIT;
