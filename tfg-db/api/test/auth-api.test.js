// Fase 2: login, refresco, cierre de sesión, /me y autorización por rol sobre un PostgreSQL real (initdb 01-05).
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import pg from 'pg';
import { motivoOmision } from './db-env.js';
import { createApp } from '../src/app.js';
import { createPool } from '../src/db.js';
import { hashPassword } from '../src/password.js';
import { hashKey } from '../src/auth.js';

const OWNER = process.env.TEST_OWNER_URL, APP = process.env.TEST_APP_URL, AUTH = process.env.TEST_AUTH_URL;
const skip = motivoOmision(['TEST_OWNER_URL', 'TEST_APP_URL', 'TEST_AUTH_URL']);
const PASS = 'Clave-De-Prueba-2026';
const MUESTRAS_TIEMPO = 5;
const PROPORCION_MAXIMA_TIEMPO = 4;
const LEGACY_KEY = 'tfg_test_key_legacy_00000000000000000000';
let owner, pool, authPool, server, base, passHash, n = 0;

const call = async (path, { method = 'GET', body, token, headers = {} } = {}) => {
  const res = await fetch(base + path, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}), ...headers }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: res.status, body: await res.json().catch(() => null) };
};
const login = (username, extra = {}) => call('/api/tpv/auth/login', { method: 'POST', body: { username, password: PASS, ...extra } });
async function usuario({ admin = false, activo = true, negocios = [] } = {}) {
  const username = `api${++n}`;
  const { rows } = await owner.query('INSERT INTO usuarios (username, nombre, password_hash, es_admin, activo) VALUES ($1,$2,$3,$4,$5) RETURNING id', [username, username, passHash, admin, activo]);
  for (const [t, r] of negocios) await owner.query('INSERT INTO usuario_negocios (usuario_id, tenant_id, rol) VALUES ($1,$2,$3)', [rows[0].id, t, r]);
  return { id: rows[0].id, username };
}

before(async () => {
  if (skip) return;
  owner = new pg.Client({ connectionString: OWNER }); await owner.connect();
  await owner.query('TRUNCATE auditoria_auth, sesiones, usuario_negocios, usuarios RESTART IDENTITY CASCADE');
  await owner.query('TRUNCATE api_keys RESTART IDENTITY');
  await owner.query("INSERT INTO api_keys (tenant_id, nombre, key_hash) VALUES ('fruteria','legacy',$1)", [hashKey(LEGACY_KEY)]);
  await owner.query("DELETE FROM productos WHERE tenant_id IN ('fruteria','taller')");
  await owner.query("INSERT INTO productos (tenant_id,codigo,nombre,precio,categoria,unidad_medida) VALUES ('fruteria',101,'Manzanas',2.40,'Frutas','KG'),('taller',201,'Aceite',9.99,'Piezas','UD')");
  passHash = await hashPassword(PASS);
  pool = createPool(APP); authPool = createPool(AUTH);
  server = createApp(pool, { authPool, rateLimitMax: 10_000, loginLimitMax: 10_000, refreshLimitMax: 10_000 }).listen(0, '127.0.0.1');
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { if (skip) return; await new Promise((r) => server.close(r)); await authPool.end(); await pool.end(); await owner.end(); });

test('login correcto de un operador devuelve tokens, rol y negocio, y el token sirve para las rutas de negocio', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const r = await login(u.username);
  assert.equal(r.status, 200); assert.equal(r.body.success, true);
  assert.match(r.body.token, /^at_[A-Za-z0-9_-]{43}$/); assert.match(r.body.refreshToken, /^rt_[A-Za-z0-9_-]{43}$/);
  assert.equal(r.body.expiresIn, 1800);
  assert.deepEqual(r.body.user, { username: u.username, restaurantId: 'fruteria', role: 'tpv_operator' });
  const p = await call('/api/tpv/products', { token: r.body.token });
  assert.equal(p.status, 200); assert.deepEqual(p.body.products.map((x) => x.codigo), [101]);
});

test('credenciales inválidas, usuario inexistente y usuario inactivo dan exactamente la misma respuesta', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const inactivo = await usuario({ activo: false, negocios: [['fruteria', 'tpv_operator']] });
  const mala = await call('/api/tpv/auth/login', { method: 'POST', body: { username: u.username, password: 'incorrecta' } });
  const noExiste = await login('no-existe-nadie');
  const inac = await login(inactivo.username);
  const nombreRaro = await login('../../etc');
  for (const x of [noExiste, inac, nombreRaro]) { assert.equal(x.status, mala.status); assert.deepEqual(x.body, mala.body); }
  assert.equal(mala.status, 401);
});

test('el tiempo de respuesta no delata si el usuario existe (siempre se calcula el hash)', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const medir = async (f) => { const t0 = performance.now(); await f(); return performance.now() - t0; };
  const mediana = (xs) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];
  await call('/api/tpv/auth/login', { method: 'POST', body: { username: u.username, password: 'x' } }); // calentamiento
  const existentes = [], inexistentes = [];
  // Muestras intercaladas: una carga puntual de la máquina afecta a los dos grupos por igual.
  for (let i = 0; i < MUESTRAS_TIEMPO; i++) {
    existentes.push(await medir(() => call('/api/tpv/auth/login', { method: 'POST', body: { username: u.username, password: 'x' } })));
    inexistentes.push(await medir(() => login(`fantasma-${i}`)));
  }
  const me = mediana(existentes), mi = mediana(inexistentes);
  // Sin umbral absoluto (depende de la máquina). Si se omitiera el hash para usuarios inexistentes la diferencia
  // sería de órdenes de magnitud, así que una proporción amplia basta para detectarlo.
  assert.ok(Math.max(me, mi) / Math.min(me, mi) < PROPORCION_MAXIMA_TIEMPO,
    `tiempos de orden distinto: existente ${me.toFixed(0)} ms, inexistente ${mi.toFixed(0)} ms`);
});

test('5 fallos bloquean la cuenta: ni la contraseña correcta entra y la respuesta no cambia', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  for (let i = 0; i < 5; i++) await call('/api/tpv/auth/login', { method: 'POST', body: { username: u.username, password: 'mala' } });
  const bloqueada = await login(u.username);
  const mala = await call('/api/tpv/auth/login', { method: 'POST', body: { username: u.username, password: 'mala' } });
  assert.equal(bloqueada.status, 401); assert.deepEqual(bloqueada.body, mala.body);
  const { rows } = await owner.query('SELECT bloqueado_hasta FROM usuarios WHERE id=$1', [u.id]);
  assert.ok(rows[0].bloqueado_hasta > new Date());
  await owner.query('UPDATE usuarios SET bloqueado_hasta = NULL, intentos_fallidos = 0 WHERE id=$1', [u.id]);
  await owner.query('DELETE FROM login_intentos_ip WHERE usuario_id=$1', [u.id]);
  assert.equal((await login(u.username)).status, 200);
});

test('un usuario con varios negocios debe indicar uno permitido', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'encargado'], ['taller', 'tpv_operator']] });
  const sin = await login(u.username);
  assert.equal(sin.status, 400); assert.deepEqual(sin.body.restaurants.map((x) => x.id).sort(), ['fruteria', 'taller']);
  assert.equal((await login(u.username, { restaurantId: 'mayorista' })).status, 403);
  const ok = await login(u.username, { restaurantId: 'softmyc_taller' });
  assert.equal(ok.status, 200); assert.equal(ok.body.user.restaurantId, 'taller'); assert.equal(ok.body.user.role, 'tpv_operator');
});

test('un usuario de un solo negocio ignora el restaurantId pedido', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const r = await login(u.username, { restaurantId: 'taller' });
  assert.equal(r.status, 200); assert.equal(r.body.user.restaurantId, 'fruteria');
});

test('el administrador de plataforma entra sin negocio (solo admin) o eligiendo uno', { skip }, async () => {
  const a = await usuario({ admin: true });
  const sin = await login(a.username);
  assert.equal(sin.status, 200); assert.equal(sin.body.user.restaurantId, null); assert.equal(sin.body.user.role, 'admin');
  assert.equal((await call('/api/tpv/products', { token: sin.body.token })).status, 403, 'sin negocio no hay datos de negocio');
  const con = await login(a.username, { restaurantId: 'taller' });
  assert.deepEqual((await call('/api/tpv/products', { token: con.body.token })).body.products.map((x) => x.codigo), [201]);
});

test('/me devuelve la identidad que el servidor tiene de la sesión', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'encargado']] });
  const t = (await login(u.username)).body.token;
  const me = await call('/api/tpv/auth/me', { token: t });
  assert.equal(me.status, 200); assert.deepEqual(me.body.user, { restaurantId: 'fruteria', role: 'encargado', isAdmin: false });
  assert.equal((await call('/api/tpv/auth/me')).status, 401);
  assert.equal((await call('/api/tpv/auth/me', { token: 'at_' + 'x'.repeat(43) })).status, 401);
});

test('refresco: entrega tokens nuevos, invalida los anteriores y reutilizar uno viejo revoca toda la sesión', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const l = (await login(u.username)).body;
  const r1 = await call('/api/tpv/auth/refresh', { method: 'POST', body: { refreshToken: l.refreshToken } });
  assert.equal(r1.status, 200); assert.notEqual(r1.body.token, l.token);
  assert.equal((await call('/api/tpv/products', { token: l.token })).status, 401, 'el acceso anterior deja de valer');
  assert.equal((await call('/api/tpv/products', { token: r1.body.token })).status, 200);
  const reuso = await call('/api/tpv/auth/refresh', { method: 'POST', body: { refreshToken: l.refreshToken } });
  assert.equal(reuso.status, 401);
  assert.equal((await call('/api/tpv/products', { token: r1.body.token })).status, 401, 'la familia entera queda revocada');
  assert.equal((await call('/api/tpv/auth/refresh', { method: 'POST', body: { refreshToken: r1.body.refreshToken } })).status, 401);
});

test('refresco con un token mal formado o desconocido: 401 genérico', { skip }, async () => {
  const a = await call('/api/tpv/auth/refresh', { method: 'POST', body: { refreshToken: 'rt_' + 'y'.repeat(43) } });
  const b = await call('/api/tpv/auth/refresh', { method: 'POST', body: { refreshToken: 'basura' } });
  assert.equal(a.status, 401); assert.equal(b.status, 400);
});

test('logout revoca la sesión entera, también el refresco', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const l = (await login(u.username)).body;
  assert.equal((await call('/api/tpv/auth/logout', { method: 'POST', token: l.token })).status, 200);
  assert.equal((await call('/api/tpv/products', { token: l.token })).status, 401);
  assert.equal((await call('/api/tpv/auth/refresh', { method: 'POST', body: { refreshToken: l.refreshToken } })).status, 401);
  assert.equal((await call('/api/tpv/auth/logout', { method: 'POST' })).status, 401);
});

test('roles: el operador no puede ajustar stock; encargado y admin sí; el cambio de rol es inmediato', { skip }, async () => {
  const op = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const t = (await login(op.username)).body.token;
  assert.equal((await call('/api/tpv/products/101/stock', { method: 'PUT', token: t, body: { stock: 5 } })).status, 403);
  await owner.query("UPDATE usuario_negocios SET rol='encargado' WHERE usuario_id=$1", [op.id]);
  assert.equal((await call('/api/tpv/products/101/stock', { method: 'PUT', token: t, body: { stock: 5 } })).status, 200, 'sin volver a iniciar sesión');
  assert.equal((await owner.query("SELECT stock FROM productos WHERE tenant_id='fruteria' AND codigo=101")).rows[0].stock, 5);
  await owner.query('DELETE FROM usuario_negocios WHERE usuario_id=$1', [op.id]);
  assert.equal((await call('/api/tpv/products', { token: t })).status, 401, 'al quitarle el negocio deja de valer');
});

test('catálogo: el operador no escribe; el encargado da de alta, edita (sin perder stock) y borra', { skip }, async () => {
  const op = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const t = (await login(op.username)).body.token;
  const nuevo = { codigo: 9001, nombre: 'Pera conferencia', precio: 2.1, categoria: 'Frutas', ingredientes: [] };
  assert.equal((await call('/api/tpv/products', { method: 'POST', token: t, body: nuevo })).status, 403);
  assert.equal((await call('/api/tpv/products/9001', { method: 'DELETE', token: t })).status, 403);
  await owner.query("UPDATE usuario_negocios SET rol='encargado' WHERE usuario_id=$1", [op.id]);

  assert.equal((await call('/api/tpv/products', { method: 'POST', token: t, body: nuevo })).status, 200);
  const alta = (await owner.query("SELECT precio, stock, unidad_medida FROM productos WHERE tenant_id='fruteria' AND codigo=9001")).rows[0];
  assert.equal(Number(alta.precio), 2.1); assert.equal(alta.stock, -1); assert.equal(alta.unidad_medida, 'UD');

  await owner.query("UPDATE productos SET stock = 7, unidad_medida = 'KG' WHERE tenant_id='fruteria' AND codigo=9001");
  assert.equal((await call('/api/tpv/products', { method: 'POST', token: t, body: { ...nuevo, precio: 2.5 } })).status, 200);
  const edit = (await owner.query("SELECT precio, stock, unidad_medida FROM productos WHERE tenant_id='fruteria' AND codigo=9001")).rows[0];
  assert.equal(Number(edit.precio), 2.5); assert.equal(edit.stock, 7, 'editar no borra el stock'); assert.equal(edit.unidad_medida, 'KG');

  assert.equal((await call('/api/tpv/products', { method: 'POST', token: t, body: { ...nuevo, stock: 3, unidadMedida: 'UD' } })).status, 200);
  const expl = (await owner.query("SELECT stock, unidad_medida FROM productos WHERE tenant_id='fruteria' AND codigo=9001")).rows[0];
  assert.equal(expl.stock, 3, 'con stock explícito se aplica'); assert.equal(expl.unidad_medida, 'UD');

  assert.equal((await call('/api/tpv/products', { method: 'POST', token: t, body: { codigo: 0, nombre: '', precio: -1 } })).status, 400);
  assert.equal((await call('/api/tpv/products/9001', { method: 'DELETE', token: t })).status, 200);
  assert.equal((await call('/api/tpv/products/9001', { method: 'DELETE', token: t })).status, 404);
  await owner.query('DELETE FROM usuario_negocios WHERE usuario_id=$1', [op.id]);
});

test('catálogo: un producto escrito en un negocio no aparece ni se borra desde otro', { skip }, async () => {
  const enc = await usuario({ negocios: [['taller', 'encargado']] });
  const t = (await login(enc.username)).body.token;
  assert.equal((await call('/api/tpv/products', { method: 'POST', token: t, body: { codigo: 9002, nombre: 'Filtro', precio: 12, categoria: 'Piezas' } })).status, 200);
  assert.equal((await owner.query("SELECT count(*)::int n FROM productos WHERE tenant_id='fruteria' AND codigo=9002")).rows[0].n, 0);
  assert.equal((await owner.query("SELECT count(*)::int n FROM productos WHERE tenant_id='taller' AND codigo=9002")).rows[0].n, 1);
  assert.equal((await call('/api/tpv/products/101', { method: 'DELETE', token: t })).status, 404, 'no ve el 101 de fruteria');
  await owner.query("DELETE FROM productos WHERE tenant_id='taller' AND codigo=9002");
  await owner.query('DELETE FROM usuario_negocios WHERE usuario_id=$1', [enc.id]);
});

test('el negocio sale del token: x-tenant-id no puede cambiarlo', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const t = (await login(u.username)).body.token;
  const p = await call('/api/tpv/products', { token: t, headers: { 'x-tenant-id': 'taller' } });
  assert.deepEqual(p.body.products.map((x) => x.codigo), [101]);
});

test('subir ventas con un token: se guardan en el negocio de la sesión', { skip }, async () => {
  const u = await usuario({ negocios: [['taller', 'tpv_operator']] });
  const t = (await login(u.username)).body.token;
  const venta = { localId: 31, uuid: '44444444-4444-4444-8444-444444444444', total: 2.4, metodoPago: 'Efectivo', fecha: Date.now() - 1000, items: [{ nombre: 'Aceite', cantidad: 1, precio_unitario: 2.4, importe_linea: 2.4 }] };
  const r = await call('/api/tpv/orders/sync', { method: 'POST', token: t, body: { restaurantId: 'fruteria', orders: [venta] } });
  assert.deepEqual(r.body.syncedIds, [31]);
  // El uuid también existe en 'fruteria' (api.test.js): la comprobación debe acotarse al negocio de la sesión.
  const { rows } = await owner.query("SELECT count(*)::int n FROM ventas WHERE tenant_id='taller' AND uuid='44444444-4444-4444-8444-444444444444'");
  assert.equal(rows[0].n, 1);
});

test('transición: la clave de API sigue sirviendo para sincronizar, con permisos de operador, y se audita', { skip }, async () => {
  const p = await call('/api/tpv/products', { headers: { 'x-api-key': LEGACY_KEY } });
  assert.equal(p.status, 200);
  assert.equal((await call('/api/tpv/products/101/stock', { method: 'PUT', headers: { 'x-api-key': LEGACY_KEY }, body: { stock: 1 } })).status, 403);
  const { rows } = await owner.query("SELECT count(*)::int n FROM auditoria_auth WHERE evento='api_key_heredada'");
  assert.ok(rows[0].n >= 1);
  const bearerGana = await call('/api/tpv/products', { token: 'at_' + 'z'.repeat(43), headers: { 'x-api-key': LEGACY_KEY } });
  assert.equal(bearerGana.status, 401, 'si hay Bearer manda el Bearer');
});

test('con la transición desactivada la clave de API ya no autentica', { skip }, async () => {
  const s2 = createApp(pool, { authPool, allowApiKey: false, rateLimitMax: 10_000 }).listen(0, '127.0.0.1');
  await new Promise((r) => s2.once('listening', r));
  const res = await fetch(`http://127.0.0.1:${s2.address().port}/api/tpv/products`, { headers: { 'x-api-key': LEGACY_KEY } });
  await new Promise((r) => s2.close(r));
  assert.equal(res.status, 401);
});

test('validación de entrada: cuerpos incompletos o desmesurados', { skip }, async () => {
  assert.equal((await call('/api/tpv/auth/login', { method: 'POST', body: { username: 'a' } })).status, 400);
  assert.equal((await call('/api/tpv/auth/login', { method: 'POST', body: { username: 'abc', password: 'x'.repeat(500) } })).status, 400);
  assert.equal((await call('/api/tpv/auth/login', { method: 'POST', body: 'no-json' })).status, 400);
});

test('limitación por IP: el login se corta con 429 al superar el máximo', { skip }, async () => {
  const s2 = createApp(pool, { authPool, loginLimitMax: 3, rateLimitMax: 10_000 }).listen(0, '127.0.0.1');
  await new Promise((r) => s2.once('listening', r));
  const url = `http://127.0.0.1:${s2.address().port}/api/tpv/auth/login`;
  const codes = [];
  for (let i = 0; i < 5; i++) codes.push((await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: 'nadie', password: 'x' }) })).status);
  await new Promise((r) => s2.close(r));
  assert.deepEqual(codes, [401, 401, 401, 429, 429]);
});

test('los intentos de acceso quedan auditados sin guardar contraseñas', { skip }, async () => {
  const { rows } = await owner.query("SELECT count(*)::int n FROM auditoria_auth WHERE evento='login'");
  assert.ok(rows[0].n > 5);
  const { rows: r2 } = await owner.query("SELECT count(*)::int n FROM auditoria_auth WHERE user_agent ILIKE '%Clave-De-Prueba%' OR username ILIKE '%Clave-De-Prueba%'");
  assert.equal(r2[0].n, 0);
});

test('las respuestas con tokens no se pueden cachear', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const res = await fetch(base + '/api/tpv/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: u.username, password: PASS }) });
  assert.equal(res.headers.get('cache-control'), 'no-store');
  const mala = await fetch(base + '/api/tpv/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: u.username, password: 'x' }) });
  assert.equal(mala.headers.get('cache-control'), 'no-store');
});

test('el limitador de login no cuenta los accesos correctos (varias tablets tras la misma IP)', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const s2 = createApp(pool, { authPool, loginLimitMax: 3, rateLimitMax: 10_000 }).listen(0, '127.0.0.1');
  await new Promise((r) => s2.once('listening', r));
  const url = `http://127.0.0.1:${s2.address().port}/api/tpv/auth/login`;
  const post = async (password) => (await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: u.username, password }) })).status;
  const ok = []; for (let i = 0; i < 6; i++) ok.push(await post(PASS));
  const malas = []; for (let i = 0; i < 4; i++) malas.push(await post('mala'));
  await new Promise((r) => s2.close(r));
  assert.deepEqual(ok, Array(6).fill(200));
  assert.deepEqual(malas, [401, 401, 401, 429]);
});

test('logout con el refresco cuando el acceso ya caducó revoca la sesión entera', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const l = (await login(u.username)).body;
  await owner.query("UPDATE sesiones SET access_expira_en = now() - interval '1 second' WHERE usuario_id=$1", [u.id]);
  const sinToken = await call('/api/tpv/auth/logout', { method: 'POST', body: { refreshToken: l.refreshToken } });
  assert.equal(sinToken.status, 200);
  assert.equal((await call('/api/tpv/auth/refresh', { method: 'POST', body: { refreshToken: l.refreshToken } })).status, 401);
  assert.equal((await call('/api/tpv/auth/logout', { method: 'POST', body: { refreshToken: 'rt_' + 'q'.repeat(43) } })).status, 200, 'idempotente y sin revelar si existía');
});

test('un hash almacenado ilegible no delata la cuenta por tiempo ni provoca un 500', { skip }, async () => {
  const { rows } = await owner.query("INSERT INTO usuarios (username, nombre, password_hash) VALUES ('hash-roto', 'x', '$scrypt$ln=15,r=8,p=3$c2FsdA$aGFzaA') RETURNING id");
  await owner.query("INSERT INTO usuario_negocios VALUES ($1,'fruteria','tpv_operator')", [rows[0].id]);
  const t0 = performance.now();
  const r = await call('/api/tpv/auth/login', { method: 'POST', body: { username: 'hash-roto', password: 'x' } });
  assert.equal(r.status, 401);
  assert.ok(performance.now() - t0 > 20, 'paga el coste del hash ficticio');
});

test('intentos simultáneos sobre una cuenta no superan el límite de 5 fallos', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  await Promise.all(Array.from({ length: 12 }, () => call('/api/tpv/auth/login', { method: 'POST', body: { username: u.username, password: 'mala' } })));
  assert.equal((await owner.query('SELECT intentos_fallidos FROM usuarios WHERE id=$1', [u.id])).rows[0].intentos_fallidos, 5);
});
