-- Esquema del TFG SoftMYC TPV (PostgreSQL 16).
-- Refleja las entidades Room de la app (productos, extras, salsas, ventas) con soporte multi-tenant
-- por columna tenant_id en lugar de una base SQLite por negocio.
BEGIN;

CREATE TABLE tenants (
    id         text PRIMARY KEY CHECK (id ~ '^[a-z0-9_]{2,40}$'),
    nombre     text NOT NULL CHECK (length(btrim(nombre)) > 0),
    sector     text NOT NULL CHECK (length(btrim(sector)) > 0),
    activo     boolean NOT NULL DEFAULT true,
    creado_en  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE tenants IS 'Negocios (tenants). El id coincide con el restaurant_id normalizado de la app (sin prefijo softmyc).';

-- ProductoLocal. stock = -1 significa "sin control de stock" (misma convención que la app).
CREATE TABLE productos (
    tenant_id      text NOT NULL REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    codigo         integer NOT NULL CHECK (codigo > 0),
    nombre         text NOT NULL CHECK (length(btrim(nombre)) > 0),
    precio         numeric(10,2) NOT NULL CHECK (precio >= 0),
    categoria      text NOT NULL DEFAULT '',
    ingredientes   text[] NOT NULL DEFAULT '{}',
    unidad_medida  text NOT NULL DEFAULT 'UD' CHECK (length(btrim(unidad_medida)) > 0),
    stock          integer NOT NULL DEFAULT -1 CHECK (stock >= -1),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, codigo)
);
CREATE INDEX productos_categoria_idx ON productos (tenant_id, categoria);
CREATE INDEX productos_stock_critico_idx ON productos (tenant_id, stock) WHERE stock >= 0;

CREATE TABLE extras (
    tenant_id text NOT NULL REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    nombre    text NOT NULL CHECK (length(btrim(nombre)) > 0),
    precio    numeric(10,2) NOT NULL CHECK (precio >= 0),
    PRIMARY KEY (tenant_id, nombre)
);

CREATE TABLE salsas (
    tenant_id text NOT NULL REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    nombre    text NOT NULL CHECK (length(btrim(nombre)) > 0),
    PRIMARY KEY (tenant_id, nombre)
);

-- VentaLocal. id_local es el id autoincremental de Room en la tablet: junto con tenant_id y fecha
-- hace idempotente la subida (reintentar un envío no duplica la venta).
CREATE TABLE ventas (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   text NOT NULL REFERENCES tenants(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    id_local    bigint NOT NULL CHECK (id_local > 0),
    fecha       timestamptz NOT NULL CHECK (fecha <= now() + interval '1 day'),
    total       numeric(10,2) NOT NULL CHECK (total >= 0),
    items       jsonb NOT NULL CHECK (jsonb_typeof(items) = 'array' AND pg_column_size(items) < 262144),
    metodo_pago text NOT NULL CHECK (metodo_pago IN ('Efectivo', 'Tarjeta')),
    recibida_en timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id_local, fecha)
);
CREATE INDEX ventas_tenant_fecha_idx ON ventas (tenant_id, fecha DESC);
CREATE INDEX ventas_items_gin_idx ON ventas USING gin (items jsonb_path_ops);
COMMENT ON COLUMN ventas.items IS 'itemsJson de la app: nombre, cantidad, precio_unitario, categoria, importe_linea, extras, quitados, salsas.';

-- Una fila por línea de ticket (para arqueo de cocina y analítica).
CREATE VIEW venta_lineas WITH (security_invoker = true) AS
SELECT v.id AS venta_id, v.tenant_id, v.fecha, v.metodo_pago,
       l.item->>'nombre'                        AS nombre,
       (l.item->>'cantidad')::numeric           AS cantidad,
       (l.item->>'precio_unitario')::numeric    AS precio_unitario,
       l.item->>'categoria'                     AS categoria,
       (l.item->>'importe_linea')::numeric      AS importe_linea
FROM ventas v
CROSS JOIN LATERAL jsonb_array_elements(v.items) AS l(item);

CREATE VIEW ventas_diarias WITH (security_invoker = true) AS
SELECT tenant_id,
       (fecha AT TIME ZONE 'Europe/Madrid')::date AS dia,
       count(*)                                   AS tickets,
       sum(total)                                 AS total,
       sum(total) FILTER (WHERE metodo_pago = 'Efectivo') AS efectivo,
       sum(total) FILTER (WHERE metodo_pago = 'Tarjeta')  AS tarjeta
FROM ventas
GROUP BY tenant_id, (fecha AT TIME ZONE 'Europe/Madrid')::date;

-- Los tenants no se borran: se desactivan (activo = false). Las ventas son histórico y no referencian productos a propósito.
-- Permisos del rol de la aplicación (fijo: tfg_app): solo DML, nunca DDL.
GRANT USAGE ON SCHEMA public TO tfg_app;
GRANT SELECT ON tenants TO tfg_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON productos, extras, salsas TO tfg_app;
GRANT SELECT, INSERT ON ventas TO tfg_app;
GRANT SELECT ON venta_lineas, ventas_diarias TO tfg_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO tfg_app;

-- Datos semilla: los tenants que usa la app (TenantCatalogSeeder).
INSERT INTO tenants (id, nombre, sector) VALUES
    ('lanube',     'La Nube Bocatería',                 'Hostelería y Restauración'),
    ('elbocata',   'El Bocata Gourmet',                 'Hostelería y Restauración'),
    ('fruteria',   'Frutería El Huerto Bio',            'Comercio al Peso / Granel'),
    ('taller',     'Taller Mecánico MotorSport',        'Servicios y Automoción'),
    ('paqueteria', 'SoftMYC Express Paquetería',        'Logística y Transporte'),
    ('mayorista',  'Central Mayorista de Distribución', 'Venta y Distribución B2B');

COMMIT;
