import { createHash, randomBytes } from 'node:crypto';

/** Tokens opacos: 32 bytes aleatorios. En la base solo se guarda su SHA-256; el prefijo permite descartar basura sin consultar. */
export const newAccessToken = () => `at_${randomBytes(32).toString('base64url')}`;
export const newRefreshToken = () => `rt_${randomBytes(32).toString('base64url')}`;
export const hashToken = (token) => createHash('sha256').update(token, 'utf8').digest();
export const ACCESS_RE = /^at_[A-Za-z0-9_-]{43}$/;
export const REFRESH_RE = /^rt_[A-Za-z0-9_-]{43}$/;

/** Devuelve el access token del encabezado Authorization, null si no hay encabezado, o false si es de otro formato. */
export function bearerToken(req) {
  const h = req.get('authorization');
  if (!h) return null;
  const m = /^Bearer (\S+)$/.exec(h);
  return m && ACCESS_RE.test(m[1]) ? m[1] : false;
}
