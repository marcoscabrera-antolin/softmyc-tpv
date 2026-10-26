import express from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { normalizeTenant } from './auth.js';
import { verifyPassword, verifyAgainstDummy, parsePhc, warmUp } from './password.js';
import { KeyedLock } from './keyedLock.js';
import { clientKey } from './clientKey.js';
import { newAccessToken, newRefreshToken, hashToken, bearerToken, REFRESH_RE } from './tokens.js';

const GENERIC_LOGIN_ERROR = { success: false, error: 'Credenciales inválidas' };
const GENERIC_SESSION_ERROR = { success: false, error: 'Sesión no válida' };
const USERNAME_RE = /^[a-z0-9._-]{3,40}$/;

const loginSchema = z.object({
  username: z.string().min(1).max(60),
  password: z.string().min(1).max(200),
  restaurantId: z.string().max(60).optional(),
}).strict();
const refreshSchema = z.object({ refreshToken: z.string().max(100) }).strict();

const clip = (v, n) => (v == null ? null : String(v).slice(0, n));
export const sessionBody = (s, ttl, user) => ({
  success: true, token: s.token, refreshToken: s.refreshToken, expiresIn: ttl.accessSeconds,
  refreshExpiresAt: new Date(s.refreshExpira).toISOString(), user,
});

/**
 * Rutas de acceso. authPool usa el rol tfg_auth (única conexión que puede ejecutar el flujo de acceso);
 * authenticate valida el token con la conexión de negocio.
 */
export function createAuthRouter({ authPool, authenticate, loginLimitMax = 10, refreshLimitMax = 60, accessTtlMin = 30, refreshTtlDays = 7 }) {
  const router = express.Router();
  const ttl = { accessSeconds: accessTtlMin * 60 };
  const userLock = new KeyedLock();
  warmUp().catch(() => {});
  // Respuestas con tokens: nunca se cachean.
  router.use((_req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });
  const limiter = (limit, extra = {}) => rateLimit({ windowMs: 15 * 60_000, limit, standardHeaders: true, legacyHeaders: false,
    keyGenerator: clientKey, message: { success: false, error: 'Demasiados intentos; inténtalo más tarde' }, ...extra });

  // Los accesos correctos no cuentan: varias tablets de un mismo local comparten IP.
  router.post('/login', limiter(loginLimitMax, { skipSuccessfulRequests: true }), async (req, res, next) => {
    try {
      const body = loginSchema.safeParse(req.body);
      if (!body.success) return res.status(400).json({ success: false, error: 'Faltan usuario o contraseña' });
      const { password, restaurantId } = body.data;
      const username = body.data.username.trim().toLowerCase();
      // Los intentos de una misma cuenta se procesan de uno en uno: así el bloqueo se aplica antes de comprobar la siguiente
      // contraseña y no se pueden probar decenas en paralelo antes de que se registre el primer fallo.
      const serial = USERNAME_RE.test(username) ? (fn) => userLock.run(username, fn) : (fn) => fn();
      const { cred, ok, bloqueo } = await serial(async () => {
        const c = USERNAME_RE.test(username) ? (await authPool.query('SELECT * FROM auth_credencial($1)', [username])).rows[0] : undefined;
        // Siempre se paga el coste del hash (real o ficticio, también con un hash ilegible): el tiempo no delata nada.
        const passOk = c && parsePhc(c.password_hash) ? await verifyPassword(password, c.password_hash) : await verifyAgainstDummy(password);
        // El bloqueo es por (cuenta, origen): un atacante desde otra red no impide entrar desde la del usuario.
        const bloqueadaAntes = c ? (await authPool.query('SELECT auth_bloqueo_vigente($1,$2) AS b', [c.usuario_id, clip(req.ip, 45)])).rows[0].b : null;
        const acierto = Boolean(c && c.activo && passOk && !bloqueadaAntes);
        const { rows: [{ b }] } = await authPool.query('SELECT auth_registrar_intento($1,$2,$3,$4,$5) AS b',
          [clip(username, 64), c?.usuario_id ?? null, acierto, clip(req.ip, 45), clip(req.get('user-agent'), 200)]);
        return { cred: c, ok: acierto, bloqueo: b };
      });
      if (!ok || bloqueo) return res.status(401).json(GENERIC_LOGIN_ERROR);   // misma respuesta en todos los fallos

      const { rows: negocios } = await authPool.query('SELECT * FROM auth_negocios_usuario($1)', [cred.usuario_id]);
      const pedido = restaurantId ? normalizeTenant(restaurantId) : null;
      let tenant;
      if (cred.es_admin) {
        tenant = pedido;                       // el administrador de plataforma puede entrar sin negocio (solo /api/admin)
      } else if (negocios.length === 0) {
        return res.status(403).json({ success: false, error: 'Tu cuenta no tiene negocios asignados' });
      } else if (negocios.length === 1) {
        tenant = negocios[0].tenant_id;        // con un único negocio se ignora el pedido
      } else if (!pedido) {
        return res.status(400).json({ success: false, error: 'Indica el negocio', restaurants: negocios.map((n) => ({ id: n.tenant_id, nombre: n.nombre })) });
      } else if (!negocios.some((n) => n.tenant_id === pedido)) {
        return res.status(403).json({ success: false, error: 'No tienes acceso a ese negocio' });
      } else {
        tenant = pedido;
      }
      const token = newAccessToken(), refreshToken = newRefreshToken();
      let s;
      try {
        s = (await authPool.query('SELECT * FROM auth_crear_sesion($1,$2,$3,$4,$5::interval,$6::interval,$7)',
          [cred.usuario_id, tenant, hashToken(token), hashToken(refreshToken), `${accessTtlMin} minutes`, `${refreshTtlDays} days`, clip(req.get('user-agent'), 100)])).rows[0];
      } catch (err) {
        if (err.code === '42501') return res.status(403).json({ success: false, error: 'No tienes acceso a ese negocio' });
        throw err;
      }
      return res.json(sessionBody({ token, refreshToken, refreshExpira: s.refresh_expira_en }, ttl,
        { username, restaurantId: tenant, role: s.rol }));
    } catch (err) {
      if (err.code === 'BUSY') return res.status(503).json({ success: false, error: 'Servidor ocupado; inténtalo de nuevo' });
      return next(err);
    }
  });

  router.post('/refresh', limiter(refreshLimitMax), async (req, res, next) => {
    try {
      const body = refreshSchema.safeParse(req.body);
      if (!body.success || !REFRESH_RE.test(body.data.refreshToken)) return res.status(400).json({ success: false, error: 'Token de refresco inválido' });
      const token = newAccessToken(), refreshToken = newRefreshToken();
      const { rows: [r] } = await authPool.query('SELECT * FROM auth_rotar_sesion($1,$2,$3,$4::interval,$5,$6)',
        [hashToken(body.data.refreshToken), hashToken(token), hashToken(refreshToken), `${accessTtlMin} minutes`, clip(req.ip, 45), clip(req.get('user-agent'), 200)]);
      if (r.estado !== 'ok') return res.status(401).json(GENERIC_SESSION_ERROR);
      return res.json(sessionBody({ token, refreshToken, refreshExpira: r.refresh_expira_en }, ttl, { restaurantId: r.tenant_id, role: r.rol }));
    } catch (err) { return next(err); }
  });

  // Cierra la sesión con el access token o, si ya caducó, con el refresco del cuerpo. Es idempotente y no revela si existía.
  router.post('/logout', limiter(120), async (req, res, next) => {
    try {
      const token = bearerToken(req);
      if (token) {
        await authPool.query('SELECT auth_revocar_sesion($1)', [hashToken(token)]);
        return res.json({ success: true });
      }
      const refresh = req.body && typeof req.body.refreshToken === 'string' ? req.body.refreshToken : '';
      if (REFRESH_RE.test(refresh)) {
        await authPool.query('SELECT auth_revocar_refresh($1)', [hashToken(refresh)]);
        return res.json({ success: true });
      }
      return res.status(401).json(GENERIC_SESSION_ERROR);
    } catch (err) { return next(err); }
  });

  router.get('/me', authenticate, (req, res) => {
    const { tenantId, rol, isAdmin } = req.auth;
    res.json({ success: true, user: { restaurantId: tenantId, role: rol, isAdmin } });
  });

  return router;
}
