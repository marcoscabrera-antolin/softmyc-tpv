import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { authMiddleware, requireRole, requireTenant } from './auth.js';
import { createAuthRouter, sessionBody } from './authRoutes.js';
import { createAdminRouter } from './adminRoutes.js';
import { withTenant } from './db.js';
import { parseOrder, syncBodySchema, stockBodySchema, codigoSchema, productoBodySchema } from './validate.js';

// Idempotencia por uuid. La app edita ventas reenviando el mismo uuid con datos nuevos, así que la reescritura se acepta,
// pero cada cambio real queda en ventas_historial (disparador de la migración 2026-10-16-api-seguridad.sql).
const SQL_GUARDAR_VENTA = `INSERT INTO ventas (tenant_id, uuid, id_local, fecha, total, items, metodo_pago)
  VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7) ON CONFLICT (tenant_id, uuid) DO UPDATE
    SET fecha = EXCLUDED.fecha, total = EXCLUDED.total, items = EXCLUDED.items, metodo_pago = EXCLUDED.metodo_pago`;
/** Errores de datos de PostgreSQL (clases 22 y 23): afectan a una venta concreta, no a la conexión. */
const ERRORES_DE_DATOS = /^(22|23)/;

/**
 * Guarda cada venta en su propio SAVEPOINT dentro de la transacción del negocio: una fila que la base rechaza
 * se devuelve como rechazada y no arrastra al resto del lote. Otros errores (conexión, permisos) sí abortan el lote.
 */
export async function guardarVentas(db, tenantId, ventas) {
  const confirmadas = [];
  const rechazadas = [];
  for (const o of ventas) {
    await db.query('SAVEPOINT venta');
    try {
      await db.query(SQL_GUARDAR_VENTA, [tenantId, o.uuid, o.localId, o.fecha, o.total, JSON.stringify(o.items), o.metodo]);
      await db.query('RELEASE SAVEPOINT venta');
      confirmadas.push(o);
    } catch (err) {
      await db.query('ROLLBACK TO SAVEPOINT venta');
      await db.query('RELEASE SAVEPOINT venta');
      if (!ERRORES_DE_DATOS.test(err.code ?? '')) throw err;
      rechazadas.push({ localId: o.localId, index: o.index, reason: 'datos no admitidos por la base de datos' });
    }
  }
  return { confirmadas, rechazadas };
}

export function createApp(pool, { authPool, rateLimitMax = 300, loginLimitMax = 10, refreshLimitMax = 60, adminLimitMax = 30, allowApiKey = true,
                              accessTtlMin = 30, refreshTtlDays = 7, trustProxy = 1 } = {}) {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', trustProxy); // nginx del despliegue: X-Forwarded-For lo fija nginx con $remote_addr
  app.use(helmet());
  app.use(express.json({ limit: '512kb' }));
  app.use(rateLimit({ windowMs: 60_000, limit: rateLimitMax, standardHeaders: true, legacyHeaders: false }));

  app.get('/health', async (_req, res) => {
    try { await pool.query('SELECT 1'); res.json({ status: 'ok' }); }
    catch { res.status(503).json({ status: 'db_unavailable' }); }
  });

  const authenticate = authMiddleware(pool, { allowApiKey });
  if (authPool) {
    app.use('/api/tpv/auth', createAuthRouter({ authPool, authenticate, loginLimitMax, refreshLimitMax, accessTtlMin, refreshTtlDays }));
    app.use('/api/admin', createAdminRouter({
      authPool, authenticate, accessTtlMin, refreshTtlDays, adminLimitMax,
      sessionBody: (s, user) => sessionBody(s, { accessSeconds: accessTtlMin * 60 }, user),
    }));
  }

  const api = express.Router();
  api.use(authenticate, requireTenant);

  // Subida de ventas desde la tablet (contrato de OrderSyncService.syncLocalOrders). Idempotente por uuid.
  api.post('/orders/sync', async (req, res, next) => {
    try {
      const body = syncBodySchema.safeParse(req.body);
      if (!body.success) return res.status(400).json({ success: false, error: 'Cuerpo inválido' });
      const syncedIds = [];
      const rejected = [];
      const valid = [];
      body.data.orders.forEach((raw, index) => {
        const r = parseOrder(raw, req.tenantId);
        if (r.ok) valid.push({ index, raw, ...r.order });
        else rejected.push({ localId: raw?.localId ?? null, index, reason: r.reason });
      });
      const guardadas = await withTenant(pool, req.tenantId, (db) => guardarVentas(db, req.tenantId, valid));
      for (const o of guardadas.confirmadas) {
        if (o.localId !== null) syncedIds.push(o.localId); // reintento = ya estaba: también se confirma
      }
      rejected.push(...guardadas.rechazadas);
      res.json({ success: true, syncedIds, rejected });
    } catch (err) { next(err); }
  });

  // Catálogo del negocio (formato que espera PedidoViewModel: products, extras y salsas).
  api.get('/products', async (req, res, next) => {
    try {
      const out = await withTenant(pool, req.tenantId, async (db) => ({
        products: (await db.query(
          `SELECT codigo, nombre, precio, categoria, ingredientes, unidad_medida AS "unidadMedida", stock
             FROM productos ORDER BY codigo`)).rows,
        extras: (await db.query('SELECT nombre, precio FROM extras ORDER BY nombre')).rows,
        salsas: (await db.query('SELECT nombre FROM salsas ORDER BY nombre')).rows.map((r) => r.nombre),
      }));
      res.json({ success: true, ...out });
    } catch (err) { next(err); }
  });

  // Alta o edición de un producto (upsert por código). Sin stock ni unidad en el cuerpo se conservan los existentes.
  api.post('/products', requireRole('encargado', 'admin'), async (req, res, next) => {
    try {
      const body = productoBodySchema.safeParse(req.body);
      if (!body.success) return res.status(400).json({ success: false, error: 'Datos inválidos' });
      const p = body.data;
      await withTenant(pool, req.tenantId, async (db) => db.query(
        `INSERT INTO productos (tenant_id, codigo, nombre, precio, categoria, ingredientes, unidad_medida, stock)
         VALUES ($1,$2,$3,$4,$5,$6,COALESCE($7,'UD'),COALESCE($8,-1))
         ON CONFLICT (tenant_id, codigo) DO UPDATE
           SET nombre = EXCLUDED.nombre, precio = EXCLUDED.precio, categoria = EXCLUDED.categoria,
               ingredientes = EXCLUDED.ingredientes,
               unidad_medida = COALESCE($7, productos.unidad_medida),
               stock = COALESCE($8, productos.stock),
               actualizado_en = now()`,
        [req.tenantId, p.codigo, p.nombre, p.precio, p.categoria, p.ingredientes, p.unidadMedida ?? null, p.stock ?? null]));
      res.json({ success: true });
    } catch (err) { next(err); }
  });

  api.delete('/products/:codigo', requireRole('encargado', 'admin'), async (req, res, next) => {
    try {
      const codigo = codigoSchema.safeParse(req.params.codigo);
      if (!codigo.success) return res.status(400).json({ success: false, error: 'Datos inválidos' });
      const n = await withTenant(pool, req.tenantId, async (db) =>
        (await db.query('DELETE FROM productos WHERE codigo = $1', [codigo.data])).rowCount);
      if (n === 0) return res.status(404).json({ success: false, error: 'Producto no encontrado' });
      res.json({ success: true });
    } catch (err) { next(err); }
  });

  // Ajuste de stock de un producto (-1 = sin control).
  api.put('/products/:codigo/stock', requireRole('encargado', 'admin'), async (req, res, next) => {
    try {
      const codigo = codigoSchema.safeParse(req.params.codigo);
      const body = stockBodySchema.safeParse(req.body);
      if (!codigo.success || !body.success) return res.status(400).json({ success: false, error: 'Datos inválidos' });
      const n = await withTenant(pool, req.tenantId, async (db) =>
        (await db.query('UPDATE productos SET stock = $1, actualizado_en = now() WHERE codigo = $2',
          [body.data.stock, codigo.data])).rowCount);
      if (n === 0) return res.status(404).json({ success: false, error: 'Producto no encontrado' });
      res.json({ success: true });
    } catch (err) { next(err); }
  });

  app.use('/api/tpv', api);
  app.use((_req, res) => res.status(404).json({ success: false, error: 'No encontrado' }));
  // eslint-disable-next-line no-unused-vars
  app.use((err, _req, res, _next) => {
    if (err?.code === 'BUSY') return res.status(503).json({ success: false, error: 'Servidor ocupado; inténtalo de nuevo' });
    if (err?.type === 'entity.parse.failed') return res.status(400).json({ success: false, error: 'JSON inválido' });
    if (err?.type === 'entity.too.large') return res.status(413).json({ success: false, error: 'Cuerpo demasiado grande' });
    console.error(JSON.stringify({ level: 'error', msg: err?.message, code: err?.code }));
    res.status(500).json({ success: false, error: 'Error interno' });
  });
  return app;
}
