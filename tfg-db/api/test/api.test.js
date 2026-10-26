import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import pg from 'pg';
import { motivoOmision } from './db-env.js';
import { createApp, guardarVentas } from '../src/app.js';
import { createPool, withTenant } from '../src/db.js';
import { hashKey, normalizeTenant } from '../src/auth.js';
import { legacyUuid, parseOrder } from '../src/validate.js';

const OWNER = process.env.TEST_OWNER_URL;
const APP = process.env.TEST_APP_URL;
const skip = motivoOmision(['TEST_OWNER_URL', 'TEST_APP_URL']);

const KEY_FRU = 'tfg_test_key_fruteria_000000000000000000';
const KEY_TAL = 'tfg_test_key_taller_00000000000000000000';
let owner, pool, server, base;

const venta = (over = {}) => ({
  localId: 1, total: 2.4, metodoPago: 'Efectivo', origen: 'tpv', estado: 'impreso', fecha: Date.now() - 1000,
  items: [{ nombre: 'Manzanas', cantidad: 1, precio_unitario: 2.4, categoria: 'Frutas', importe_linea: 2.4 }], ...over,
});
const call = (path, { key = KEY_FRU, method = 'GET', body, headers = {} } = {}) =>
  fetch(base + path, { method, headers: { 'content-type': 'application/json', ...(key ? { 'x-api-key': key } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body) });

before(async () => {
  if (skip) return;
  owner = new pg.Client({ connectionString: OWNER }); await owner.connect();
  await owner.query('TRUNCATE ventas, ventas_historial, api_keys RESTART IDENTITY');
  await owner.query("INSERT INTO api_keys (tenant_id,nombre,key_hash) VALUES ('fruteria','t',$1),('taller','t',$2)", [hashKey(KEY_FRU), hashKey(KEY_TAL)]);
  await owner.query("DELETE FROM productos WHERE tenant_id IN ('fruteria','taller')");
  await owner.query("INSERT INTO productos (tenant_id,codigo,nombre,precio,categoria,unidad_medida) VALUES ('fruteria',101,'Manzanas',2.40,'Frutas','KG'),('taller',201,'Aceite',9.99,'Piezas','UD')");
  pool = createPool(APP);
  server = createApp(pool, { rateLimitMax: 10_000 }).listen(0, '127.0.0.1');
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { if (skip) return; await new Promise((r) => server.close(r)); await pool.end(); await owner.end(); });

test('normalizeTenant quita prefijo softmyc y sufijo _vN', () => {
  assert.equal(normalizeTenant('softmyc_fruteria'), 'fruteria');
  assert.equal(normalizeTenant('lanube_v2'), 'lanube');
  assert.equal(normalizeTenant('../etc'), null);
});
test('legacyUuid es determinista y distingue ventas', () => {
  assert.equal(legacyUuid('a', 1, 5), legacyUuid('a', 1, 5));
  assert.notEqual(legacyUuid('a', 1, 5), legacyUuid('a', 2, 5));
  assert.match(legacyUuid('a', 1, 5), /^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
test('parseOrder rechaza total incoherente, método inválido y fecha futura', () => {
  assert.equal(parseOrder(venta({ total: 9 }), 'x').ok, false);
  assert.equal(parseOrder(venta({ metodoPago: 'Bizum' }), 'x').ok, false);
  assert.equal(parseOrder(venta({ fecha: Date.now() + 3 * 86_400_000 }), 'x').ok, false);
  assert.equal(parseOrder(venta({ metodoPago: 'TARJETA' }), 'x').order.metodo, 'Tarjeta');
});

test('health responde sin autenticación', { skip }, async () => {
  assert.equal((await call('/health', { key: null })).status, 200);
});
test('sin clave o con clave falsa: 401', { skip }, async () => {
  assert.equal((await call('/api/tpv/products', { key: null })).status, 401);
  assert.equal((await call('/api/tpv/products', { key: 'tfg_clave_inventada_000000000000' })).status, 401);
});
test('x-tenant-id que no corresponde a la clave: 403', { skip }, async () => {
  assert.equal((await call('/api/tpv/products', { headers: { 'x-tenant-id': 'taller' } })).status, 403);
  assert.equal((await call('/api/tpv/products', { headers: { 'x-tenant-id': 'softmyc_fruteria' } })).status, 200);
});
test('sync guarda la venta y es idempotente', { skip }, async () => {
  const body = { restaurantId: 'fruteria', orders: [venta({ localId: 7, uuid: '22222222-2222-4222-8222-222222222222' })] };
  const r1 = await (await call('/api/tpv/orders/sync', { method: 'POST', body })).json();
  const r2 = await (await call('/api/tpv/orders/sync', { method: 'POST', body })).json();
  assert.deepEqual(r1.syncedIds, [7]); assert.deepEqual(r2.syncedIds, [7]);
  const { rows } = await owner.query("SELECT count(*)::int n, min(metodo_pago) m FROM ventas WHERE tenant_id='fruteria' AND uuid='22222222-2222-4222-8222-222222222222'");
  assert.deepEqual(rows[0], { n: 1, m: 'Efectivo' });
});
test('una venta editada (mismo uuid) se actualiza sin duplicarse', { skip }, async () => {
  const uuid = '33333333-3333-4333-8333-333333333333';
  await call('/api/tpv/orders/sync', { method: 'POST', body: { orders: [venta({ localId: 20, uuid })] } });
  const editada = venta({ localId: 20, uuid, total: 4.8, metodoPago: 'TARJETA',
    items: [{ nombre: 'Manzanas', cantidad: 2, precio_unitario: 2.4, importe_linea: 4.8 }] });
  await call('/api/tpv/orders/sync', { method: 'POST', body: { orders: [editada] } });
  const { rows } = await owner.query("SELECT count(*)::int n, max(total) t, max(metodo_pago) m FROM ventas WHERE uuid=$1", [uuid]);
  assert.deepEqual(rows[0], { n: 1, t: 4.8, m: 'Tarjeta' });
  // SEG-API-02: la edición se acepta, pero deja rastro con el valor anterior (ventas_historial).
  const historial = (await owner.query('SELECT total_anterior::float t FROM ventas_historial WHERE uuid=$1', [uuid])).rows;
  assert.deepEqual(historial, [{ t: 2.4 }]);
});
test('venta antigua sin uuid no se duplica al reintentar', { skip }, async () => {
  const body = { orders: [venta({ localId: 9, fecha: 1_760_000_000_000 })] };
  await call('/api/tpv/orders/sync', { method: 'POST', body }); await call('/api/tpv/orders/sync', { method: 'POST', body });
  const { rows } = await owner.query("SELECT count(*)::int n FROM ventas WHERE id_local=9 AND tenant_id='fruteria'");
  assert.equal(rows[0].n, 1);
});
test('venta incoherente se rechaza y no entra en syncedIds', { skip }, async () => {
  const r = await (await call('/api/tpv/orders/sync', { method: 'POST', body: { orders: [venta({ localId: 11, total: 50 })] } })).json();
  assert.deepEqual(r.syncedIds, []); assert.equal(r.rejected[0].localId, 11);
});
test('cuerpo inválido: 400; JSON roto: 400; demasiado grande: 413', { skip }, async () => {
  assert.equal((await call('/api/tpv/orders/sync', { method: 'POST', body: { orders: 'x' } })).status, 400);
  const rota = await fetch(base + '/api/tpv/orders/sync', { method: 'POST', headers: { 'content-type': 'application/json', 'x-api-key': KEY_FRU }, body: '{rota' });
  assert.equal(rota.status, 400);
  const grande = await call('/api/tpv/orders/sync', { method: 'POST', body: { orders: [], relleno: 'a'.repeat(600_000) } });
  assert.equal(grande.status, 413);
});
test('aislamiento: cada clave solo ve y modifica su catálogo', { skip }, async () => {
  const fru = await (await call('/api/tpv/products')).json();
  const tal = await (await call('/api/tpv/products', { key: KEY_TAL })).json();
  assert.deepEqual(fru.products.map((p) => p.codigo), [101]);
  assert.deepEqual(fru.extras, []); assert.deepEqual(fru.salsas, []);
  assert.deepEqual(tal.products.map((p) => p.codigo), [201]);
  // Con la clave de API (transición) no se puede ajustar el stock: eso exige un usuario con rol encargado o admin.
  assert.equal((await call('/api/tpv/products/101/stock', { method: 'PUT', body: { stock: 12 } })).status, 403);
  assert.equal((await owner.query("SELECT stock FROM productos WHERE tenant_id='fruteria' AND codigo=101")).rows[0].stock, -1);
});
test('una venta de un negocio no es visible para otro (con la conexión de la API)', { skip }, async () => {
  const uuid = '22222222-2222-4222-8222-222222222222';
  const veces = (tenant) => withTenant(pool, tenant, async (db) =>
    (await db.query('SELECT count(*)::int n FROM ventas WHERE uuid=$1', [uuid])).rows[0].n);
  assert.equal(await veces('fruteria'), 1);
  assert.equal(await veces('taller'), 0);
});

test('un pedido con fecha absurda o texto nulo no bloquea el resto del lote', { skip }, async () => {
  const buena = venta({ localId: 31, uuid: '44444444-4444-4444-8444-444444444444' });
  const fechaAbsurda = venta({ localId: 32, fecha: 1e20 });
  const textoNulo = venta({ localId: 33, items: [{ nombre: 'a\u0000b', cantidad: 1, precio_unitario: 2.4, importe_linea: 2.4 }] });
  const r = await (await call('/api/tpv/orders/sync', { method: 'POST', body: { orders: [fechaAbsurda, textoNulo, buena] } })).json();
  assert.equal(r.success, true);
  assert.deepEqual(r.syncedIds, [31]);
  assert.deepEqual(r.rejected.map((x) => x.localId).sort(), [32, 33]);
  assert.equal((await owner.query('SELECT count(*)::int n FROM ventas WHERE uuid=$1', [buena.uuid])).rows[0].n, 1);
});

test('guardarVentas aísla la fila que PostgreSQL rechaza (SAVEPOINT) y confirma el resto', { skip }, async () => {
  const base = { fecha: new Date(), total: 2.4, metodo: 'Efectivo', items: [] };
  const buena = { ...base, uuid: '55555555-5555-4555-8555-555555555555', localId: 41, index: 0 };
  const mala = { ...base, uuid: '66666666-6666-4666-8666-666666666666', localId: 42, index: 1, total: 1e9 }; // numeric(10,2)
  const r = await withTenant(pool, 'fruteria', (db) => guardarVentas(db, 'fruteria', [mala, buena]));
  assert.deepEqual(r.confirmadas.map((o) => o.localId), [41]);
  assert.deepEqual(r.rechazadas.map((x) => x.localId), [42]);
  assert.equal((await owner.query('SELECT count(*)::int n FROM ventas WHERE uuid = ANY($1)', [[buena.uuid, mala.uuid]])).rows[0].n, 1);
});
