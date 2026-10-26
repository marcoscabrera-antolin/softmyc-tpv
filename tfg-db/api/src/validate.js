import { createHash } from 'node:crypto';
import { z } from 'zod';

const MAX_ITEMS_PER_ORDER = 200;
const MAX_ORDERS_PER_BATCH = 100;
const TOLERANCIA_EUR = 0.011;
// Límites de la base: items < 256 KiB (CHECK de ventas) y fechas con Date válida, no demasiado antiguas ni futuras.
const MAX_BYTES_ITEMS = 200_000;
const FECHA_MINIMA_MS = Date.UTC(2000, 0, 1);
const MARGEN_FUTURO_MS = 86_400_000;

/** Métodos de pago admitidos. Un Map evita que claves heredadas (constructor, __proto__) se traten como métodos. */
export const METODOS = new Map([['efectivo', 'Efectivo'], ['tarjeta', 'Tarjeta']]);

/** true si algún texto (valor o clave) contiene U+0000: PostgreSQL no admite ese carácter en jsonb. */
function contieneNul(valor) {
  if (typeof valor === 'string') return valor.includes('\u0000');
  if (Array.isArray(valor)) return valor.some(contieneNul);
  if (valor && typeof valor === 'object') {
    return Object.entries(valor).some(([clave, v]) => clave.includes('\u0000') || contieneNul(v));
  }
  return false;
}

/** UUID determinista para ventas antiguas sin uuid: mismo (negocio, localId, fecha) => mismo uuid. */
export function legacyUuid(tenantId, localId, fechaMs) {
  const h = createHash('sha1').update(`tfg-softmyc|${tenantId}|${localId}|${fechaMs}`).digest();
  h[6] = (h[6] & 0x0f) | 0x50;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.subarray(0, 16).toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20, 32)}`;
}

const itemSchema = z.object({
  nombre: z.string().min(1).max(200),
  cantidad: z.number().positive().max(100000),
  precio_unitario: z.number().min(0).max(1_000_000),
  importe_linea: z.number().min(0).max(10_000_000).optional(),
}).passthrough();

const orderSchema = z.object({
  localId: z.number().int().positive().max(Number.MAX_SAFE_INTEGER).optional(),
  uuid: z.string().uuid().optional(),
  total: z.number().min(0).max(10_000_000),
  metodoPago: z.string().max(20).optional(),
  fecha: z.number().int().positive(),
  items: z.array(itemSchema).max(MAX_ITEMS_PER_ORDER),
}).passthrough();

export const syncBodySchema = z.object({
  restaurantId: z.string().max(60).optional(),
  orders: z.array(z.unknown()).max(MAX_ORDERS_PER_BATCH),
});

const round2 = (n) => Math.round(n * 100) / 100;

/** Valida y normaliza una venta. Devuelve {ok, order} o {ok:false, reason}. */
export function parseOrder(raw, tenantId) {
  const parsed = orderSchema.safeParse(raw);
  if (!parsed.success) return { ok: false, reason: 'formato inválido' };
  const o = parsed.data;
  const metodo = METODOS.get((o.metodoPago ?? 'efectivo').trim().toLowerCase());
  if (!metodo) return { ok: false, reason: 'método de pago no válido' };
  const fecha = new Date(o.fecha);
  if (Number.isNaN(fecha.getTime()) || fecha.getTime() < FECHA_MINIMA_MS) return { ok: false, reason: 'fecha fuera de rango' };
  if (fecha.getTime() > Date.now() + MARGEN_FUTURO_MS) return { ok: false, reason: 'fecha futura' };
  if (contieneNul(o.items)) return { ok: false, reason: 'texto no admitido en los items' };
  if (Buffer.byteLength(JSON.stringify(o.items), 'utf8') > MAX_BYTES_ITEMS) return { ok: false, reason: 'items demasiado grandes' };
  if (o.items.every((i) => i.importe_linea !== undefined)) {
    const suma = round2(o.items.reduce((acc, i) => acc + i.importe_linea, 0));
    if (Math.abs(suma - o.total) > TOLERANCIA_EUR) return { ok: false, reason: 'el total no coincide con las líneas' };
  }
  const uuid = o.uuid ?? legacyUuid(tenantId, o.localId ?? 0, o.fecha);
  return { ok: true, order: { uuid, localId: o.localId ?? null, fecha, total: round2(o.total), metodo, items: o.items } };
}

export const stockBodySchema = z.object({ stock: z.number().int().min(-1).max(1_000_000) });
export const codigoSchema = z.coerce.number().int().positive();

/** Alta o edición de un producto del catálogo (la tablet envía codigo, nombre, precio, categoria e ingredientes). */
export const productoBodySchema = z.object({
  codigo: z.number().int().positive().max(2_000_000_000),
  nombre: z.string().trim().min(1).max(200),
  precio: z.number().min(0).max(1_000_000),
  categoria: z.string().trim().max(100).default(''),
  ingredientes: z.array(z.string().trim().min(1).max(200)).max(100).default([]),
  unidadMedida: z.string().trim().min(1).max(10).optional(),
  stock: z.number().int().min(-1).max(1_000_000).optional(),
});
