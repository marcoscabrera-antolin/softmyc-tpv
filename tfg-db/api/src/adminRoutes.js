import express from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { clientKey } from './clientKey.js';
import { normalizeTenant } from './auth.js';
import { hashPassword, verifyPassword, verifyAgainstDummy, parsePhc } from './password.js';
import { validatePassword } from './passwordPolicy.js';
import { newAccessToken, newRefreshToken, hashToken, bearerToken } from './tokens.js';

const ROLES = ['admin', 'encargado', 'tpv_operator'];
const USERNAME_RE = /^[a-z0-9._-]{3,40}$/;
const negocioSchema = z.object({
  tenantId: z.string().max(60).transform((v) => normalizeTenant(v)).refine((v) => v !== null, 'negocio no válido'),
  rol: z.enum(ROLES),
}).strict();
const negociosSchema = z.array(negocioSchema).max(20);
const createSchema = z.object({
  username: z.string().max(60).transform((v) => v.trim().toLowerCase()).refine((v) => USERNAME_RE.test(v), 'usuario no válido'),
  nombre: z.string().trim().min(1).max(80),
  password: z.string().max(200),
  adminPassword: z.string().max(200).optional(),
  esAdmin: z.boolean().optional(),
  negocios: negociosSchema.default([]),
}).strict();
const patchSchema = z.object({
  nombre: z.string().trim().min(1).max(80).optional(),
  activo: z.boolean().optional(),
  esAdmin: z.boolean().optional(),
  negocios: negociosSchema.optional(),
  adminPassword: z.string().max(200).optional(),
}).strict().refine((b) => Object.keys(b).length > 0, 'nada que cambiar');
const passwordSchema = z.object({ password: z.string().max(200), adminPassword: z.string().max(200).optional() }).strict();
const switchSchema = z.object({ restaurantId: z.string().max(60) }).strict();
const idSchema = z.coerce.number().int().positive();

const bad = (res, msg) => res.status(400).json({ success: false, error: msg });
const toUser = (r) => ({ id: Number(r.id), username: r.username, nombre: r.nombre, esAdmin: r.es_admin, activo: r.activo,
  intentosFallidos: r.intentos_fallidos, bloqueadoHasta: r.bloqueado_hasta, negocios: r.negocios });

/** Traduce los errores de las funciones SQL de administración a respuestas HTTP. */
function sqlError(err, res) {
  if (err.code === '23505') return res.status(409).json({ success: false, error: 'Ese usuario ya existe o hay un negocio repetido' });
  if (['23503', '23514', '22023'].includes(err.code)) return bad(res, 'Datos no válidos (negocio inexistente, rol o formato incorrecto)');
  if (err.code === 'P0001' && /ultimo_administrador/.test(err.message)) return res.status(409).json({ success: false, error: 'Debe quedar al menos un administrador activo' });
  if (err.code === 'P0001' && /sin_negocios/.test(err.message)) return bad(res, 'Un usuario que no es administrador necesita al menos un negocio');
  if (err.code === 'P0001' && /auto_bloqueo/.test(err.message)) return res.status(409).json({ success: false, error: 'No puedes quitarte a ti mismo el acceso de administrador' });
  return null;
}

/**
 * Administración de plataforma. Exige un Bearer de un administrador (la clave de API no llega aquí) y usa la conexión tfg_auth.
 */
export function createAdminRouter({ authPool, authenticate, sessionBody, adminLimitMax = 30, accessTtlMin = 30, refreshTtlDays = 7 }) {
  const router = express.Router();
  router.use((req, res, next) => (req.get('authorization') ? next() : res.status(401).json({ success: false, error: 'Autenticación requerida' })));
  router.use(authenticate);
  router.use((req, res, next) => (req.auth?.isAdmin && !req.auth.legacy ? next() : res.status(403).json({ success: false, error: 'Solo administradores' })));
  router.use(rateLimit({ windowMs: 60_000, limit: adminLimitMax, standardHeaders: true, legacyHeaders: false, keyGenerator: clientKey,
    message: { success: false, error: 'Demasiadas peticiones; inténtalo más tarde' } }));
  router.use((_req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });

  const listUsers = async () => (await authPool.query('SELECT * FROM auth_admin_listar_usuarios()')).rows;

  /**
   * Reconfirma la contraseña del administrador que hace la petición (un token robado no basta para crear o promover
   * administradores ni para cambiar la contraseña de otro). Un fallo cuenta como intento fallido y puede bloquear la cuenta.
   */
  async function confirmAdmin(req, adminPassword) {
    if (typeof adminPassword !== 'string' || adminPassword === '') return false;
    const { rows: [c] } = await authPool.query('SELECT * FROM auth_admin_credencial($1)', [req.auth.userId]);
    if (!c) return false;
    const passOk = parsePhc(c.password_hash) ? await verifyPassword(adminPassword, c.password_hash) : await verifyAgainstDummy(adminPassword);
    const { rows: [{ b: bloqueo }] } = await authPool.query('SELECT auth_bloqueo_vigente($1,$2) AS b',
      [req.auth.userId, String(req.ip ?? '').slice(0, 45)]);
    const acierto = passOk && c.activo && !bloqueo;
    const { rows: [{ b }] } = await authPool.query('SELECT auth_registrar_intento($1,$2,$3,$4,$5) AS b',
      [c.username, req.auth.userId, acierto, String(req.ip ?? '').slice(0, 45), String(req.get('user-agent') ?? '').slice(0, 200)]);
    return acierto && !b;
  }
  const needConfirm = (res) => res.status(403).json({ success: false, error: 'Confirma tu contraseña de administrador' });

  router.get('/restaurants', async (req, res, next) => {
    try {
      const { rows } = await authPool.query('SELECT * FROM auth_negocios_usuario($1)', [req.auth.userId]);
      res.json({ success: true, restaurants: rows.map((r) => ({ id: r.tenant_id, nombre: r.nombre, activo: true })) });
    } catch (err) { next(err); }
  });

  router.post('/switch-restaurant', async (req, res, next) => {
    try {
      const body = switchSchema.safeParse(req.body);
      const tenant = body.success ? normalizeTenant(body.data.restaurantId) : null;
      if (!tenant) return bad(res, 'Negocio no válido');
      const token = newAccessToken(), refreshToken = newRefreshToken();
      let s;
      try {
        s = (await authPool.query('SELECT * FROM auth_admin_cambiar_negocio($1,$2,$3,$4,$5,$6::interval,$7::interval,$8)',
          [req.auth.userId, hashToken(bearerToken(req)), tenant, hashToken(token), hashToken(refreshToken), `${accessTtlMin} minutes`, `${refreshTtlDays} days`, String(req.get('user-agent') ?? '').slice(0, 100)])).rows[0];
      } catch (err) {
        if (err.code === '42501') return res.status(403).json({ success: false, error: 'No tienes acceso a ese negocio' });
        throw err;
      }
      res.json(sessionBody({ token, refreshToken, refreshExpira: s.refresh_expira_en }, { restaurantId: tenant, role: s.rol }));
    } catch (err) { next(err); }
  });

  router.get('/users', async (_req, res, next) => {
    try { res.json({ success: true, users: (await listUsers()).map(toUser) }); } catch (err) { next(err); }
  });

  router.post('/users', async (req, res, next) => {
    try {
      const body = createSchema.safeParse(req.body);
      if (!body.success) return bad(res, 'Datos no válidos: ' + body.error.issues.map((i) => i.path.join('.') || i.message).join(', '));
      const b = body.data;
      const motivo = validatePassword(b.password, b.username);
      if (motivo) return bad(res, motivo);
      if (!b.esAdmin && b.negocios.length === 0) return bad(res, 'Un usuario que no es administrador necesita al menos un negocio');
      if (b.esAdmin && !(await confirmAdmin(req, b.adminPassword))) return needConfirm(res);
      const hash = await hashPassword(b.password);
      try {
        const { rows: [{ id }] } = await authPool.query('SELECT auth_admin_crear_usuario($1,$2,$3,$4,$5,$6::jsonb) AS id',
          [req.auth.userId, b.username, b.nombre, hash, b.esAdmin ?? false, JSON.stringify(b.negocios)]);
        res.status(201).json({ success: true, id: Number(id) });
      } catch (err) { if (!sqlError(err, res)) throw err; }
    } catch (err) { next(err); }
  });

  router.patch('/users/:id', async (req, res, next) => {
    try {
      const id = idSchema.safeParse(req.params.id), body = patchSchema.safeParse(req.body);
      if (!id.success || !body.success) return bad(res, 'Datos no válidos');
      const b = body.data;
      if (b.esAdmin === true && !(await confirmAdmin(req, b.adminPassword))) return needConfirm(res);
      try {
        const { rows: [{ ok }] } = await authPool.query('SELECT auth_admin_actualizar_usuario($1,$2,$3,$4,$5,$6::jsonb) AS ok',
          [req.auth.userId, id.data, b.nombre ?? null, b.activo ?? null, b.esAdmin ?? null, b.negocios === undefined ? null : JSON.stringify(b.negocios)]);
        if (!ok) return res.status(404).json({ success: false, error: 'Usuario no encontrado' });
        res.json({ success: true });
      } catch (err) { if (!sqlError(err, res)) throw err; }
    } catch (err) { next(err); }
  });

  router.post('/users/:id/password', async (req, res, next) => {
    try {
      const id = idSchema.safeParse(req.params.id), body = passwordSchema.safeParse(req.body);
      if (!id.success || !body.success) return bad(res, 'Datos no válidos');
      const { rows: [user] } = await authPool.query('SELECT * FROM auth_admin_credencial($1)', [id.data]);
      if (!user) return res.status(404).json({ success: false, error: 'Usuario no encontrado' });
      const motivo = validatePassword(body.data.password, user.username);
      if (motivo) return bad(res, motivo);
      if (user.es_admin && !(await confirmAdmin(req, body.data.adminPassword))) return needConfirm(res);
      await authPool.query('SELECT auth_admin_cambiar_password($1,$2,$3)', [req.auth.userId, id.data, await hashPassword(body.data.password)]);
      res.json({ success: true });
    } catch (err) { next(err); }
  });

  router.post('/users/:id/unlock', async (req, res, next) => {
    try {
      const id = idSchema.safeParse(req.params.id);
      if (!id.success) return bad(res, 'Datos no válidos');
      const { rows: [{ ok }] } = await authPool.query('SELECT auth_admin_desbloquear($1,$2) AS ok', [req.auth.userId, id.data]);
      if (!ok) return res.status(404).json({ success: false, error: 'Usuario no encontrado' });
      res.json({ success: true });
    } catch (err) { next(err); }
  });

  return router;
}
