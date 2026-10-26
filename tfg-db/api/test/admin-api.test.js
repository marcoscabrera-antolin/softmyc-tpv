// Fase 3: administración de usuarios y de negocios (/api/admin/*) y política de contraseñas.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import pg from 'pg';
import { motivoOmision } from './db-env.js';
import { createApp } from '../src/app.js';
import { createPool } from '../src/db.js';
import { hashPassword, verifyPassword } from '../src/password.js';
import { hashKey } from '../src/auth.js';
import { validatePassword } from '../src/passwordPolicy.js';

const OWNER = process.env.TEST_OWNER_URL, APP = process.env.TEST_APP_URL, AUTH = process.env.TEST_AUTH_URL;
const skip = motivoOmision(['TEST_OWNER_URL', 'TEST_APP_URL', 'TEST_AUTH_URL']);
const PASS = 'Clave-De-Prueba-2026';
const LEGACY_KEY = 'tfg_test_key_legacy_00000000000000000000';
let owner, pool, authPool, server, base, passHash, n = 0;

const call = async (path, { method = 'GET', body, token, headers = {} } = {}) => {
  const res = await fetch(base + path, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}), ...headers }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: res.status, body: await res.json().catch(() => null) };
};
const login = async (username, password = PASS, extra = {}) => call('/api/tpv/auth/login', { method: 'POST', body: { username, password, ...extra } });
async function usuario({ admin = false, activo = true, negocios = [] } = {}) {
  const username = `adm${++n}`;
  const { rows } = await owner.query('INSERT INTO usuarios (username, nombre, password_hash, es_admin, activo) VALUES ($1,$2,$3,$4,$5) RETURNING id', [username, username, passHash, admin, activo]);
  for (const [t, r] of negocios) await owner.query('INSERT INTO usuario_negocios (usuario_id, tenant_id, rol) VALUES ($1,$2,$3)', [rows[0].id, t, r]);
  return { id: rows[0].id, username };
}
const adminToken = async () => { const a = await usuario({ admin: true }); return { a, token: (await login(a.username)).body.token }; };
const nuevo = (over = {}) => ({ username: `nuevo${++n}`, nombre: 'Persona Nueva', password: 'Otra-Clave-Larga-77', negocios: [{ tenantId: 'fruteria', rol: 'encargado' }], ...over });

before(async () => {
  if (skip) return;
  owner = new pg.Client({ connectionString: OWNER }); await owner.connect();
  await owner.query('TRUNCATE auditoria_auth, sesiones, usuario_negocios, usuarios RESTART IDENTITY CASCADE');
  await owner.query('TRUNCATE api_keys RESTART IDENTITY');
  await owner.query("INSERT INTO api_keys (tenant_id, nombre, key_hash) VALUES ('fruteria','legacy',$1)", [hashKey(LEGACY_KEY)]);
  passHash = await hashPassword(PASS);
  pool = createPool(APP); authPool = createPool(AUTH);
  server = createApp(pool, { authPool, rateLimitMax: 10_000, loginLimitMax: 10_000, refreshLimitMax: 10_000, adminLimitMax: 10_000 }).listen(0, '127.0.0.1');
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { if (skip) return; await new Promise((r) => server.close(r)); await authPool.end(); await pool.end(); await owner.end(); });

test('política de contraseñas: longitud, parecido al usuario y repeticiones', () => {
  assert.equal(validatePassword('Otra-Clave-Larga-77', 'ana'), null);
  assert.match(validatePassword('corta', 'ana'), /12/);
  assert.match(validatePassword('x'.repeat(300), 'ana'), /200/);
  assert.match(validatePassword('contraseña-de-ana-1', 'ana'), /usuario/);
  assert.match(validatePassword('aaaaaaaaaaaaaaaa', 'ana'), /repet/);
  assert.match(validatePassword('123456789012', 'ana'), /secuencia|simple/);
  for (const comun of ['passwordpassword', 'qwertyuiop12', 'Admin12345678', 'Contraseña-Segura-1', 'SoftMYC-2026-tpv']) assert.match(validatePassword(comun, 'ana') ?? '', /común/, comun);
});

test('las rutas de administración exigen un administrador: sin token, operador, encargado y clave de API quedan fuera', { skip }, async () => {
  assert.equal((await call('/api/admin/users')).status, 401);
  const op = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const enc = await usuario({ negocios: [['fruteria', 'encargado']] });
  for (const u of [op, enc]) assert.equal((await call('/api/admin/users', { token: (await login(u.username)).body.token })).status, 403);
  assert.equal((await call('/api/admin/users', { headers: { 'x-api-key': LEGACY_KEY } })).status, 401, 'la clave de API no llega a administración');
});

test('GET /api/admin/restaurants lista los negocios activos', { skip }, async () => {
  const { token } = await adminToken();
  const r = await call('/api/admin/restaurants', { token });
  assert.equal(r.status, 200); assert.equal(r.body.restaurants.length, 6);
  assert.deepEqual(Object.keys(r.body.restaurants[0]).sort(), ['activo', 'id', 'nombre']);
});

test('cambiar de negocio emite una sesión nueva para ese negocio y revoca la anterior', { skip }, async () => {
  const { token } = await adminToken();
  const sw = await call('/api/admin/switch-restaurant', { method: 'POST', token, body: { restaurantId: 'softmyc_taller' } });
  assert.equal(sw.status, 200); assert.equal(sw.body.user.restaurantId, 'taller'); assert.equal(sw.body.user.role, 'admin');
  assert.equal((await call('/api/tpv/auth/me', { token })).status, 401, 'la sesión anterior queda revocada');
  assert.equal((await call('/api/tpv/auth/me', { token: sw.body.token })).body.user.restaurantId, 'taller');
  assert.equal((await call('/api/admin/switch-restaurant', { method: 'POST', token: sw.body.token, body: { restaurantId: 'noexiste' } })).status, 403);
  const op = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  assert.equal((await call('/api/admin/switch-restaurant', { method: 'POST', token: (await login(op.username)).body.token, body: { restaurantId: 'taller' } })).status, 403);
});

test('crear usuario: validaciones y alta correcta con inicio de sesión posterior', { skip }, async () => {
  const { token } = await adminToken();
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: nuevo({ password: 'corta' }) })).status, 400);
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: nuevo({ username: 'Mal Nombre!' }) })).status, 400);
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: nuevo({ negocios: [] }) })).status, 400, 'un no administrador necesita algún negocio');
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: nuevo({ negocios: [{ tenantId: 'no-existe', rol: 'encargado' }] }) })).status, 400);
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: nuevo({ negocios: [{ tenantId: 'fruteria', rol: 'superjefe' }] }) })).status, 400);
  const u = nuevo();
  const ok = await call('/api/admin/users', { method: 'POST', token, body: u });
  assert.equal(ok.status, 201); assert.ok(ok.body.id);
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: u })).status, 409, 'usuario duplicado');
  const l = await login(u.username, u.password);
  assert.equal(l.status, 200); assert.equal(l.body.user.role, 'encargado');
  const { rows } = await owner.query('SELECT password_hash FROM usuarios WHERE id=$1', [ok.body.id]);
  assert.match(rows[0].password_hash, /^\$scrypt\$/); assert.ok(!rows[0].password_hash.includes(u.password));
});

test('listar usuarios no devuelve nunca el hash de la contraseña', { skip }, async () => {
  const { token } = await adminToken();
  const r = await call('/api/admin/users', { token });
  assert.equal(r.status, 200); assert.ok(r.body.users.length >= 1);
  assert.ok(!JSON.stringify(r.body).includes('scrypt'));
  assert.ok(!('password_hash' in r.body.users[0]) && !('passwordHash' in r.body.users[0]));
  assert.ok(Array.isArray(r.body.users[0].negocios));
});

test('modificar usuario: rol y negocios cambian al instante; desactivar revoca sus sesiones y cierra el acceso', { skip }, async () => {
  const { token } = await adminToken();
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const t = (await login(u.username)).body.token;
  assert.equal((await call('/api/tpv/products/101/stock', { method: 'PUT', token: t, body: { stock: 3 } })).status, 403);
  const p = await call(`/api/admin/users/${u.id}`, { method: 'PATCH', token, body: { negocios: [{ tenantId: 'fruteria', rol: 'encargado' }], nombre: 'Nuevo Nombre' } });
  assert.equal(p.status, 200);
  assert.notEqual((await call('/api/tpv/products/101/stock', { method: 'PUT', token: t, body: { stock: 3 } })).status, 403, 'rol nuevo sin reiniciar sesión');
  assert.equal((await call(`/api/admin/users/${u.id}`, { method: 'PATCH', token, body: { activo: false } })).status, 200);
  assert.equal((await call('/api/tpv/auth/me', { token: t })).status, 401);
  assert.equal((await login(u.username)).status, 401);
  assert.equal((await call('/api/admin/users/999999', { method: 'PATCH', token, body: { nombre: 'x' } })).status, 404);
  assert.equal((await call(`/api/admin/users/${u.id}`, { method: 'PATCH', token, body: { esAdmin: 'si' } })).status, 400);
});

test('un administrador no puede quitarse el acceso ni dejar la plataforma sin administradores', { skip }, async () => {
  const { a, token } = await adminToken();
  assert.equal((await call(`/api/admin/users/${a.id}`, { method: 'PATCH', token, body: { activo: false } })).status, 409);
  assert.equal((await call(`/api/admin/users/${a.id}`, { method: 'PATCH', token, body: { esAdmin: false } })).status, 409);
  await owner.query('UPDATE usuarios SET activo=false WHERE es_admin AND id <> $1', [a.id]);
  const otro = await usuario({ admin: true });
  const { rows: [{ n: otrosActivos }] } = await owner.query('SELECT count(*)::int n FROM usuarios WHERE es_admin AND activo AND id <> $1', [otro.id]);
  assert.ok(otrosActivos >= 1);
  await owner.query('UPDATE usuarios SET activo=false WHERE id <> $1 AND es_admin', [otro.id]);
  const t2 = (await login(otro.username)).body.token;
  const x = await usuario({ admin: true });
  await owner.query('UPDATE usuarios SET activo=false WHERE id = $1', [x.id]);
  assert.equal((await call(`/api/admin/users/${otro.id}`, { method: 'PATCH', token: t2, body: { esAdmin: false } })).status, 409, 'es el último administrador');
});

test('cambiar la contraseña: la anterior deja de valer, se revocan las sesiones y se desbloquea la cuenta', { skip }, async () => {
  const { token } = await adminToken();
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const t = (await login(u.username)).body.token;
  assert.equal((await call(`/api/admin/users/${u.id}/password`, { method: 'POST', token, body: { password: 'corta' } })).status, 400);
  assert.equal((await call(`/api/admin/users/${u.id}/password`, { method: 'POST', token, body: { password: 'Frase-Segura-Nueva-2027' } })).status, 200);
  assert.equal((await call('/api/tpv/auth/me', { token: t })).status, 401);
  assert.equal((await login(u.username)).status, 401);
  assert.equal((await login(u.username, 'Frase-Segura-Nueva-2027')).status, 200);
});

test('desbloquear una cuenta bloqueada por intentos fallidos', { skip }, async () => {
  const { token } = await adminToken();
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  for (let i = 0; i < 5; i++) await login(u.username, 'mala');
  assert.equal((await login(u.username)).status, 401, 'bloqueada');
  assert.equal((await call(`/api/admin/users/${u.id}/unlock`, { method: 'POST', token })).status, 200);
  assert.equal((await login(u.username)).status, 200);
  assert.equal((await call('/api/admin/users/999999/unlock', { method: 'POST', token })).status, 404);
});

test('las acciones de administración quedan auditadas con quién las hizo', { skip }, async () => {
  const { a, token } = await adminToken();
  const u = nuevo();
  const r = await call('/api/admin/users', { method: 'POST', token, body: u });
  await call(`/api/admin/users/${r.body.id}/unlock`, { method: 'POST', token });
  const { rows } = await owner.query("SELECT evento, username FROM auditoria_auth WHERE usuario_id=$1 AND evento LIKE 'admin_%' ORDER BY id", [r.body.id]);
  assert.deepEqual(rows.map((x) => x.evento), ['admin_crear_usuario', 'admin_desbloquear']);
  assert.ok(rows.every((x) => x.username === `actor:${a.id}`));
});

test('el script de usuarios crea, lista, cambia contraseña, desbloquea y desactiva (contraseña por stdin)', { skip }, async () => {
  const { spawnSync } = await import('node:child_process');
  const run = (args, input = '') => spawnSync('node', ['scripts/usuarios.js', ...args], { input, env: { ...process.env, AUTH_DATABASE_URL: AUTH }, encoding: 'utf8' });
  const c = run(['crear', '--username', 'cli-usuario', '--nombre', 'Usuario CLI', '--negocio', 'taller:encargado'], 'Frase-Del-Script-Larga-9\n');
  assert.equal(c.status, 0, c.stderr);
  const { rows } = await owner.query("SELECT password_hash, activo FROM usuarios WHERE username='cli-usuario'");
  assert.equal(await verifyPassword('Frase-Del-Script-Larga-9', rows[0].password_hash), true);
  assert.equal(run(['crear', '--username', 'cli-usuario', '--nombre', 'x', '--negocio', 'taller:encargado'], 'Frase-Del-Script-Larga-9\n').status, 1, 'duplicado');
  assert.equal(run(['crear', '--username', 'cli-flojo', '--nombre', 'x', '--negocio', 'taller:encargado'], 'corta\n').status, 1, 'política de contraseñas');
  assert.match(run(['listar']).stdout, /cli-usuario/);
  assert.ok(!run(['listar']).stdout.includes('scrypt'));
  for (let i = 0; i < 5; i++) await login('cli-usuario', 'mala');
  assert.equal(run(['desbloquear', '--username', 'cli-usuario']).status, 0);
  assert.equal(run(['password', '--username', 'cli-usuario'], 'Frase-Alternativa-Larga-5\n').status, 0);
  assert.equal((await login('cli-usuario', 'Frase-Alternativa-Larga-5')).status, 200);
  assert.equal(run(['desactivar', '--username', 'cli-usuario']).status, 0);
  assert.equal((await login('cli-usuario', 'Frase-Alternativa-Larga-5')).status, 401);
  assert.equal(run(['crear', '--username', 'cli-admin', '--nombre', 'Admin CLI', '--admin'], 'Llave-Maestra-Del-Sistema-1\n').status, 0);
  assert.equal(run(['comando-raro']).status, 2);
});

test('crear o promover administradores y cambiar la contraseña de otro administrador exige reconfirmar la contraseña', { skip }, async () => {
  const { a, token } = await adminToken();
  const sinConfirmar = await call('/api/admin/users', { method: 'POST', token, body: nuevo({ esAdmin: true, negocios: [] }) });
  assert.equal(sinConfirmar.status, 403);
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: nuevo({ esAdmin: true, negocios: [], adminPassword: 'incorrecta' }) })).status, 403);
  const ok = await call('/api/admin/users', { method: 'POST', token, body: nuevo({ esAdmin: true, negocios: [], adminPassword: PASS }) });
  assert.equal(ok.status, 201);
  const op = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  assert.equal((await call(`/api/admin/users/${op.id}`, { method: 'PATCH', token, body: { esAdmin: true } })).status, 403);
  assert.equal((await call(`/api/admin/users/${op.id}`, { method: 'PATCH', token, body: { esAdmin: true, adminPassword: PASS } })).status, 200);
  const nueva = 'Frase-Segura-Otra-Larga-31';
  assert.equal((await call(`/api/admin/users/${ok.body.id}/password`, { method: 'POST', token, body: { password: nueva } })).status, 403, 'otro administrador: reconfirmar');
  assert.equal((await call(`/api/admin/users/${ok.body.id}/password`, { method: 'POST', token, body: { password: nueva, adminPassword: PASS } })).status, 200);
  const normal = await usuario({ negocios: [['taller', 'tpv_operator']] });
  assert.equal((await call(`/api/admin/users/${normal.id}/password`, { method: 'POST', token, body: { password: nueva } })).status, 200, 'un usuario normal no lo exige');
  assert.ok(a.id);
});

test('reconfirmar con una contraseña incorrecta cuenta como intento fallido y puede bloquear al administrador', { skip }, async () => {
  const { a, token } = await adminToken();
  for (let i = 0; i < 5; i++) await call('/api/admin/users', { method: 'POST', token, body: nuevo({ esAdmin: true, negocios: [], adminPassword: 'mala' }) });
  assert.ok((await owner.query('SELECT bloqueado_hasta FROM usuarios WHERE id=$1', [a.id])).rows[0].bloqueado_hasta > new Date());
  assert.equal((await call('/api/admin/users', { method: 'POST', token, body: nuevo({ esAdmin: true, negocios: [], adminPassword: PASS }) })).status, 403, 'bloqueado: ni la correcta vale');
});

test('un administrador no puede dejar a un usuario normal sin ningún negocio', { skip }, async () => {
  const { token } = await adminToken();
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  assert.equal((await call(`/api/admin/users/${u.id}`, { method: 'PATCH', token, body: { negocios: [] } })).status, 400);
  assert.equal((await owner.query('SELECT count(*)::int n FROM usuario_negocios WHERE usuario_id=$1', [u.id])).rows[0].n, 1);
});

test('cambiar de negocio es atómico y no expulsa las sesiones del administrador en otros dispositivos', { skip }, async () => {
  const a = await usuario({ admin: true });
  const tokens = []; for (let i = 0; i < 3; i++) tokens.push((await login(a.username)).body.token);
  let actual = tokens[0];
  for (const t of ['taller', 'mayorista', 'fruteria', 'taller']) {
    const sw = await call('/api/admin/switch-restaurant', { method: 'POST', token: actual, body: { restaurantId: t } });
    assert.equal(sw.status, 200); actual = sw.body.token;
  }
  const { rows } = await owner.query('SELECT count(DISTINCT familia)::int n FROM sesiones WHERE usuario_id=$1 AND revocada_en IS NULL AND reemplazada_por IS NULL', [a.id]);
  assert.equal(rows[0].n, 3, 'las 3 sesiones siguen: el cambio sustituye la actual sin tocar las demás');
  assert.equal((await call('/api/tpv/auth/me', { token: tokens[1] })).status, 200);
  assert.equal((await call('/api/tpv/auth/me', { token: tokens[0] })).status, 401);
  assert.equal((await owner.query("SELECT count(*)::int n FROM auditoria_auth WHERE usuario_id=$1 AND evento='admin_switch'", [a.id])).rows[0].n, 4);
});

test('la auditoría distingue promover, degradar, desactivar y cambiar negocios', { skip }, async () => {
  const { token } = await adminToken();
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const patch = (body) => call(`/api/admin/users/${u.id}`, { method: 'PATCH', token, body });
  await patch({ negocios: [{ tenantId: 'taller', rol: 'encargado' }] });
  await patch({ esAdmin: true, adminPassword: PASS });
  await patch({ esAdmin: false });
  await patch({ activo: false });
  await patch({ activo: true });
  const { rows } = await owner.query("SELECT evento FROM auditoria_auth WHERE usuario_id=$1 AND evento LIKE 'admin_%' ORDER BY id", [u.id]);
  assert.deepEqual(rows.map((r) => r.evento), ['admin_negocios', 'admin_promover', 'admin_degradar', 'admin_desactivar', 'admin_activar']);
});

test('el área de administración tiene su propio límite de peticiones y el servidor ocupado responde 503', { skip }, async () => {
  const { token } = await adminToken();
  const s2 = createApp(pool, { authPool, adminLimitMax: 3, rateLimitMax: 10_000 }).listen(0, '127.0.0.1');
  await new Promise((r) => s2.once('listening', r));
  const codes = [];
  for (let i = 0; i < 5; i++) codes.push((await fetch(`http://127.0.0.1:${s2.address().port}/api/admin/restaurants`, { headers: { authorization: `Bearer ${token}` } })).status);
  await new Promise((r) => s2.close(r));
  assert.deepEqual(codes, [200, 200, 200, 429, 429]);
});

test('el script informa de un fallo de conexión sin volcar trazas', { skip }, async () => {
  const { spawnSync } = await import('node:child_process');
  const r = spawnSync('node', ['scripts/usuarios.js', 'listar'], { env: { ...process.env, AUTH_DATABASE_URL: 'postgres://x:y@127.0.0.1:1/nada' }, encoding: 'utf8' });
  assert.equal(r.status, 1);
  assert.ok(!r.stderr.includes('    at '), 'sin pila de llamadas');
});
