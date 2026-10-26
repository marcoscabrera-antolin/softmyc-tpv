#!/usr/bin/env node
// Gestión de cuentas desde el servidor, sin pasar por la API. La contraseña se lee por la entrada estándar (nunca como
// argumento, para que no quede en el historial del shell ni en la lista de procesos). Se conecta con AUTH_DATABASE_URL.
//
//   docker exec -it tfg-api node scripts/usuarios.js crear --username ana --nombre "Ana Pérez" --negocio fruteria:encargado
//   docker exec -it tfg-api node scripts/usuarios.js crear --username admin --nombre "Administrador" --admin
//   docker exec -it tfg-api node scripts/usuarios.js listar | password|desbloquear|desactivar|activar --username ana
import pg from 'pg';
import { hashPassword } from '../src/password.js';
import { validatePassword } from '../src/passwordPolicy.js';
import { normalizeTenant } from '../src/auth.js';

const ROLES = ['admin', 'encargado', 'tpv_operator'];
const USO = `Uso: usuarios.js <crear|listar|password|desbloquear|desactivar|activar> [--username u] [--nombre n] [--admin] [--negocio negocio:rol ...]`;

function parseArgs(argv) {
  const o = { negocios: [], admin: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--admin') o.admin = true;
    else if (a === '--username') o.username = argv[++i];
    else if (a === '--nombre') o.nombre = argv[++i];
    else if (a === '--negocio') o.negocios.push(argv[++i]);
    else throw new Error(`Opción desconocida: ${a}`);
  }
  return o;
}

async function readPassword(prompt) {
  if (!process.stdin.isTTY) {
    const chunks = []; for await (const c of process.stdin) chunks.push(c);
    return Buffer.concat(chunks).toString('utf8').split(/\r?\n/)[0];
  }
  process.stderr.write(prompt);
  process.stdin.setRawMode(true); process.stdin.resume(); process.stdin.setEncoding('utf8');
  return new Promise((resolve) => {
    let pw = '';
    const onData = (ch) => {
      for (const c of ch) {
        if (c === '\r' || c === '\n') { process.stdin.setRawMode(false); process.stdin.pause(); process.stdin.off('data', onData); process.stderr.write('\n'); return resolve(pw); }
        if (c === '\u0003') { process.stdin.setRawMode(false); process.stderr.write('\n'); process.exit(130); }
        if (c === '\u007f') pw = pw.slice(0, -1); else pw += c;
      }
    };
    process.stdin.on('data', onData);
  });
}

async function askNewPassword(username) {
  const p1 = await readPassword('Contraseña: ');
  if (process.stdin.isTTY && (await readPassword('Repite la contraseña: ')) !== p1) throw new Error('Las contraseñas no coinciden');
  const motivo = validatePassword(p1, username);
  if (motivo) throw new Error(motivo);
  return p1;
}

export async function main(argv, env = process.env) {
  const [cmd, ...rest] = argv;
  if (!['crear', 'listar', 'password', 'desbloquear', 'desactivar', 'activar'].includes(cmd)) { console.error(USO); return 2; }
  if (!env.AUTH_DATABASE_URL) { console.error('Falta AUTH_DATABASE_URL'); return 2; }
  let opts; try { opts = parseArgs(rest); } catch (e) { console.error(e.message, '\n' + USO); return 2; }
  const db = new pg.Client({ connectionString: env.AUTH_DATABASE_URL });
  try {
    await db.connect();
  } catch {
    console.error('No se pudo conectar con la base de datos (revisa AUTH_DATABASE_URL).');
    return 1;
  }
  try {
    const users = (await db.query('SELECT * FROM auth_admin_listar_usuarios()')).rows;
    if (cmd === 'listar') {
      for (const u of users) console.log(`${String(u.id).padEnd(4)}${u.username.padEnd(22)}${u.activo ? 'activo  ' : 'inactivo'} ${u.es_admin ? 'ADMIN ' : '      '}${u.bloqueado_hasta && new Date(u.bloqueado_hasta) > new Date() ? 'BLOQUEADO ' : ''}${u.negocios.map((n) => `${n.tenantId}:${n.rol}`).join(',')}`);
      return 0;
    }
    const username = (opts.username ?? '').trim().toLowerCase();
    if (!username) { console.error('Falta --username'); return 2; }
    const user = users.find((u) => u.username === username);
    if (cmd === 'crear') {
      if (!opts.nombre) { console.error('Falta --nombre'); return 2; }
      const negocios = opts.negocios.map((n) => { const [t, r] = String(n).split(':'); return { tenantId: normalizeTenant(t), rol: r }; });
      if (negocios.some((n) => !n.tenantId || !ROLES.includes(n.rol))) throw new Error('--negocio debe ser negocio:rol (admin, encargado o tpv_operator)');
      if (!opts.admin && negocios.length === 0) throw new Error('Un usuario que no es administrador necesita al menos un --negocio');
      const hash = await hashPassword(await askNewPassword(username));
      const { rows: [{ id }] } = await db.query('SELECT auth_admin_crear_usuario(NULL,$1,$2,$3,$4,$5::jsonb) AS id', [username, opts.nombre, hash, opts.admin, JSON.stringify(negocios)]);
      console.log(`Usuario ${username} creado (id ${id}).`);
      return 0;
    }
    if (!user) { console.error(`No existe el usuario ${username}`); return 1; }
    if (cmd === 'password') {
      await db.query('SELECT auth_admin_cambiar_password(NULL,$1,$2)', [user.id, await hashPassword(await askNewPassword(username))]);
      console.log('Contraseña cambiada; sesiones cerradas y cuenta desbloqueada.');
    } else if (cmd === 'desbloquear') {
      await db.query('SELECT auth_admin_desbloquear(NULL,$1)', [user.id]); console.log('Cuenta desbloqueada.');
    } else {
      await db.query('SELECT auth_admin_actualizar_usuario(NULL,$1,NULL,$2,NULL,NULL)', [user.id, cmd === 'activar']);
      console.log(cmd === 'activar' ? 'Usuario activado.' : 'Usuario desactivado y sesiones cerradas.');
    }
    return 0;
  } catch (err) {
    console.error(err.code === '23505' ? 'Ese usuario ya existe.' : err.message);
    return 1;
  } finally { await db.end(); }
}

process.exitCode = await main(process.argv.slice(2));
