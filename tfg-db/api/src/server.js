import { createApp } from './app.js';
import { createPool } from './db.js';
import { leerConfiguracion, avisosDeArranque } from './config.js';

const url = process.env.DATABASE_URL;           // rol tfg_app: datos de negocio y validación del token
const authUrl = process.env.AUTH_DATABASE_URL;  // rol tfg_auth: login, refresco, sesiones (única conexión que puede ejecutarlos)
if (!url || !authUrl) { console.error('Faltan DATABASE_URL y AUTH_DATABASE_URL'); process.exit(1); }

let config;
try {
  config = leerConfiguracion(process.env);
} catch (err) {
  console.error(`Configuración no válida: ${err.message}`);
  process.exit(1);
}

for (const msg of avisosDeArranque(config)) console.warn(JSON.stringify({ level: 'warn', msg }));

const pool = createPool(url);
const authPool = createPool(authUrl);
const server = createApp(pool, {
  authPool,
  allowApiKey: config.allowApiKey,
  trustProxy: config.trustProxy,
  accessTtlMin: config.accessTtlMin,
  refreshTtlDays: config.refreshTtlDays,
}).listen(config.port, '0.0.0.0', () => console.log(JSON.stringify({ level: 'info', msg: 'tfg-api escuchando', port: config.port })));

function shutdown() { server.close(() => Promise.all([pool.end(), authPool.end()]).finally(() => process.exit(0))); setTimeout(() => process.exit(1), 10_000).unref(); }
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
