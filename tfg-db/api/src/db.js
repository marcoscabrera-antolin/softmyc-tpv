import pg from 'pg';

pg.types.setTypeParser(1700, (v) => Number(v)); // numeric -> number (importes con 2 decimales)

export function createPool(connectionString) {
  const pool = new pg.Pool({ connectionString, max: 8, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000 });
  // Sin este listener, un reinicio de PostgreSQL emite 'error' en un cliente inactivo y tumba el proceso.
  pool.on('error', (err) => {
    console.error('Error en un cliente inactivo del pool de PostgreSQL:', err.message);
  });
  return pool;
}

/** Ejecuta fn dentro de una transacción con app.tenant fijado: la RLS de PostgreSQL aísla los datos del negocio. */
export async function withTenant(pool, tenantId, fn) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query("SELECT set_config('app.tenant', $1, true)", [tenantId]);
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    throw err;
  } finally {
    client.release();
  }
}
