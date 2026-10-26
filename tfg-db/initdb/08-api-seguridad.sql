-- Instalaciones nuevas: copia de migrations/2026-10-16-api-seguridad.sql (mantener ambas iguales).
-- Endurecimiento de la API tras la auditoría del 2026-10-16 (SEG-API-03 y SEG-API-02). Fichero nuevo: no edita migraciones previas.
--
-- SEG-API-03: el bloqueo por intentos fallidos era por cuenta. Cualquiera que conociera un usuario (p. ej. "admin") podía
-- bloquearlo desde cualquier IP. Ahora el bloqueo se aplica por (usuario, IP de origen): un atacante solo bloquea su propio
-- origen y el acceso legítimo desde otra red sigue funcionando. Se conservan usuarios.intentos_fallidos y bloqueado_hasta
-- como resumen (y para las pruebas existentes), pero ya no deciden el acceso. Limitación residual: si el atacante comparte IP
-- con el local (NAT), puede bloquear esas tablets durante el tiempo de bloqueo; el limitador por IP de la API acota la tasa.
--
-- SEG-API-02: la app edita ventas reenviando el mismo uuid, así que no se puede prohibir la reescritura sin romper ese caso.
-- Se registra cada cambio real de una venta en ventas_historial (valores anteriores, rol de conexión y momento), para
-- que una sobrescritura deje rastro y pueda revisarse.
BEGIN;

-- 1) Intentos fallidos por cuenta y origen. Solo lo tocan funciones SECURITY DEFINER (tfg_auth_owner); ningún rol directo.
CREATE TABLE IF NOT EXISTS login_intentos_ip (
    usuario_id      bigint NOT NULL REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE,
    ip              text NOT NULL CHECK (length(ip) <= 45),
    fallos          integer NOT NULL DEFAULT 0 CHECK (fallos >= 0),
    bloqueado_hasta timestamptz,
    actualizado_en  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, ip)
);
ALTER TABLE login_intentos_ip OWNER TO tfg_auth_owner;
REVOKE ALL ON login_intentos_ip FROM PUBLIC;

-- Bloqueo vigente para un origen concreto (NULL si no hay). Lo usa la API antes de comprobar la contraseña.
CREATE OR REPLACE FUNCTION auth_bloqueo_vigente(p_usuario_id bigint, p_ip text)
RETURNS timestamptz LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
    SELECT max(bloqueado_hasta) FROM login_intentos_ip
     WHERE usuario_id = p_usuario_id AND ip = left(coalesce(p_ip, ''), 45) AND bloqueado_hasta > now();
$$;
REVOKE ALL ON FUNCTION auth_bloqueo_vigente(bigint, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_bloqueo_vigente(bigint, text) TO tfg_auth;
-- Propietario sin privilegios (como el resto de funciones de auth, ver initdb/05): una función SECURITY DEFINER
-- propiedad del superusuario que ejecuta tfg_auth deshacería ese aislamiento.
ALTER FUNCTION auth_bloqueo_vigente(bigint, text) OWNER TO tfg_auth_owner;

-- Mismo contrato que antes (devuelve el bloqueo vigente o NULL), pero el bloqueo y el contador son por (usuario, IP).
-- Un fallo desde un origen bloqueado no se cuenta; un acierto desde otro origen no levanta el bloqueo ajeno.
CREATE OR REPLACE FUNCTION auth_registrar_intento(p_username text, p_usuario_id bigint, p_ok boolean, p_ip text, p_user_agent text)
RETURNS timestamptz LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
    v_ip text := left(coalesce(p_ip, ''), 45);
    v_fila login_intentos_ip%ROWTYPE;
    v_fallos integer;
    v_bloqueo timestamptz;
BEGIN
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito, ip, user_agent)
    VALUES (p_usuario_id, left(p_username, 64), 'login', p_ok, left(p_ip, 45), left(p_user_agent, 200));
    IF p_usuario_id IS NULL THEN RETURN NULL; END IF;
    PERFORM 1 FROM usuarios WHERE id = p_usuario_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT * INTO v_fila FROM login_intentos_ip WHERE usuario_id = p_usuario_id AND ip = v_ip FOR UPDATE;
    IF v_fila.bloqueado_hasta IS NOT NULL AND v_fila.bloqueado_hasta > now() THEN RETURN v_fila.bloqueado_hasta; END IF;

    IF p_ok THEN
        DELETE FROM login_intentos_ip WHERE usuario_id = p_usuario_id AND ip = v_ip;
        UPDATE usuarios SET intentos_fallidos = 0,
               bloqueado_hasta = (SELECT max(bloqueado_hasta) FROM login_intentos_ip
                                   WHERE usuario_id = p_usuario_id AND bloqueado_hasta > now())
         WHERE id = p_usuario_id;
        RETURN NULL;
    END IF;

    -- Purga de orígenes antiguos sin bloqueo de este usuario (evita que crezca sin límite).
    DELETE FROM login_intentos_ip WHERE usuario_id = p_usuario_id
       AND actualizado_en < now() - interval '1 day' AND coalesce(bloqueado_hasta, 'epoch'::timestamptz) < now();

    v_fallos := coalesce(v_fila.fallos, 0) + 1;
    IF v_fallos >= 5 THEN
        v_bloqueo := now() + make_interval(mins => least(15 * power(2, least((v_fallos - 5) / 5, 7))::int, 1440));
    END IF;
    INSERT INTO login_intentos_ip (usuario_id, ip, fallos, bloqueado_hasta, actualizado_en)
    VALUES (p_usuario_id, v_ip, v_fallos, v_bloqueo, now())
    ON CONFLICT (usuario_id, ip) DO UPDATE SET fallos = EXCLUDED.fallos, bloqueado_hasta = EXCLUDED.bloqueado_hasta,
                                               actualizado_en = now();

    -- Resumen por cuenta (no decide el acceso): total de fallos desde el último acierto de cada origen y último bloqueo.
    UPDATE usuarios SET intentos_fallidos = least(intentos_fallidos + 1, 1000000),
           bloqueado_hasta = greatest(bloqueado_hasta, v_bloqueo)
     WHERE id = p_usuario_id;
    RETURN v_bloqueo;
END $$;

-- El desbloqueo y el cambio de contraseña del administrador también levantan los bloqueos por origen.
CREATE OR REPLACE FUNCTION auth_admin_desbloquear(p_actor bigint, p_id bigint)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = p_id;
    IF NOT FOUND THEN RETURN false; END IF;
    DELETE FROM login_intentos_ip WHERE usuario_id = p_id;
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito)
    VALUES (p_id, 'actor:' || coalesce(p_actor::text, 'cli'), 'admin_desbloquear', true);
    RETURN true;
END $$;

CREATE OR REPLACE FUNCTION auth_admin_cambiar_password(p_actor bigint, p_id bigint, p_password_hash text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    UPDATE usuarios SET password_hash = p_password_hash, pwd_cambiada_en = now(), intentos_fallidos = 0, bloqueado_hasta = NULL
     WHERE id = p_id;
    IF NOT FOUND THEN RETURN false; END IF;
    DELETE FROM login_intentos_ip WHERE usuario_id = p_id;
    PERFORM auth_revocar_usuario(p_id);
    INSERT INTO auditoria_auth (usuario_id, username, evento, exito)
    VALUES (p_id, 'actor:' || coalesce(p_actor::text, 'cli'), 'admin_cambiar_password', true);
    RETURN true;
END $$;

-- 2) Historial de reescrituras de ventas (SEG-API-02). Solo lo escribe el disparador; ningún rol de la API lo lee ni lo modifica.
CREATE TABLE IF NOT EXISTS ventas_historial (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id             bigint NOT NULL,
    tenant_id            text NOT NULL,
    uuid                 uuid NOT NULL,
    fecha_anterior       timestamptz NOT NULL,
    total_anterior       numeric(10,2) NOT NULL,
    items_anteriores     jsonb NOT NULL,
    metodo_pago_anterior text NOT NULL,
    conexion             text NOT NULL,
    reescrita_en         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ventas_historial_venta_idx ON ventas_historial (tenant_id, uuid, reescrita_en);

CREATE OR REPLACE FUNCTION ventas_registrar_reescritura() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    INSERT INTO ventas_historial (venta_id, tenant_id, uuid, fecha_anterior, total_anterior, items_anteriores,
                                  metodo_pago_anterior, conexion)
    VALUES (OLD.id, OLD.tenant_id, OLD.uuid, OLD.fecha, OLD.total, OLD.items, OLD.metodo_pago, session_user);
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS ventas_historial_trg ON ventas;
CREATE TRIGGER ventas_historial_trg BEFORE UPDATE ON ventas FOR EACH ROW
    WHEN (OLD.fecha IS DISTINCT FROM NEW.fecha OR OLD.total IS DISTINCT FROM NEW.total
          OR OLD.items IS DISTINCT FROM NEW.items OR OLD.metodo_pago IS DISTINCT FROM NEW.metodo_pago)
    EXECUTE FUNCTION ventas_registrar_reescritura();

REVOKE ALL ON ventas_historial FROM PUBLIC;
REVOKE ALL ON FUNCTION ventas_registrar_reescritura() FROM PUBLIC;
-- El disparador solo escribe en su propia tabla de historial: ambos pasan a un propietario sin acceso a las ventas.
-- Ningún rol de la API puede leer ni modificar el historial (solo el superusuario); no se activa RLS porque nadie
-- más tiene SELECT. Si algún día se concede lectura a tfg_app, habrá que añadir ENABLE/FORCE RLS con la política
-- de aislamiento por negocio. La tabla crece sin purga automática (una fila por reescritura real).
ALTER TABLE ventas_historial OWNER TO tfg_auth_owner;
ALTER FUNCTION ventas_registrar_reescritura() OWNER TO tfg_auth_owner;

COMMIT;
