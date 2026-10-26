// Fase 1 de la autenticación con cuentas de usuario: tablas, permisos y funciones SECURITY DEFINER de la base.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import pg from 'pg';
import { motivoOmision } from './db-env.js';

const OWNER = process.env.TEST_OWNER_URL;
const APP = process.env.TEST_APP_URL;
const AUTH = process.env.TEST_AUTH_URL;
const skip = motivoOmision(['TEST_OWNER_URL', 'TEST_APP_URL', 'TEST_AUTH_URL']);

const h = (s) => createHash('sha256').update(s).digest();
const FAKE_HASH = '$scrypt$ln=17,r=8,p=1$c2FsdA$aGFzaA';
let owner, app, auth;
let n = 0;

before(async () => {
  if (skip) return;
  owner = new pg.Client({ connectionString: OWNER }); await owner.connect();
  app = new pg.Client({ connectionString: APP }); await app.connect();
  auth = new pg.Client({ connectionString: AUTH }); await auth.connect();
  await owner.query('TRUNCATE auditoria_auth, sesiones, usuario_negocios, usuarios RESTART IDENTITY CASCADE');
});
after(async () => { if (skip) return; await auth.end(); await app.end(); await owner.end(); });

async function usuario({ admin = false, activo = true, negocios = [] } = {}) {
  const username = `usr${++n}`;
  const { rows } = await owner.query(
    'INSERT INTO usuarios (username, nombre, password_hash, es_admin, activo) VALUES ($1,$2,$3,$4,$5) RETURNING id',
    [username, `Usuario ${n}`, FAKE_HASH, admin, activo]);
  for (const [tenant, rol] of negocios) {
    await owner.query('INSERT INTO usuario_negocios (usuario_id, tenant_id, rol) VALUES ($1,$2,$3)', [rows[0].id, tenant, rol]);
  }
  return { id: rows[0].id, username };
}
const crear = (u, tenant, a = 'a', r = 'r', ttl = ['30 minutes', '7 days']) =>
  auth.query('SELECT * FROM auth_crear_sesion($1,$2,$3,$4,$5::interval,$6::interval,$7)', [u.id, tenant, h(a), h(r), ttl[0], ttl[1], 'tablet']);
const validar = async (a) => (await app.query('SELECT * FROM auth_validar_acceso($1)', [h(a)])).rows;
const rotar = async (r, na, nr) => (await auth.query('SELECT * FROM auth_rotar_sesion($1,$2,$3,$4::interval,$5,$6)', [h(r), h(na), h(nr), '30 minutes', '10.0.0.1', 'ua'])).rows[0];
const denegado = (p) => assert.rejects(p, (e) => e.code === '42501');

test('tfg_app no puede leer ni modificar usuarios, negocios de usuario ni sesiones directamente', { skip }, async () => {
  for (const t of ['usuarios', 'usuario_negocios', 'sesiones']) {
    await denegado(app.query(`SELECT * FROM ${t}`));
  }
  await denegado(app.query("UPDATE usuarios SET es_admin = true"));
  await denegado(app.query("DELETE FROM sesiones"));
});

test('la auditoría es de solo inserción para tfg_app', { skip }, async () => {
  await app.query("INSERT INTO auditoria_auth (evento, exito, username) VALUES ('prueba', true, 'x')");
  await denegado(app.query('SELECT * FROM auditoria_auth'));
  await denegado(app.query("UPDATE auditoria_auth SET exito = false"));
  await denegado(app.query('DELETE FROM auditoria_auth'));
});

test('las funciones sensibles tienen search_path fijo y no son ejecutables por PUBLIC', { skip }, async () => {
  const { rows } = await owner.query(`
    SELECT p.proname, p.prosecdef, p.proconfig::text AS cfg,
           has_function_privilege('public', p.oid, 'EXECUTE') AS publico
      FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'public' AND p.proname LIKE 'auth\\_%'`);
  assert.ok(rows.length >= 8, 'faltan funciones auth_*');
  for (const f of rows) {
    assert.equal(f.prosecdef, true, `${f.proname} debe ser SECURITY DEFINER`);
    assert.match(f.cfg, /search_path/, `${f.proname} sin search_path fijo`);
    assert.equal(f.publico, false, `${f.proname} ejecutable por PUBLIC`);
  }
});

test('auth_credencial devuelve los datos de un usuario y nada para uno inexistente', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'encargado']] });
  const ok = (await auth.query('SELECT * FROM auth_credencial($1)', [u.username])).rows;
  assert.equal(ok.length, 1);
  assert.equal(ok[0].password_hash, FAKE_HASH);
  assert.equal(ok[0].activo, true);
  assert.equal((await auth.query('SELECT * FROM auth_credencial($1)', ['no-existe'])).rows.length, 0);
});

test('5 fallos bloquean la cuenta; un acierto antes lo reinicia; el bloqueo no se alarga mientras dura', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const fallo = async () => (await auth.query('SELECT auth_registrar_intento($1,$2,false,$3,$4) AS b', [u.username, u.id, '1.1.1.1', 'ua'])).rows[0].b;
  for (let i = 0; i < 4; i++) assert.equal(await fallo(), null);
  await auth.query('SELECT auth_registrar_intento($1,$2,true,$3,$4)', [u.username, u.id, '1.1.1.1', 'ua']);
  assert.equal((await owner.query('SELECT intentos_fallidos FROM usuarios WHERE id=$1', [u.id])).rows[0].intentos_fallidos, 0);
  for (let i = 0; i < 4; i++) assert.equal(await fallo(), null);
  const bloqueado = await fallo();
  assert.ok(bloqueado instanceof Date && bloqueado > new Date(Date.now() + 14 * 60_000), 'bloqueo de unos 15 min');
  const antes = (await owner.query('SELECT bloqueado_hasta, intentos_fallidos FROM usuarios WHERE id=$1', [u.id])).rows[0];
  await fallo();
  const despues = (await owner.query('SELECT bloqueado_hasta, intentos_fallidos FROM usuarios WHERE id=$1', [u.id])).rows[0];
  assert.equal(despues.intentos_fallidos, antes.intentos_fallidos, 'no cuenta intentos mientras está bloqueada');
  assert.deepEqual(despues.bloqueado_hasta, antes.bloqueado_hasta);
});

test('un intento con usuario inexistente se audita sin crear nada', { skip }, async () => {
  await auth.query("SELECT auth_registrar_intento('fantasma', NULL, false, '2.2.2.2', 'ua')");
  const { rows } = await owner.query("SELECT evento, exito, usuario_id FROM auditoria_auth WHERE username='fantasma'");
  assert.deepEqual(rows[0], { evento: 'login', exito: false, usuario_id: null });
});

test('auth_negocios_usuario: un usuario ve solo los suyos y el admin de plataforma ve todos los activos', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'encargado'], ['taller', 'tpv_operator']] });
  const suyos = (await auth.query('SELECT * FROM auth_negocios_usuario($1) ORDER BY tenant_id', [u.id])).rows;
  assert.deepEqual(suyos.map((r) => [r.tenant_id, r.rol]), [['fruteria', 'encargado'], ['taller', 'tpv_operator']]);
  const a = await usuario({ admin: true });
  const todos = (await auth.query('SELECT * FROM auth_negocios_usuario($1)', [a.id])).rows;
  assert.equal(todos.length, 6);
  assert.ok(todos.every((r) => r.rol === 'admin'));
});

test('crear sesión: rol según el negocio, acceso denegado fuera de sus negocios y el admin puede no tener negocio', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'encargado']] });
  const s = (await crear(u, 'fruteria', 'a1', 'r1')).rows[0];
  assert.equal(s.rol, 'encargado');
  await denegado(crear(u, 'taller', 'a2', 'r2'));
  await denegado(crear(u, null, 'a3', 'r3'));
  const a = await usuario({ admin: true });
  assert.equal((await crear(a, null, 'a4', 'r4')).rows[0].rol, 'admin');
  assert.equal((await crear(a, 'mayorista', 'a5', 'r5')).rows[0].rol, 'admin');
  await denegado(crear(a, 'no-existe', 'a6', 'r6'));
  const inactivo = await usuario({ activo: false, negocios: [['fruteria', 'tpv_operator']] });
  await denegado(crear(inactivo, 'fruteria', 'a7', 'r7'));
});

test('validar acceso: sesión válida, desconocido, caducado, usuario desactivado', { skip }, async () => {
  const u = await usuario({ negocios: [['taller', 'tpv_operator']] });
  await crear(u, 'taller', 'v1', 'vr1');
  const v = await validar('v1');
  assert.equal(v.length, 1);
  assert.equal(v[0].tenant_id, 'taller'); assert.equal(v[0].rol, 'tpv_operator'); assert.equal(v[0].es_admin, false);
  assert.equal((await validar('desconocido')).length, 0);
  await crear(u, 'taller', 'v2', 'vr2');
  await owner.query("UPDATE sesiones SET access_expira_en = now() - interval '1 second' WHERE access_hash = $1", [h('v2')]);
  assert.equal((await validar('v2')).length, 0, 'access token caducado');
  await owner.query('UPDATE usuarios SET activo = false WHERE id = $1', [u.id]);
  assert.equal((await validar('v1')).length, 0, 'usuario desactivado');
});

test('rotar: devuelve tokens nuevos, invalida los anteriores y respeta la caducidad absoluta', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'encargado']] });
  const s = (await crear(u, 'fruteria', 'ra1', 'rr1')).rows[0];
  const r = await rotar('rr1', 'ra2', 'rr2');
  assert.equal(r.estado, 'ok'); assert.equal(r.rol, 'encargado'); assert.equal(r.tenant_id, 'fruteria');
  assert.deepEqual(r.refresh_expira_en, s.refresh_expira_en, 'la caducidad del refresco es absoluta');
  assert.equal((await validar('ra1')).length, 0, 'el access anterior queda invalidado');
  assert.equal((await validar('ra2')).length, 1);
  assert.equal((await rotar('no-existe', 'x', 'y')).estado, 'invalido');
  const c = (await crear(u, 'fruteria', 'rc1', 'rcr1')).rows[0];
  await owner.query("UPDATE sesiones SET creada_en = now() - interval '2 hours', familia_expira_en = now() - interval '1 second' WHERE refresh_hash = $1", [h('rcr1')]);
  assert.equal((await rotar('rcr1', 'rc2', 'rcr2')).estado, 'caducado');
  assert.ok(c.sesion_id);
});

test('reutilizar un refresco ya rotado revoca toda la familia y se audita', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  await crear(u, 'fruteria', 'fa1', 'fr1');
  assert.equal((await rotar('fr1', 'fa2', 'fr2')).estado, 'ok');
  assert.equal((await rotar('fr1', 'fa3', 'fr3')).estado, 'reutilizado');
  assert.equal((await validar('fa2')).length, 0, 'la rama legítima también queda revocada');
  assert.equal((await rotar('fr2', 'fa4', 'fr4')).estado, 'invalido');
  const { rows } = await owner.query("SELECT 1 FROM auditoria_auth WHERE usuario_id=$1 AND evento='refresh_reutilizado'", [u.id]);
  assert.equal(rows.length, 1);
});

test('rotar con el usuario desactivado o sin el negocio revoca la sesión', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  await crear(u, 'fruteria', 'ia1', 'ir1');
  await owner.query('DELETE FROM usuario_negocios WHERE usuario_id = $1', [u.id]);
  assert.equal((await rotar('ir1', 'ia2', 'ir2')).estado, 'inactivo');
  assert.equal((await validar('ia1')).length, 0);
});

test('revocar sesión (logout) y revocar todas las de un usuario', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  await crear(u, 'fruteria', 'la1', 'lr1');
  assert.equal((await auth.query('SELECT auth_revocar_sesion($1) AS ok', [h('la1')])).rows[0].ok, true);
  assert.equal((await validar('la1')).length, 0);
  assert.equal((await auth.query('SELECT auth_revocar_sesion($1) AS ok', [h('no-existe')])).rows[0].ok, false);
  await crear(u, 'fruteria', 'lb1', 'lr2'); await crear(u, 'fruteria', 'lb2', 'lr3');
  assert.equal((await auth.query('SELECT auth_revocar_usuario($1) AS n', [u.id])).rows[0].n, 2);
  assert.equal((await validar('lb1')).length + (await validar('lb2')).length, 0);
});

test('RLS: cada negocio solo ve y modifica sus ventas; sin negocio no se ve ninguna', { skip }, async () => {
  const uuidFru = randomUUID(), uuidTal = randomUUID();
  const ids = [uuidFru, uuidTal];
  await owner.query(`INSERT INTO ventas (tenant_id, uuid, id_local, fecha, total, items, metodo_pago) VALUES
    ('fruteria', $1, 1, now(), 1.00, '[]', 'Efectivo'), ('taller', $2, 2, now(), 1.00, '[]', 'Efectivo')`, [uuidFru, uuidTal]);
  // Cada consulta va en su transacción con app.tenant fijado (como withTenant) y se deshace al final: no deja datos.
  const enNegocio = async (tenant, fn) => {
    await app.query('BEGIN');
    try {
      if (tenant) await app.query("SELECT set_config('app.tenant', $1, true)", [tenant]);
      return await fn(app);
    } finally {
      await app.query('ROLLBACK');
    }
  };
  const visibles = (tenant) => enNegocio(tenant, async (db) =>
    (await db.query('SELECT uuid FROM ventas WHERE uuid = ANY($1) ORDER BY uuid', [ids])).rows.map((r) => r.uuid));
  try {
    assert.deepEqual(await visibles('fruteria'), [uuidFru].sort(), 'fruteria solo ve su venta');
    assert.deepEqual(await visibles('taller'), [uuidTal].sort(), 'taller solo ve su venta');
    assert.deepEqual(await visibles(null), [], 'sin app.tenant no se ve ninguna venta');
    const modificadas = await enNegocio('fruteria', async (db) =>
      (await db.query('UPDATE ventas SET total = 99 WHERE uuid = $1', [uuidTal])).rowCount);
    assert.equal(modificadas, 0, 'fruteria no puede modificar una venta de taller');
    assert.equal(Number((await owner.query('SELECT total FROM ventas WHERE uuid=$1', [uuidTal])).rows[0].total), 1);
    await assert.rejects(enNegocio('fruteria', (db) => db.query(
      "INSERT INTO ventas (tenant_id, uuid, id_local, fecha, total, items, metodo_pago) VALUES ('taller', $1, 3, now(), 1, '[]', 'Efectivo')",
      [randomUUID()])), (e) => e.code === '42501', 'fruteria no puede insertar ventas de taller');
  } finally {
    await owner.query('DELETE FROM ventas WHERE uuid = ANY($1)', [ids]);
  }
  assert.ok((await app.query('SELECT count(*)::int n FROM api_keys')).rows[0].n >= 0);
});

test('un bloqueo por origen no afecta a otra IP; un acierto desde otra IP no levanta el bloqueo ajeno', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const reg = async (ok, ip) => (await auth.query('SELECT auth_registrar_intento($1,$2,$3,$4,$5) AS b', [u.username, u.id, ok, ip, 'ua'])).rows[0].b;
  const vigente = async (ip) => (await auth.query('SELECT auth_bloqueo_vigente($1,$2) AS b', [u.id, ip])).rows[0].b;
  for (let i = 0; i < 5; i++) await reg(false, '6.6.6.6');
  assert.ok((await vigente('6.6.6.6')) instanceof Date, 'el origen atacante queda bloqueado');
  assert.equal(await vigente('7.7.7.7'), null, 'otro origen no está bloqueado');
  assert.equal(await reg(true, '7.7.7.7'), null, 'el usuario entra desde otra red');
  assert.ok((await vigente('6.6.6.6')) instanceof Date, 'el acierto de otro origen no levanta el bloqueo');
});

// ---- Endurecimiento tras la revisión de seguridad ----
test('tfg_app solo puede validar accesos: no puede crear sesiones, leer credenciales ni tocar bloqueos', { skip }, async () => {
  const u = await usuario({ admin: true });
  for (const [sql, args] of [
    ['SELECT * FROM auth_crear_sesion($1,NULL,$2,$3,$4::interval,$5::interval,$6)', [u.id, h('x'), h('y'), '30 minutes', '7 days', 'd']],
    ['SELECT * FROM auth_credencial($1)', [u.username]],
    ["SELECT auth_registrar_intento($1,$2,true,'ip','ua')", [u.username, u.id]],
    ['SELECT * FROM auth_negocios_usuario($1)', [u.id]],
    ['SELECT auth_revocar_usuario($1)', [u.id]],
    ['SELECT * FROM auth_rotar_sesion($1,$2,$3,$4::interval,$5,$6)', [h('a'), h('b'), h('c'), '30 minutes', 'ip', 'ua']],
    ['SELECT auth_revocar_sesion($1)', [h('a')]],
  ]) await denegado(app.query(sql, args));
  assert.equal((await app.query('SELECT * FROM auth_validar_acceso($1)', [h('x')])).rows.length, 0);
});

test('tfg_auth ejecuta las funciones pero no lee las tablas', { skip }, async () => {
  for (const t of ['usuarios', 'sesiones', 'usuario_negocios']) await denegado(auth.query(`SELECT * FROM ${t}`));
  await denegado(auth.query('SELECT * FROM auth_purgar()'));
});

test('las funciones y tablas de autenticación no pertenecen a un superusuario', { skip }, async () => {
  const { rows } = await owner.query(`
    SELECT 'funcion' AS tipo, p.proname AS nombre, r.rolsuper FROM pg_proc p JOIN pg_roles r ON r.oid = p.proowner
     WHERE p.proname LIKE 'auth\_%' AND p.pronamespace = 'public'::regnamespace
    UNION ALL SELECT 'tabla', c.relname, r.rolsuper FROM pg_class c JOIN pg_roles r ON r.oid = c.relowner
     WHERE c.relname IN ('usuarios','usuario_negocios','sesiones','auditoria_auth') AND c.relkind = 'r'`);
  assert.ok(rows.length >= 14);
  for (const r of rows) assert.equal(r.rolsuper, false, `${r.tipo} ${r.nombre} pertenece a un superusuario`);
});

test('validar acceso refleja cambios de permisos: negocio retirado, rol cambiado y es_admin quitado', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'encargado']] });
  await crear(u, 'fruteria', 'pa1', 'pr1');
  await owner.query("UPDATE usuario_negocios SET rol='tpv_operator' WHERE usuario_id=$1", [u.id]);
  assert.equal((await validar('pa1'))[0].rol, 'tpv_operator', 'el rol sale de los permisos actuales, no del guardado');
  await owner.query('DELETE FROM usuario_negocios WHERE usuario_id=$1', [u.id]);
  assert.equal((await validar('pa1')).length, 0, 'sin negocio ya no vale');
  const a = await usuario({ admin: true });
  await crear(a, 'taller', 'pa2', 'pr2');
  assert.equal((await validar('pa2')).length, 1);
  await owner.query('UPDATE usuarios SET es_admin=false WHERE id=$1', [a.id]);
  assert.equal((await validar('pa2')).length, 0, 'sin es_admin ni negocio asignado ya no vale');
});

test('un acierto no desbloquea una cuenta bloqueada y el contador no desborda', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const reg = async (ok) => (await auth.query('SELECT auth_registrar_intento($1,$2,$3,$4,$5) AS b', [u.username, u.id, ok, '1.1.1.1', 'ua'])).rows[0].b;
  for (let i = 0; i < 5; i++) await reg(false);
  const bloqueo = await reg(true);
  assert.ok(bloqueo instanceof Date && bloqueo > new Date(), 'devuelve el bloqueo y no lo levanta');
  assert.ok((await owner.query('SELECT bloqueado_hasta FROM usuarios WHERE id=$1', [u.id])).rows[0].bloqueado_hasta > new Date());
  await owner.query('UPDATE login_intentos_ip SET fallos = 400, bloqueado_hasta = NULL WHERE usuario_id=$1', [u.id]);
  const b = await reg(false);
  assert.ok(b instanceof Date && b < new Date(Date.now() + 25 * 3600_000) && b > new Date(Date.now() + 23 * 3600_000), 'tope de 24 h');
});

test('los TTL se acotan en la base: el llamador no puede pedir sesiones eternas ni negativas', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  const largo = (await crear(u, 'fruteria', 't1', 'tr1', ['100 years', '100 years'])).rows[0];
  assert.ok(largo.access_expira_en < new Date(Date.now() + 61 * 60_000), 'el acceso dura como mucho 1 h');
  assert.ok(largo.refresh_expira_en < new Date(Date.now() + 31 * 86400_000), 'el refresco dura como mucho 30 días');
  const neg = (await crear(u, 'fruteria', 't2', 'tr2', ['-5 minutes', '-5 days'])).rows[0];
  assert.ok(neg.access_expira_en > new Date(), 'el acceso nunca nace caducado');
  assert.ok(neg.refresh_expira_en > new Date());
});

test('límite de sesiones activas por usuario y longitudes en la auditoría', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  for (let i = 0; i < 13; i++) await crear(u, 'fruteria', `ma${i}`, `mr${i}`);
  const { rows } = await owner.query('SELECT count(DISTINCT familia)::int n FROM sesiones WHERE usuario_id=$1 AND revocada_en IS NULL AND reemplazada_por IS NULL', [u.id]);
  assert.equal(rows[0].n, 10, 'se conservan las 10 sesiones más recientes');
  assert.equal((await validar('ma12')).length, 1);
  assert.equal((await validar('ma0')).length, 0, 'la más antigua se revoca');
  await auth.query("SELECT auth_registrar_intento($1, NULL, false, $2, $3)", ['x'.repeat(500), '9'.repeat(200), 'ua'.repeat(300)]);
  const l = (await owner.query("SELECT length(username) a, length(ip) b, length(user_agent) c FROM auditoria_auth ORDER BY id DESC LIMIT 1")).rows[0];
  assert.deepEqual(l, { a: 64, b: 45, c: 200 });
  await assert.rejects(app.query("INSERT INTO auditoria_auth (evento, exito, username) VALUES ('x', true, $1)", ['y'.repeat(500)]), (e) => e.code === '23514');
});

test('purgar elimina familias viejas enteras y auditoría antigua, sin tocar lo reciente', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  await crear(u, 'fruteria', 'pg1', 'pgr1');
  await rotar('pgr1', 'pg2', 'pgr2');
  await crear(u, 'fruteria', 'pg3', 'pgr3');
  await owner.query("UPDATE sesiones SET revocada_en = now() - interval '60 days', creada_en = now() - interval '90 days', familia_expira_en = now() - interval '60 days' WHERE access_hash = ANY($1)", [[h('pg1'), h('pg2')]]);
  await owner.query("INSERT INTO auditoria_auth (ts, evento, exito) VALUES (now() - interval '400 days', 'vieja', true)");
  const r = (await owner.query('SELECT * FROM auth_purgar()')).rows[0];
  assert.equal(Number(r.sesiones_borradas), 2, 'se borra la familia entera (2 generaciones)');
  assert.ok(Number(r.auditoria_borrada) >= 1);
  assert.equal((await validar('pg3')).length, 1, 'la sesión reciente no se toca');
});

test('revocar por refresco: revoca la familia aunque el acceso haya caducado y solo la puede ejecutar tfg_auth', { skip }, async () => {
  const u = await usuario({ negocios: [['fruteria', 'tpv_operator']] });
  await crear(u, 'fruteria', 'rv1', 'rvr1');
  await owner.query("UPDATE sesiones SET access_expira_en = now() - interval '1 second' WHERE access_hash = $1", [h('rv1')]);
  await denegado(app.query('SELECT auth_revocar_refresh($1)', [h('rvr1')]));
  assert.equal((await auth.query('SELECT auth_revocar_refresh($1) AS ok', [h('rvr1')])).rows[0].ok, true);
  assert.equal((await rotar('rvr1', 'rv2', 'rvr2')).estado, 'invalido', 'ya no se puede rotar');
  assert.equal((await auth.query('SELECT auth_revocar_refresh($1) AS ok', [h('no-existe')])).rows[0].ok, false);
});

// ---- Funciones de administración (fase 3) ----
test('las funciones de administración solo las ejecuta tfg_auth y pertenecen a un rol sin privilegios', { skip }, async () => {
  const u = await usuario({ admin: true });
  await denegado(app.query("SELECT auth_admin_crear_usuario(NULL,'x1y2z3',$1,$2,false,'[]'::jsonb)", ['x', '$scrypt$ln=15,r=8,p=3$c2FsdHNhbHQ$aGFzaGhhc2hoYXNoaGFzaA']));
  await denegado(app.query('SELECT * FROM auth_admin_listar_usuarios()'));
  await denegado(app.query('SELECT auth_admin_desbloquear(NULL,$1)', [u.id]));
  const { rows } = await owner.query(`SELECT p.proname, r.rolsuper FROM pg_proc p JOIN pg_roles r ON r.oid = p.proowner
    WHERE p.proname LIKE 'auth\\_admin\\_%' AND p.pronamespace = 'public'::regnamespace`);
  assert.ok(rows.length >= 5);
  for (const r of rows) assert.equal(r.rolsuper, false, r.proname);
  assert.equal((await auth.query('SELECT * FROM auth_admin_listar_usuarios()')).rows.length > 0, true);
});

test('el último administrador activo no se puede desactivar ni degradar (ni por la base)', { skip }, async () => {
  await owner.query('UPDATE usuarios SET activo = false WHERE es_admin');
  const a = await usuario({ admin: true });
  await assert.rejects(auth.query('SELECT auth_admin_actualizar_usuario(NULL,$1,NULL,false,NULL,NULL)', [a.id]), (e) => /ultimo_administrador/.test(e.message));
  await assert.rejects(auth.query('SELECT auth_admin_actualizar_usuario(NULL,$1,NULL,NULL,false,NULL)', [a.id]), (e) => /ultimo_administrador/.test(e.message));
  const b = await usuario({ admin: true });
  assert.equal((await auth.query('SELECT auth_admin_actualizar_usuario(NULL,$1,NULL,false,NULL,NULL) AS ok', [a.id])).rows[0].ok, true);
  assert.equal((await owner.query('SELECT activo FROM usuarios WHERE id=$1', [a.id])).rows[0].activo, false);
  assert.ok(b.id);
});
