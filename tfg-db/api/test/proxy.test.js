import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from '../src/app.js';
import { leerTrustProxy } from '../src/config.js';

// Pool falso: /health solo necesita query(). El limitador se evalúa antes que cualquier ruta.
const poolFalso = { query: async () => ({ rows: [{ '?column?': 1 }] }) };

async function conServidor(opciones, fn) {
  const server = createApp(poolFalso, opciones).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    return await fn(base);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

function peticionDesde(base, ipCliente) {
  return fetch(`${base}/health`, { headers: { 'X-Forwarded-For': ipCliente } });
}

test('con trustProxy por defecto, X-Forwarded-For identifica al cliente real detrás de nginx', async () => {
  await conServidor({ rateLimitMax: 1 }, async (base) => {
    const primera = await peticionDesde(base, '198.51.100.1');
    const segunda = await peticionDesde(base, '198.51.100.2');

    assert.equal(primera.status, 200);
    assert.equal(segunda.status, 200, 'clientes distintos tienen cubos de límite distintos');
  });
});

test('un mismo cliente que supera el límite recibe 429 aunque cambie la cabecera X-Forwarded-For', async () => {
  await conServidor({ rateLimitMax: 1 }, async (base) => {
    await peticionDesde(base, '198.51.100.1');
    const repetida = await peticionDesde(base, '198.51.100.1');

    assert.equal(repetida.status, 429);
  });
});

test('con trustProxy desactivado se ignora X-Forwarded-For y todas las peticiones cuentan como la misma conexión', async () => {
  await conServidor({ rateLimitMax: 1, trustProxy: false }, async (base) => {
    const primera = await peticionDesde(base, '198.51.100.1');
    const suplantada = await peticionDesde(base, '198.51.100.2');

    assert.equal(primera.status, 200);
    assert.equal(suplantada.status, 429, 'falsear la cabecera no sirve para evadir el límite');
  });
});

test('leerTrustProxy: por defecto 1 salto, false explícito o 0 desactivan la confianza, y valores inválidos fallan al arrancar', () => {
  assert.equal(leerTrustProxy({}), 1);
  assert.equal(leerTrustProxy({ TRUST_PROXY: '2' }), 2);
  assert.equal(leerTrustProxy({ TRUST_PROXY: '0' }), false);
  assert.equal(leerTrustProxy({ TRUST_PROXY: 'false' }), false);
  assert.equal(leerTrustProxy({ TRUST_PROXY: ' FALSE ' }), false);
  assert.throws(() => leerTrustProxy({ TRUST_PROXY: 'loopback' }), /TRUST_PROXY debe ser un entero/);
  assert.throws(() => leerTrustProxy({ TRUST_PROXY: '11' }), /TRUST_PROXY/);
});
