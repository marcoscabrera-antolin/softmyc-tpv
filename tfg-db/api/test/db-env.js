/**
 * Motivo para omitir los tests que necesitan PostgreSQL, o false si hay variables y deben ejecutarse.
 * Sin variables, en local se omiten (con aviso en el nombre del test). Con CI=true o REQUIRE_DB_TESTS=1 la ausencia
 * de variables es un error: así un CI no puede dar verde sin haber ejecutado ni un test de base de datos.
 */
export function motivoOmision(variables) {
  const faltan = variables.filter((nombre) => !process.env[nombre]);
  if (faltan.length === 0) return false;
  if (process.env.CI === 'true' || process.env.REQUIRE_DB_TESTS === '1') {
    throw new Error(`Tests de base de datos obligatorios (CI/REQUIRE_DB_TESTS) pero faltan: ${faltan.join(', ')}`);
  }
  return `define ${faltan.join(', ')}`;
}
