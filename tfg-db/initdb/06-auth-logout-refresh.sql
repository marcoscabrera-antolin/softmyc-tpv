-- Cierre de sesión con el token de refresco: la app puede cerrar sesión aunque el acceso (30 min) ya haya caducado.
-- Sin esto, un logout con el acceso caducado dejaba viva la sesión y su refresco hasta 7 días.
BEGIN;
CREATE FUNCTION auth_revocar_refresh(p_refresh_hash bytea) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_familia uuid;
BEGIN
    SELECT familia INTO v_familia FROM sesiones WHERE refresh_hash = p_refresh_hash;
    IF NOT FOUND THEN RETURN false; END IF;
    UPDATE sesiones SET revocada_en = now() WHERE familia = v_familia AND revocada_en IS NULL;
    RETURN true;
END $$;
ALTER FUNCTION auth_revocar_refresh(bytea) OWNER TO tfg_auth_owner;
REVOKE ALL ON FUNCTION auth_revocar_refresh(bytea) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_revocar_refresh(bytea) TO tfg_auth;
COMMIT;
