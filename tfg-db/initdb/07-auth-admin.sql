-- Administración de cuentas (fase 3): funciones que usan los endpoints /api/admin/* y el script de usuarios.
-- Solo las ejecuta tfg_auth; la API exige además que la sesión sea de un administrador de plataforma.
-- Cada acción queda en auditoria_auth con su autor (username = 'actor:<id>', o 'actor:cli' desde el script).
BEGIN;

CREATE FUNCTION auth_admin_listar_usuarios()
RETURNS TABLE (id bigint, username text, nombre text, es_admin boolean, activo boolean,
               intentos_fallidos integer, bloqueado_hasta timestamptz, negocios jsonb)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
    SELECT u.id, u.username, u.nombre, u.es_admin, u.activo, u.intentos_fallidos, u.bloqueado_hasta,
           coalesce((SELECT jsonb_agg(jsonb_build_object('tenantId', un.tenant_id, 'rol', un.rol) ORDER BY un.tenant_id)
                       FROM usuario_negocios un WHERE un.usuario_id = u.id), '[]'::jsonb)
      FROM usuarios u ORDER BY u.username
$$;

CREATE FUNCTION auth_admin_crear_usuario(p_actor bigint, p_username text, p_nombre text, p_password_hash text,
                                         p_es_admin boolean, p_negocios jsonb)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_id bigint; n jsonb; v_negocios jsonb := coalesce(p_negocios, '[]'::jsonb);
BEGIN
    IF jsonb_typeof(v_negocios) <> 'array' THEN RAISE EXCEPTION 'negocios_invalidos' USING ERRCODE = '22023'; END IF;
    INSERT INTO usuarios (username, nombre, password_hash, es_admin)
    VALUES (lower(p_username), p_nombre, p_password_hash, coalesce(p_es_admin, false)) RETURNING id INTO v_id;
    FOR n IN SELECT * FROM jsonb_array_elements(v_negocios) LOOP
        INSERT INTO usuario_negocios (usuario_id, tenant_id, rol) VALUES (v_id, n->>'tenantId', n->>'rol');
    END LOOP;
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito)
    VALUES (v_id, 'actor:' || coalesce(p_actor::text, 'cli'), 'admin_crear_usuario', true);
    RETURN v_id;
END $$;

-- Los parámetros NULL no cambian nada. Desactivar revoca las sesiones. No se puede dejar la plataforma sin administradores
-- activos ni quitarse uno mismo el acceso de administrador (error 'auto_bloqueo' o 'ultimo_administrador').
CREATE FUNCTION auth_admin_actualizar_usuario(p_actor bigint, p_id bigint, p_nombre text, p_activo boolean,
                                              p_es_admin boolean, p_negocios jsonb)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE u usuarios%ROWTYPE; n jsonb; v_otros integer; v_actor text;
BEGIN
    PERFORM pg_advisory_xact_lock(7770001);       -- serializa los cambios de administradores: dos a la vez no dejan cero
    SELECT * INTO u FROM usuarios WHERE id = p_id FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;
    IF p_negocios IS NOT NULL AND jsonb_typeof(p_negocios) <> 'array' THEN RAISE EXCEPTION 'negocios_invalidos' USING ERRCODE = '22023'; END IF;
    IF u.es_admin AND u.activo AND (p_activo IS FALSE OR p_es_admin IS FALSE) THEN
        IF p_actor IS NOT NULL AND p_actor = p_id THEN RAISE EXCEPTION 'auto_bloqueo' USING ERRCODE = 'P0001'; END IF;
        SELECT count(*) INTO v_otros FROM usuarios WHERE es_admin AND activo AND id <> p_id;
        IF v_otros = 0 THEN RAISE EXCEPTION 'ultimo_administrador' USING ERRCODE = 'P0001'; END IF;
    END IF;
    UPDATE usuarios SET nombre = coalesce(p_nombre, nombre), activo = coalesce(p_activo, activo),
                        es_admin = coalesce(p_es_admin, es_admin) WHERE id = p_id;
    IF p_negocios IS NOT NULL THEN
        DELETE FROM usuario_negocios WHERE usuario_id = p_id;
        FOR n IN SELECT * FROM jsonb_array_elements(p_negocios) LOOP
            INSERT INTO usuario_negocios (usuario_id, tenant_id, rol) VALUES (p_id, n->>'tenantId', n->>'rol');
        END LOOP;
    END IF;
    -- Un usuario que no es administrador de plataforma no puede quedarse sin ningún negocio.
    IF (p_negocios IS NOT NULL OR p_es_admin IS FALSE)
       AND NOT (SELECT es_admin FROM usuarios WHERE id = p_id)
       AND NOT EXISTS (SELECT 1 FROM usuario_negocios WHERE usuario_id = p_id) THEN
        RAISE EXCEPTION 'sin_negocios' USING ERRCODE = 'P0001';
    END IF;
    IF p_activo IS FALSE THEN PERFORM auth_revocar_usuario(p_id); END IF;
    -- Un evento de auditoría por cada cambio real, para poder distinguir promover, degradar, desactivar o cambiar negocios.
    v_actor := 'actor:' || coalesce(p_actor::text, 'cli');
    IF p_nombre IS NOT NULL AND p_nombre IS DISTINCT FROM u.nombre THEN INSERT INTO auditoria_auth (usuario_id, username, evento, exito) VALUES (p_id, v_actor, 'admin_nombre', true); END IF;
    IF p_negocios IS NOT NULL THEN INSERT INTO auditoria_auth (usuario_id, username, evento, exito) VALUES (p_id, v_actor, 'admin_negocios', true); END IF;
    IF p_es_admin IS TRUE AND NOT u.es_admin THEN INSERT INTO auditoria_auth (usuario_id, username, evento, exito) VALUES (p_id, v_actor, 'admin_promover', true); END IF;
    IF p_es_admin IS FALSE AND u.es_admin THEN INSERT INTO auditoria_auth (usuario_id, username, evento, exito) VALUES (p_id, v_actor, 'admin_degradar', true); END IF;
    IF p_activo IS FALSE AND u.activo THEN INSERT INTO auditoria_auth (usuario_id, username, evento, exito) VALUES (p_id, v_actor, 'admin_desactivar', true); END IF;
    IF p_activo IS TRUE AND NOT u.activo THEN INSERT INTO auditoria_auth (usuario_id, username, evento, exito) VALUES (p_id, v_actor, 'admin_activar', true); END IF;
    RETURN true;
END $$;

-- Cambia la contraseña, desbloquea la cuenta y revoca todas sus sesiones.
CREATE FUNCTION auth_admin_cambiar_password(p_actor bigint, p_id bigint, p_password_hash text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    UPDATE usuarios SET password_hash = p_password_hash, pwd_cambiada_en = now(), intentos_fallidos = 0, bloqueado_hasta = NULL
     WHERE id = p_id;
    IF NOT FOUND THEN RETURN false; END IF;
    PERFORM auth_revocar_usuario(p_id);
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito)
    VALUES (p_id, 'actor:' || coalesce(p_actor::text, 'cli'), 'admin_cambiar_password', true);
    RETURN true;
END $$;

CREATE FUNCTION auth_admin_desbloquear(p_actor bigint, p_id bigint)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = p_id;
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito)
    VALUES (p_id, 'actor:' || coalesce(p_actor::text, 'cli'), 'admin_desbloquear', true);
    RETURN true;
END $$;

-- Credencial de un usuario por id (para reconfirmar la contraseña del administrador y validar la de otro usuario).
CREATE FUNCTION auth_admin_credencial(p_id bigint)
RETURNS TABLE (username text, password_hash text, es_admin boolean, activo boolean, bloqueado_hasta timestamptz)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
    SELECT username, password_hash, es_admin, activo, bloqueado_hasta FROM usuarios WHERE id = p_id
$$;

-- Cambio de negocio de un administrador, atómico: revoca la sesión actual (y solo esa) y abre la nueva. No expulsa las
-- sesiones del mismo administrador en otros dispositivos, que es lo que ocurriría si primero se creara la nueva.
CREATE FUNCTION auth_admin_cambiar_negocio(p_usuario_id bigint, p_old_access_hash bytea, p_tenant_id text, p_access_hash bytea,
                                           p_refresh_hash bytea, p_access_ttl interval, p_refresh_ttl interval, p_dispositivo text)
RETURNS TABLE (sesion_id uuid, rol text, access_expira_en timestamptz, refresh_expira_en timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_familia uuid;
BEGIN
    IF p_tenant_id IS NULL OR auth_rol_para(p_usuario_id, NULL) IS DISTINCT FROM 'admin' OR auth_rol_para(p_usuario_id, p_tenant_id) IS NULL THEN
        RAISE EXCEPTION 'acceso_denegado' USING ERRCODE = '42501';
    END IF;
    SELECT familia INTO v_familia FROM sesiones
     WHERE access_hash = p_old_access_hash AND usuario_id = p_usuario_id AND revocada_en IS NULL AND reemplazada_por IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'acceso_denegado' USING ERRCODE = '42501'; END IF;
    UPDATE sesiones SET revocada_en = now() WHERE familia = v_familia AND revocada_en IS NULL;
    INSERT INTO auditoria_auth (usuario_id, tenant_id, username, evento, exito)
    VALUES (p_usuario_id, p_tenant_id, 'actor:' || p_usuario_id::text, 'admin_switch', true);
    RETURN QUERY SELECT * FROM auth_crear_sesion(p_usuario_id, p_tenant_id, p_access_hash, p_refresh_hash, p_access_ttl, p_refresh_ttl, p_dispositivo);
END $$;

ALTER FUNCTION auth_admin_credencial(bigint) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_admin_cambiar_negocio(bigint, bytea, text, bytea, bytea, interval, interval, text) OWNER TO tfg_auth_owner;
REVOKE ALL ON FUNCTION auth_admin_credencial(bigint), auth_admin_cambiar_negocio(bigint, bytea, text, bytea, bytea, interval, interval, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_admin_credencial(bigint), auth_admin_cambiar_negocio(bigint, bytea, text, bytea, bytea, interval, interval, text) TO tfg_auth;
ALTER FUNCTION auth_admin_listar_usuarios() OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_admin_crear_usuario(bigint, text, text, text, boolean, jsonb) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_admin_actualizar_usuario(bigint, bigint, text, boolean, boolean, jsonb) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_admin_cambiar_password(bigint, bigint, text) OWNER TO tfg_auth_owner;
ALTER FUNCTION auth_admin_desbloquear(bigint, bigint) OWNER TO tfg_auth_owner;
REVOKE ALL ON FUNCTION auth_admin_listar_usuarios(), auth_admin_crear_usuario(bigint, text, text, text, boolean, jsonb),
    auth_admin_actualizar_usuario(bigint, bigint, text, boolean, boolean, jsonb), auth_admin_cambiar_password(bigint, bigint, text),
    auth_admin_desbloquear(bigint, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_admin_listar_usuarios(), auth_admin_crear_usuario(bigint, text, text, text, boolean, jsonb),
    auth_admin_actualizar_usuario(bigint, bigint, text, boolean, boolean, jsonb), auth_admin_cambiar_password(bigint, bigint, text),
    auth_admin_desbloquear(bigint, bigint) TO tfg_auth;
COMMIT;
