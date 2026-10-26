import { createHash, timingSafeEqual } from 'node:crypto';
import { bearerToken, hashToken } from './tokens.js';

/** Mismo criterio que OrderSyncService.sanitizeTenantId de la app: minúsculas y sin prefijo "softmyc". */
export function normalizeTenant(raw) {
  if (typeof raw !== 'string') return null;
  let id = raw.trim().toLowerCase();
  id = id.replace(/^softmyc[_-]?/, '').replace(/_v\d+$/, '');
  return /^[a-z0-9_]{2,40}$/.test(id) ? id : null;
}

export function hashKey(key) {
  return createHash('sha256').update(key, 'utf8').digest();
}

const HOUR_MS = 3_600_000;

/**
 * Autentica cada petición y deja la identidad en req.auth = {userId, tenantId, rol, sessionId, isAdmin, legacy}.
 *  - Authorization: Bearer <access token>: sesión de usuario, validada en la base (rol y acceso se recalculan en cada petición).
 *  - x-api-key (transición, mientras allowApiKey): rol tpv_operator y solo el negocio de la clave; se audita su uso.
 * Si llega Bearer manda el Bearer: un Bearer inválido no cae a la clave de API.
 */
export function authMiddleware(pool, { allowApiKey = true } = {}) {
  const lastLegacyLog = new Map();
  return async (req, res, next) => {
    try {
      const bearer = bearerToken(req);
      if (bearer !== null) {
        if (bearer === false) return res.status(401).json({ success: false, error: 'Sesión no válida' });
        const { rows } = await pool.query('SELECT * FROM auth_validar_acceso($1)', [hashToken(bearer)]);
        if (rows.length !== 1) return res.status(401).json({ success: false, error: 'Sesión no válida' });
        const s = rows[0];
        req.auth = { userId: Number(s.usuario_id), tenantId: s.tenant_id, rol: s.rol, sessionId: s.sesion_id, isAdmin: s.es_admin, legacy: false };
        req.tenantId = s.tenant_id;
        return next();
      }
      const key = req.get('x-api-key');
      if (!allowApiKey || !key || key.length < 20 || key.length > 200) return res.status(401).json({ success: false, error: 'Autenticación requerida' });
      const digest = hashKey(key);
      const { rows } = await pool.query('SELECT id, tenant_id, key_hash FROM api_keys WHERE key_hash = $1 AND activa', [digest]);
      if (rows.length !== 1 || rows[0].key_hash.length !== digest.length || !timingSafeEqual(rows[0].key_hash, digest)) return res.status(401).json({ success: false, error: 'API key no válida' });
      const claimed = req.get('x-tenant-id');
      if (claimed && normalizeTenant(claimed) !== rows[0].tenant_id) return res.status(403).json({ success: false, error: 'La clave no pertenece a ese negocio' });
      req.auth = { userId: null, tenantId: rows[0].tenant_id, rol: 'tpv_operator', sessionId: null, isAdmin: false, legacy: true };
      req.tenantId = rows[0].tenant_id;
      pool.query('UPDATE api_keys SET ultimo_uso = now() WHERE id = $1', [rows[0].id]).catch(() => {});
      const now = Date.now();
      if (now - (lastLegacyLog.get(rows[0].id) ?? 0) > HOUR_MS) {   // como mucho una vez por hora y clave
        lastLegacyLog.set(rows[0].id, now);
        pool.query("INSERT INTO auditoria_auth (tenant_id, evento, exito, ip) VALUES ($1, 'api_key_heredada', true, $2)", [rows[0].tenant_id, String(req.ip ?? '').slice(0, 45)]).catch(() => {});
      }
      return next();
    } catch (err) {
      return next(err);
    }
  };
}

/** Exige uno de los roles indicados (el rol sale siempre del servidor, nunca de la app). */
export function requireRole(...roles) {
  return (req, res, next) => (roles.includes(req.auth?.rol) ? next() : res.status(403).json({ success: false, error: 'No tienes permiso para esta operación' }));
}

/** Las rutas de datos de negocio necesitan un negocio en la sesión (el administrador sin negocio no lo tiene). */
export function requireTenant(req, res, next) {
  return req.auth?.tenantId ? next() : res.status(403).json({ success: false, error: 'Selecciona un negocio' });
}
