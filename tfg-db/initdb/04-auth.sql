-- Autenticación con cuentas de usuario (fase 1): tablas, permisos y funciones SECURITY DEFINER.
-- La API (rol tfg_app) NO lee usuarios ni sesiones directamente: solo ejecuta estas funciones, de modo que una
-- inyección SQL en otro endpoint no puede volcar hashes de contraseña ni tokens. Los tokens se guardan como SHA-256.
BEGIN;

CREATE TABLE usuarios (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username          text NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9._-]{3,40}$'),
    nombre            text NOT NULL CHECK (length(btrim(nombre)) > 0),
    -- Formato PHC: $scrypt$ln=17,r=8,p=1$<sal>$<hash>. La contraseña en claro no se guarda nunca.
    password_hash     text NOT NULL CHECK (password_hash ~ '^\$scrypt\$'),
    es_admin          boolean NOT NULL DEFAULT false,
    activo            boolean NOT NULL DEFAULT true,
    intentos_fallidos integer NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    bloqueado_hasta   timestamptz,
    pwd_cambiada_en   timestamptz NOT NULL DEFAULT now(),
    creado_en         timestamptz NOT NULL DEFAULT now()
);
COMMENT ON COLUMN usuarios.es_admin IS 'Administrador de plataforma: puede operar en cualquier negocio activo con el rol admin.';

CREATE TABLE usuario_negocios (
    usuario_id bigint NOT NULL REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    tenant_id  text   NOT NULL REFERENCES tenants(id)  ON UPDATE NO ACTION ON DELETE RESTRICT,
    rol        text   NOT NULL CHECK (rol IN ('admin', 'encargado', 'tpv_operator')),
    PRIMARY KEY (usuario_id, tenant_id)
);

-- Una fila por generación de tokens (access + refresh). Al rotar el refresco se crea una fila nueva de la misma
-- familia y la anterior queda enlazada en reemplazada_por: reutilizar un refresco ya rotado revoca la familia.
CREATE TABLE sesiones (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    familia           uuid NOT NULL,
    usuario_id        bigint NOT NULL REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    tenant_id         text REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    rol               text NOT NULL CHECK (rol IN ('admin', 'encargado', 'tpv_operator')),
    access_hash       bytea NOT NULL UNIQUE CHECK (octet_length(access_hash) = 32),
    refresh_hash      bytea NOT NULL UNIQUE CHECK (octet_length(refresh_hash) = 32),
    access_expira_en  timestamptz NOT NULL,
    familia_expira_en timestamptz NOT NULL,
    dispositivo       text,
    creada_en         timestamptz NOT NULL DEFAULT now(),
    ultimo_uso        timestamptz,
    revocada_en       timestamptz,
    reemplazada_por   uuid REFERENCES sesiones(id)
);
CREATE INDEX sesiones_familia_idx ON sesiones (familia);
CREATE INDEX sesiones_usuario_idx ON sesiones (usuario_id) WHERE revocada_en IS NULL;

CREATE TABLE auditoria_auth (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ts         timestamptz NOT NULL DEFAULT now(),
    usuario_id bigint,
    username   text,
    tenant_id  text,
    evento     text NOT NULL,
    exito      boolean NOT NULL,
    ip         text,
    user_agent text
);
CREATE INDEX auditoria_auth_ts_idx ON auditoria_auth (ts DESC);

-- Permisos de tablas: tfg_app no tiene ninguno sobre usuarios, usuario_negocios ni sesiones; la auditoría es de solo inserción.
REVOKE ALL ON usuarios, usuario_negocios, sesiones, auditoria_auth FROM PUBLIC;
GRANT INSERT ON auditoria_auth TO tfg_app;

-- Rol efectivo de un usuario en un negocio (NULL si no tiene acceso). Interna: no la ejecuta tfg_app.
CREATE FUNCTION auth_rol_para(p_usuario_id bigint, p_tenant_id text) RETURNS text
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE u usuarios%ROWTYPE; r text;
BEGIN
    SELECT * INTO u FROM usuarios WHERE id = p_usuario_id AND activo;
    IF NOT FOUND THEN RETURN NULL; END IF;
    IF p_tenant_id IS NULL THEN
        RETURN CASE WHEN u.es_admin THEN 'admin' END;   -- sin negocio solo el administrador de plataforma
    END IF;
    IF NOT EXISTS (SELECT 1 FROM tenants WHERE id = p_tenant_id AND activo) THEN RETURN NULL; END IF;
    IF u.es_admin THEN RETURN 'admin'; END IF;
    SELECT rol INTO r FROM usuario_negocios WHERE usuario_id = p_usuario_id AND tenant_id = p_tenant_id;
    RETURN r;
END $$;

-- Datos para verificar la contraseña en la API (el hash se comprueba en Node, no en la base).
CREATE FUNCTION auth_credencial(p_username text)
RETURNS TABLE (usuario_id bigint, password_hash text, es_admin boolean, activo boolean, bloqueado_hasta timestamptz)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT id, password_hash, es_admin, activo, bloqueado_hasta FROM usuarios WHERE username = lower(p_username)
$$;

-- Registra un intento de acceso. 5 fallos seguidos bloquean la cuenta 15 min, el doble cada 5 fallos más (máx. 24 h).
-- Mientras la cuenta está bloqueada los fallos se auditan pero no se cuentan. Devuelve bloqueado_hasta (NULL si no).
CREATE FUNCTION auth_registrar_intento(p_username text, p_usuario_id bigint, p_ok boolean, p_ip text, p_user_agent text)
RETURNS timestamptz LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE u usuarios%ROWTYPE; v_bloqueo timestamptz;
BEGIN
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito, ip, user_agent)
    VALUES (p_usuario_id, p_username, 'login', p_ok, p_ip, p_user_agent);
    IF p_usuario_id IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO u FROM usuarios WHERE id = p_usuario_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    IF p_ok THEN
        UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = p_usuario_id;
        RETURN NULL;
    END IF;
    IF u.bloqueado_hasta IS NOT NULL AND u.bloqueado_hasta > now() THEN RETURN u.bloqueado_hasta; END IF;
    u.intentos_fallidos := u.intentos_fallidos + 1;
    IF u.intentos_fallidos >= 5 THEN
        v_bloqueo := now() + make_interval(mins => least(15 * power(2, (u.intentos_fallidos - 5) / 5)::int, 1440));
    END IF;
    UPDATE usuarios SET intentos_fallidos = u.intentos_fallidos, bloqueado_hasta = v_bloqueo WHERE id = p_usuario_id;
    RETURN v_bloqueo;
END $$;

-- Negocios a los que puede acceder un usuario (el administrador de plataforma, todos los activos).
CREATE FUNCTION auth_negocios_usuario(p_usuario_id bigint)
RETURNS TABLE (tenant_id text, nombre text, rol text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT t.id, t.nombre, 'admin'::text
      FROM tenants t JOIN usuarios u ON u.id = p_usuario_id AND u.activo AND u.es_admin WHERE t.activo
    UNION ALL
    SELECT t.id, t.nombre, un.rol
      FROM usuario_negocios un JOIN tenants t ON t.id = un.tenant_id AND t.activo
      JOIN usuarios u ON u.id = un.usuario_id AND u.activo AND NOT u.es_admin
     WHERE un.usuario_id = p_usuario_id
$$;

-- Abre una sesión (nueva familia). p_tenant_id NULL solo para el administrador de plataforma.
CREATE FUNCTION auth_crear_sesion(p_usuario_id bigint, p_tenant_id text, p_access_hash bytea, p_refresh_hash bytea,
                                  p_access_ttl interval, p_refresh_ttl interval, p_dispositivo text)
RETURNS TABLE (sesion_id uuid, rol text, access_expira_en timestamptz, refresh_expira_en timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE v_rol text;
BEGIN
    v_rol := auth_rol_para(p_usuario_id, p_tenant_id);
    IF v_rol IS NULL THEN
        INSERT INTO auditoria_auth (usuario_id, tenant_id, evento, exito) VALUES (p_usuario_id, p_tenant_id, 'sesion_denegada', false);
        RAISE EXCEPTION 'acceso_denegado' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    INSERT INTO sesiones (familia, usuario_id, tenant_id, rol, access_hash, refresh_hash, access_expira_en, familia_expira_en, dispositivo)
    VALUES (gen_random_uuid(), p_usuario_id, p_tenant_id, v_rol, p_access_hash, p_refresh_hash,
            now() + p_access_ttl, now() + p_refresh_ttl, p_dispositivo)
    RETURNING id, sesiones.rol, sesiones.access_expira_en, sesiones.familia_expira_en;
END $$;

-- Rota el refresco. estado: ok | invalido | caducado | inactivo | reutilizado (esta última revoca toda la familia).
CREATE FUNCTION auth_rotar_sesion(p_refresh_hash bytea, p_nuevo_access_hash bytea, p_nuevo_refresh_hash bytea,
                                  p_access_ttl interval, p_ip text, p_user_agent text)
RETURNS TABLE (estado text, sesion_id uuid, usuario_id bigint, tenant_id text, rol text,
               access_expira_en timestamptz, refresh_expira_en timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE s sesiones%ROWTYPE; v_rol text; v_nueva sesiones%ROWTYPE;
BEGIN
    SELECT * INTO s FROM sesiones WHERE refresh_hash = p_refresh_hash FOR UPDATE;
    IF NOT FOUND THEN RETURN QUERY SELECT 'invalido', NULL::uuid, NULL::bigint, NULL::text, NULL::text, NULL::timestamptz, NULL::timestamptz; RETURN; END IF;
    IF s.reemplazada_por IS NOT NULL THEN
        UPDATE sesiones SET revocada_en = now() WHERE familia = s.familia AND revocada_en IS NULL;
        INSERT INTO auditoria_auth (usuario_id, tenant_id, evento, exito, ip, user_agent)
        VALUES (s.usuario_id, s.tenant_id, 'refresh_reutilizado', false, p_ip, p_user_agent);
        RETURN QUERY SELECT 'reutilizado', s.id, s.usuario_id, s.tenant_id, s.rol, NULL::timestamptz, NULL::timestamptz; RETURN;
    END IF;
    IF s.revocada_en IS NOT NULL THEN RETURN QUERY SELECT 'invalido', NULL::uuid, NULL::bigint, NULL::text, NULL::text, NULL::timestamptz, NULL::timestamptz; RETURN; END IF;
    IF s.familia_expira_en <= now() THEN RETURN QUERY SELECT 'caducado', s.id, s.usuario_id, s.tenant_id, s.rol, NULL::timestamptz, NULL::timestamptz; RETURN; END IF;
    v_rol := auth_rol_para(s.usuario_id, s.tenant_id);
    IF v_rol IS NULL THEN
        UPDATE sesiones SET revocada_en = now() WHERE familia = s.familia AND revocada_en IS NULL;
        INSERT INTO auditoria_auth (usuario_id, tenant_id, evento, exito, ip, user_agent)
        VALUES (s.usuario_id, s.tenant_id, 'refresh_sin_acceso', false, p_ip, p_user_agent);
        RETURN QUERY SELECT 'inactivo', s.id, s.usuario_id, s.tenant_id, s.rol, NULL::timestamptz, NULL::timestamptz; RETURN;
    END IF;
    INSERT INTO sesiones (familia, usuario_id, tenant_id, rol, access_hash, refresh_hash, access_expira_en, familia_expira_en, dispositivo)
    VALUES (s.familia, s.usuario_id, s.tenant_id, v_rol, p_nuevo_access_hash, p_nuevo_refresh_hash,
            now() + p_access_ttl, s.familia_expira_en, s.dispositivo)
    RETURNING * INTO v_nueva;
    UPDATE sesiones SET reemplazada_por = v_nueva.id WHERE id = s.id;
    RETURN QUERY SELECT 'ok', v_nueva.id, v_nueva.usuario_id, v_nueva.tenant_id, v_nueva.rol, v_nueva.access_expira_en, v_nueva.familia_expira_en;
END $$;

-- Valida un access token en cada petición. Anota el último uso como máximo una vez por minuto.
CREATE FUNCTION auth_validar_acceso(p_access_hash bytea)
RETURNS TABLE (sesion_id uuid, usuario_id bigint, tenant_id text, rol text, es_admin boolean)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE s sesiones%ROWTYPE; v_admin boolean;
BEGIN
    SELECT se.* INTO s FROM sesiones se JOIN usuarios u ON u.id = se.usuario_id AND u.activo
     WHERE se.access_hash = p_access_hash AND se.revocada_en IS NULL AND se.reemplazada_por IS NULL
       AND se.access_expira_en > now() AND se.familia_expira_en > now();
    IF NOT FOUND THEN RETURN; END IF;
    IF s.ultimo_uso IS NULL OR s.ultimo_uso < now() - interval '1 minute' THEN
        UPDATE sesiones SET ultimo_uso = now() WHERE id = s.id;
    END IF;
    SELECT u.es_admin INTO v_admin FROM usuarios u WHERE u.id = s.usuario_id;
    RETURN QUERY SELECT s.id, s.usuario_id, s.tenant_id, s.rol, v_admin;
END $$;

-- Cierre de sesión: revoca toda la familia del access token. Devuelve false si no existía.
CREATE FUNCTION auth_revocar_sesion(p_access_hash bytea) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE v_familia uuid;
BEGIN
    SELECT familia INTO v_familia FROM sesiones WHERE access_hash = p_access_hash;
    IF NOT FOUND THEN RETURN false; END IF;
    UPDATE sesiones SET revocada_en = now() WHERE familia = v_familia AND revocada_en IS NULL;
    RETURN true;
END $$;

-- Revoca todas las sesiones activas de un usuario (al desactivarlo). Devuelve cuántas filas revocó.
CREATE FUNCTION auth_revocar_usuario(p_usuario_id bigint) RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE n integer;
BEGIN
    UPDATE sesiones SET revocada_en = now() WHERE usuario_id = p_usuario_id AND revocada_en IS NULL AND reemplazada_por IS NULL;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END $$;

-- Solo tfg_app puede ejecutar las funciones de cara a la API; la interna y el resto, nadie más.
REVOKE ALL ON FUNCTION auth_rol_para(bigint, text), auth_credencial(text), auth_registrar_intento(text, bigint, boolean, text, text),
    auth_negocios_usuario(bigint), auth_crear_sesion(bigint, text, bytea, bytea, interval, interval, text),
    auth_rotar_sesion(bytea, bytea, bytea, interval, text, text), auth_validar_acceso(bytea),
    auth_revocar_sesion(bytea), auth_revocar_usuario(bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_credencial(text), auth_registrar_intento(text, bigint, boolean, text, text),
    auth_negocios_usuario(bigint), auth_crear_sesion(bigint, text, bytea, bytea, interval, interval, text),
    auth_rotar_sesion(bytea, bytea, bytea, interval, text, text), auth_validar_acceso(bytea),
    auth_revocar_sesion(bytea), auth_revocar_usuario(bigint) TO tfg_app;

COMMIT;
