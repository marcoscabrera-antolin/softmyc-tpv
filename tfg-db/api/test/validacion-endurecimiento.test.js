// Unitarios (sin PostgreSQL): validación de ventas y lectura de configuración tras la auditoría del 2026-10-16.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { METODOS, parseOrder } from '../src/validate.js';
import { leerConfiguracion, leerEntero } from '../src/config.js';

const venta = (over = {}) => ({
  localId: 1, total: 2.4, metodoPago: 'Efectivo', fecha: Date.now() - 1000,
  items: [{ nombre: 'Manzanas', cantidad: 1, precio_unitario: 2.4, importe_linea: 2.4 }], ...over,
});

test('una venta válida se acepta y el método se normaliza', () => {
  const r = parseOrder(venta({ metodoPago: 'TARJETA' }), 'fruteria');
  assert.equal(r.ok, true);
  assert.equal(r.order.metodo, 'Tarjeta');
});

test('las claves heredadas del objeto no se aceptan como método de pago', () => {
  assert.equal(METODOS.get('constructor'), undefined);
  assert.equal(parseOrder(venta({ metodoPago: 'constructor' }), 'x').ok, false);
  assert.equal(parseOrder(venta({ metodoPago: '__proto__' }), 'x').ok, false);
  assert.equal(parseOrder(venta({ metodoPago: 'toString' }), 'x').ok, false);
});

test('una fecha que no forma una Date válida se rechaza sin lanzar excepción', () => {
  assert.deepEqual(parseOrder(venta({ fecha: 1e20 }), 'x'), { ok: false, reason: 'fecha fuera de rango' });
});

test('una fecha anterior al año 2000 o futura se rechaza', () => {
  assert.equal(parseOrder(venta({ fecha: 1_000 }), 'x').ok, false);
  assert.equal(parseOrder(venta({ fecha: Date.now() + 3 * 86_400_000 }), 'x').ok, false);
});

test('un localId por encima de Number.MAX_SAFE_INTEGER se rechaza', () => {
  assert.equal(parseOrder(venta({ localId: Number.MAX_SAFE_INTEGER + 2 }), 'x').ok, false);
});

test('un texto con U+0000 en los items se rechaza (jsonb no lo admite)', () => {
  const conNul = venta({ items: [{ nombre: 'a\u0000b', cantidad: 1, precio_unitario: 2.4, importe_linea: 2.4 }] });
  assert.deepEqual(parseOrder(conNul, 'x'), { ok: false, reason: 'texto no admitido en los items' });
  const claveConNul = venta({ items: [{ nombre: 'ok', cantidad: 1, precio_unitario: 2.4, importe_linea: 2.4, ['c\u0000']: 1 }] });
  assert.equal(parseOrder(claveConNul, 'x').ok, false);
});

test('unos items que superan el tamaño admitido por la base se rechazan', () => {
  const grandes = Array.from({ length: 200 }, () => ({
    nombre: 'x'.repeat(200), cantidad: 1, precio_unitario: 1, importe_linea: 1, relleno: 'y'.repeat(1500),
  }));
  assert.deepEqual(parseOrder(venta({ items: grandes, total: 200 }), 'x'), { ok: false, reason: 'items demasiado grandes' });
});

test('leerEntero usa el valor por defecto si la variable está ausente o vacía', () => {
  const opciones = { defecto: 30, min: 1, max: 60 };
  assert.equal(leerEntero({}, 'ACCESS_TTL_MIN', opciones), 30);
  assert.equal(leerEntero({ ACCESS_TTL_MIN: '' }, 'ACCESS_TTL_MIN', opciones), 30);
  assert.equal(leerEntero({ ACCESS_TTL_MIN: '   ' }, 'ACCESS_TTL_MIN', opciones), 30);
});

test('leerEntero acepta un entero dentro de rango (con espacios alrededor)', () => {
  assert.equal(leerEntero({ ACCESS_TTL_MIN: ' 15 ' }, 'ACCESS_TTL_MIN', { defecto: 30, min: 1, max: 60 }), 15);
});

test('leerEntero falla con mensaje claro si el valor no es un entero o está fuera de rango', () => {
  const opciones = { defecto: 30, min: 1, max: 60 };
  for (const malo of ['abc', '0', '-5', '1e3', '2.5', '61', 'NaN']) {
    assert.throws(() => leerEntero({ ACCESS_TTL_MIN: malo }, 'ACCESS_TTL_MIN', opciones), /ACCESS_TTL_MIN debe ser un entero entre 1 y 60/, malo);
  }
});

test('leerConfiguracion da valores seguros por defecto, con x-api-key desactivada', () => {
  assert.deepEqual(leerConfiguracion({}), { port: 3100, accessTtlMin: 30, refreshTtlDays: 7, allowApiKey: false, trustProxy: 1 });
  assert.equal(leerConfiguracion({ ALLOW_API_KEY_AUTH: 'true' }).allowApiKey, true);
  assert.throws(() => leerConfiguracion({ PORT: 'abc' }), /PORT debe ser un entero/);
  assert.throws(() => leerConfiguracion({ REFRESH_TTL_DAYS: '0' }), /REFRESH_TTL_DAYS/);
});
