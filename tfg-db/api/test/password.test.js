import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hashPassword, verifyPassword, needsRehash, parsePhc, SCRYPT } from '../src/password.js';
import { KeyedLock } from '../src/keyedLock.js';
import { clientKey } from '../src/clientKey.js';

test('hashPassword genera un hash PHC scrypt con sal distinta cada vez', async () => {
  const a = await hashPassword('contraseña-segura-1');
  const b = await hashPassword('contraseña-segura-1');
  assert.match(a, /^\$scrypt\$ln=15,r=8,p=3\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$/);
  assert.notEqual(a, b, 'la sal debe ser aleatoria');
  assert.ok(!a.includes('contraseña'));
});

test('verifyPassword acepta la contraseña correcta y rechaza las demás', async () => {
  const h = await hashPassword('Correcta#2026');
  assert.equal(await verifyPassword('Correcta#2026', h), true);
  assert.equal(await verifyPassword('correcta#2026', h), false);
  assert.equal(await verifyPassword('', h), false);
});

test('verifyPassword normaliza Unicode (NFKC): la misma contraseña con distinta composición vale', async () => {
  const h = await hashPassword('Contraseña');            // ñ compuesta
  assert.equal(await verifyPassword('Contrasen\u0303a', h), true); // n + virguilla
});

test('verifyPassword devuelve false ante un hash mal formado o con parámetros abusivos', async () => {
  assert.equal(await verifyPassword('x', 'texto-plano'), false);
  assert.equal(await verifyPassword('x', '$scrypt$ln=15,r=8,p=3$AAAA'), false);
  assert.equal(await verifyPassword('x', '$scrypt$ln=30,r=8,p=3$c2FsdA$aGFzaA'), false, 'ln=30 agotaría la memoria');
  assert.equal(await verifyPassword('x', '$scrypt$ln=15,r=8,p=99$c2FsdA$aGFzaA'), false);
});

test('parsePhc y needsRehash detectan parámetros antiguos', async () => {
  const h = await hashPassword('x');
  assert.deepEqual({ ln: parsePhc(h).ln, r: parsePhc(h).r, p: parsePhc(h).p }, { ln: SCRYPT.ln, r: SCRYPT.r, p: SCRYPT.p });
  assert.equal(needsRehash(h), false);
  assert.equal(needsRehash('$scrypt$ln=14,r=8,p=1$c2FsdA$aGFzaA'), true);
});

test('varias verificaciones simultáneas terminan (la cola acotada no se bloquea)', async () => {
  const h = await hashPassword('x');
  const r = await Promise.all(Array.from({ length: 6 }, () => verifyPassword('x', h)));
  assert.deepEqual(r, Array(6).fill(true));
});

test('parsePhc rechaza parámetros que superarían el tope de memoria (ln=17 con r=16 pide 256 MiB)', () => {
  assert.equal(parsePhc('$scrypt$ln=17,r=16,p=1$c2FsdHNhbHQ$aGFzaGhhc2hoYXNoaGFzaA'), null);
  assert.ok(parsePhc('$scrypt$ln=15,r=8,p=3$c2FsdHNhbHQ$aGFzaGhhc2hoYXNoaGFzaA'));
});

test('KeyedLock: una misma clave se ejecuta de una en una y claves distintas en paralelo', async () => {
  const lock = new KeyedLock();
  let activas = 0, maxMisma = 0, activasOtras = 0, maxTotal = 0;
  const tarea = (key) => lock.run(key, async () => {
    activas += key === 'a' ? 1 : 0; activasOtras += 1; maxMisma = Math.max(maxMisma, activas); maxTotal = Math.max(maxTotal, activasOtras);
    await new Promise((r) => setTimeout(r, 15));
    activas -= key === 'a' ? 1 : 0; activasOtras -= 1;
  });
  await Promise.all([tarea('a'), tarea('a'), tarea('a'), tarea('b'), tarea('c')]);
  assert.equal(maxMisma, 1, 'nunca dos tareas de la clave a a la vez');
  assert.ok(maxTotal >= 2, 'claves distintas sí se solapan');
  const fallo = lock.run('x', async () => { throw new Error('boom'); });
  await assert.rejects(fallo);
  assert.equal(await lock.run('x', async () => 'ok'), 'ok', 'un fallo no deja la clave bloqueada');
});

test('clientKey agrupa IPv6 por /64 y deja IPv4 y las IPv4 mapeadas tal cual', () => {
  assert.equal(clientKey({ ip: '203.0.113.7' }), '203.0.113.7');
  assert.equal(clientKey({ ip: '::ffff:203.0.113.7' }), '203.0.113.7');
  assert.equal(clientKey({ ip: '2001:db8:1:2:aaaa:bbbb:cccc:dddd' }), clientKey({ ip: '2001:db8:1:2::1' }));
  assert.notEqual(clientKey({ ip: '2001:db8:1:2::1' }), clientKey({ ip: '2001:db8:1:3::1' }));
  assert.equal(clientKey({ ip: undefined }), 'desconocida');
});
