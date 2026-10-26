import { test } from 'node:test';
import assert from 'node:assert/strict';
import { leerAllowApiKey, leerConfiguracion, avisosDeArranque } from '../src/config.js';

test('ALLOW_API_KEY_AUTH sin definir o vacía deja x-api-key desactivada (valor por defecto)', () => {
  assert.equal(leerAllowApiKey({}), false);
  assert.equal(leerAllowApiKey({ ALLOW_API_KEY_AUTH: '' }), false);
  assert.equal(leerConfiguracion({}).allowApiKey, false);
});

test('ALLOW_API_KEY_AUTH=false desactiva x-api-key, sin importar mayúsculas ni espacios', () => {
  assert.equal(leerAllowApiKey({ ALLOW_API_KEY_AUTH: 'false' }), false);
  assert.equal(leerAllowApiKey({ ALLOW_API_KEY_AUTH: ' FALSE ' }), false);
  assert.equal(leerConfiguracion({ ALLOW_API_KEY_AUTH: 'false' }).allowApiKey, false);
  assert.equal(leerConfiguracion({ ALLOW_API_KEY_AUTH: ' TRUE ' }).allowApiKey, true);
});

test('ALLOW_API_KEY_AUTH con un valor que no es true ni false falla al arrancar en vez de quedar activa en silencio', () => {
  assert.throws(() => leerAllowApiKey({ ALLOW_API_KEY_AUTH: '0' }), /ALLOW_API_KEY_AUTH debe ser true o false/);
  assert.throws(() => leerAllowApiKey({ ALLOW_API_KEY_AUTH: 'no' }), /ALLOW_API_KEY_AUTH/);
});

test('con x-api-key activa el arranque avisa de que la clave no caduca', () => {
  const avisos = avisosDeArranque({ allowApiKey: true });

  assert.equal(avisos.length, 1);
  assert.match(avisos[0], /x-api-key activa/);
  assert.match(avisos[0], /ALLOW_API_KEY_AUTH=false/);
});

test('con x-api-key desactivada el arranque no emite avisos', () => {
  assert.deepEqual(avisosDeArranque({ allowApiKey: false }), []);
});
