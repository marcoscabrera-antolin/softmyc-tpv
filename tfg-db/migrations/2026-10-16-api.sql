-- Preparación para la API REST: UUID de venta, claves de API por negocio y seguridad por filas (RLS).
BEGIN;

-- Deduplicación robusta: el UUID lo genera la tablet. id_local queda solo como dato informativo.
ALTER TABLE ventas ADD COLUMN uuid uuid;
UPDATE ventas SET uuid = gen_random_uuid() WHERE uuid IS NULL;
ALTER TABLE ventas ALTER COLUMN uuid SET NOT NULL;
ALTER TABLE ventas ALTER COLUMN id_local DROP NOT NULL;
ALTER TABLE ventas DROP CONSTRAINT IF EXISTS ventas_tenant_id_id_local_fecha_key;
ALTER TABLE ventas ADD CONSTRAINT ventas_tenant_uuid_key UNIQUE (tenant_id, uuid);

-- Claves de API: solo se guarda el SHA-256; la clave en claro se muestra una vez al crearla.
CREATE TABLE IF NOT EXISTS api_keys (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  text NOT NULL REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    nombre     text NOT NULL CHECK (length(btrim(nombre)) > 0),
    key_hash   bytea NOT NULL UNIQUE CHECK (octet_length(key_hash) = 32),
    activa     boolean NOT NULL DEFAULT true,
    creada_en  timestamptz NOT NULL DEFAULT now(),
    ultimo_uso timestamptz
);
GRANT SELECT ON api_keys TO tfg_app;
GRANT UPDATE (ultimo_uso) ON api_keys TO tfg_app;

-- RLS: tfg_app solo ve y escribe filas del negocio fijado con SET LOCAL app.tenant en cada transacción.
-- Sin app.tenant no ve ninguna fila (current_setting(..., true) devuelve NULL).
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['productos','extras','salsas','ventas'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS aislamiento_tenant ON %I', t);
        EXECUTE format($p$CREATE POLICY aislamiento_tenant ON %I
                          USING (tenant_id = current_setting('app.tenant', true))
                          WITH CHECK (tenant_id = current_setting('app.tenant', true))$p$, t);
    END LOOP;
END $$;

COMMIT;
