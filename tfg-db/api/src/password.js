import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';

/**
 * Parámetros de scrypt (equivalentes a la guía de OWASP: N=2^15, r=8, p=3, unos 32 MiB por cálculo).
 * Van codificados en el propio hash (formato PHC), de modo que se pueden subir más adelante sin invalidar los existentes.
 */
export const SCRYPT = { ln: 15, r: 8, p: 3, keyLen: 32, saltLen: 16 };
const MAX_LN = 17, MAX_R = 16, MAX_P = 8;                // tope a lo que se acepta leer de un hash almacenado
const MAXMEM = 160 * 1024 * 1024;
const MAX_CONCURRENT = 2, MAX_QUEUE = 40;                // acota la memoria y la CPU: scrypt es caro a propósito

class Limiter {
  constructor(max, maxQueue) { this.max = max; this.maxQueue = maxQueue; this.active = 0; this.queue = []; }
  async run(fn) {
    if (this.active >= this.max) {
      if (this.queue.length >= this.maxQueue) throw Object.assign(new Error('servidor ocupado'), { code: 'BUSY' });
      await new Promise((resolve) => this.queue.push(resolve));
    }
    this.active++;
    try { return await fn(); }
    finally { this.active--; this.queue.shift()?.(); }
  }
}
const limiter = new Limiter(MAX_CONCURRENT, MAX_QUEUE);

const b64 = (buf) => buf.toString('base64').replace(/=+$/, '');
const derive = (plain, salt, { ln, r, p, keyLen }) => limiter.run(() => new Promise((resolve, reject) =>
  scrypt(plain.normalize('NFKC'), salt, keyLen, { N: 2 ** ln, r, p, maxmem: MAXMEM }, (err, key) => (err ? reject(err) : resolve(key)))));

export function parsePhc(phc) {
  const m = /^\$scrypt\$ln=(\d+),r=(\d+),p=(\d+)\$([A-Za-z0-9+/]+)\$([A-Za-z0-9+/]+)$/.exec(typeof phc === 'string' ? phc : '');
  if (!m) return null;
  const ln = Number(m[1]), r = Number(m[2]), p = Number(m[3]);
  if (ln < 10 || ln > MAX_LN || r < 1 || r > MAX_R || p < 1 || p > MAX_P) return null;
  if (128 * 2 ** ln * r > MAXMEM / 2) return null;        // acota la memoria de un cálculo (~80 MiB) para 2 en paralelo
  const salt = Buffer.from(m[4], 'base64'), hash = Buffer.from(m[5], 'base64');
  if (salt.length < 8 || hash.length < 16) return null;
  return { ln, r, p, salt, hash };
}

export async function hashPassword(plain, params = SCRYPT) {
  const salt = randomBytes(params.saltLen);
  const key = await derive(plain, salt, params);
  return `$scrypt$ln=${params.ln},r=${params.r},p=${params.p}$${b64(salt)}$${b64(key)}`;
}

export async function verifyPassword(plain, phc) {
  const parsed = parsePhc(phc);
  if (!parsed) return false;
  const key = await derive(plain, parsed.salt, { ...parsed, keyLen: parsed.hash.length });
  return timingSafeEqual(key, parsed.hash);
}

export function needsRehash(phc, params = SCRYPT) {
  const parsed = parsePhc(phc);
  return !parsed || parsed.ln !== params.ln || parsed.r !== params.r || parsed.p !== params.p;
}

let dummyHash;
/** Calcula el hash ficticio al arrancar para que el primer login no sea más lento (ni se repita con varios simultáneos). */
export function warmUp() { return (dummyHash ??= hashPassword(randomBytes(16).toString('hex'))); }
/**
 * Verifica contra un hash ficticio cuando el usuario no existe: así el tiempo de respuesta no delata si existe.
 * Siempre devuelve false.
 */
export async function verifyAgainstDummy(plain) {
  await verifyPassword(plain, await warmUp());
  return false;
}
