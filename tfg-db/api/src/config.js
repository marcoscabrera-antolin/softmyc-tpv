/**
 * Lectura de la configuración desde variables de entorno. Una variable ausente o vacía usa su valor por defecto;
 * una variable no numérica o fuera de rango lanza un error al arrancar (en vez de producir NaN o 0 en tiempo de ejecución).
 */
export function leerEntero(env, nombre, { defecto, min, max }) {
  const crudo = env[nombre];
  if (crudo === undefined || crudo.trim() === '') return defecto;
  const texto = crudo.trim();
  const valor = /^\d+$/.test(texto) ? Number(texto) : NaN;
  if (!Number.isSafeInteger(valor) || valor < min || valor > max) {
    throw new Error(`${nombre} debe ser un entero entre ${min} y ${max} (valor recibido: "${crudo}")`);
  }
  return valor;
}

/**
 * Número de saltos de proxy de confianza delante de la API (por defecto 1: el nginx del despliegue). Con 0 o
 * TRUST_PROXY=false no se confía en X-Forwarded-For y req.ip es siempre la conexión directa.
 */
export function leerTrustProxy(env) {
  if (env.TRUST_PROXY !== undefined && env.TRUST_PROXY.trim().toLowerCase() === 'false') return false;
  const saltos = leerEntero(env, 'TRUST_PROXY', { defecto: 1, min: 0, max: 10 });
  return saltos === 0 ? false : saltos;
}

/**
 * ALLOW_API_KEY_AUTH controla la clave heredada x-api-key. Ausente o vacía = false (desactivada por defecto, SEG-API-04):
 * para mantenerla hay que definir ALLOW_API_KEY_AUTH=true de forma explícita.
 * Cualquier otro valor distinto de "true" o "false" falla al arrancar: no se acepta "0" ni "no" como si fueran "false".
 */
export function leerAllowApiKey(env) {
  const crudo = env.ALLOW_API_KEY_AUTH;
  if (crudo === undefined || crudo.trim() === '') return false;
  const texto = crudo.trim().toLowerCase();
  if (texto === 'true' || texto === 'false') return texto === 'true';
  throw new Error(`ALLOW_API_KEY_AUTH debe ser true o false (valor recibido: "${crudo}")`);
}

/** Avisos que el servidor escribe en el log al arrancar. */
export function avisosDeArranque(config) {
  const avisos = [];
  if (config.allowApiKey) {
    avisos.push('x-api-key activa (ALLOW_API_KEY_AUTH=true): la clave no caduca y da acceso de operador a su negocio. ' +
      'Para desactivarla, define ALLOW_API_KEY_AUTH=false (ver README, "API (`api/`)").');
  }
  return avisos;
}

export function leerConfiguracion(env = process.env) {
  return {
    port: leerEntero(env, 'PORT', { defecto: 3100, min: 1, max: 65535 }),
    accessTtlMin: leerEntero(env, 'ACCESS_TTL_MIN', { defecto: 30, min: 1, max: 60 }),
    refreshTtlDays: leerEntero(env, 'REFRESH_TTL_DAYS', { defecto: 7, min: 1, max: 30 }),
    allowApiKey: leerAllowApiKey(env),   // por defecto false (SEG-API-04): la clave heredada hay que activarla de forma explícita
    trustProxy: leerTrustProxy(env),
  };
}
