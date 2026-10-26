-- Aplica a una base ya creada las mismas mejoras que ahora lleva initdb/ (revisión de seguridad).
BEGIN;
ALTER TABLE ventas ALTER COLUMN id_local TYPE bigint;
ALTER TABLE ventas ADD CONSTRAINT ventas_fecha_no_futura CHECK (fecha <= now() + interval '1 day');
ALTER TABLE ventas ADD CONSTRAINT ventas_items_tamano CHECK (pg_column_size(items) < 262144);
ALTER VIEW venta_lineas SET (security_invoker = true);
ALTER VIEW ventas_diarias SET (security_invoker = true);
ALTER TABLE productos DROP CONSTRAINT productos_tenant_id_fkey, ADD FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT;
ALTER TABLE extras    DROP CONSTRAINT extras_tenant_id_fkey,    ADD FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT;
ALTER TABLE salsas    DROP CONSTRAINT salsas_tenant_id_fkey,    ADD FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT;
ALTER TABLE ventas    DROP CONSTRAINT ventas_tenant_id_fkey,    ADD FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT;
REVOKE TEMP ON DATABASE tfg_softmyc_tpv FROM PUBLIC;
ALTER ROLE tfg_app CONNECTION LIMIT 10;
ALTER ROLE tfg_app SET statement_timeout = '5s';
COMMIT;
