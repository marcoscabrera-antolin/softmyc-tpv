/**
 * Clave del limitador por IP. Las direcciones IPv6 se agrupan por /64 (un atacante con un prefijo propio
 * tiene miles de millones de direcciones) y las IPv4 mapeadas en IPv6 se tratan como IPv4.
 */
export function clientKey(req) {
  const ip = req.ip;
  if (!ip) return 'desconocida';
  const mapped = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i.exec(ip);
  if (mapped) return mapped[1];
  if (!ip.includes(':')) return ip;
  const halves = ip.split('::');
  const head = halves[0] ? halves[0].split(':') : [];
  const tail = halves.length > 1 && halves[1] ? halves[1].split(':') : [];
  const groups = halves.length > 1 ? [...head, ...Array(Math.max(0, 8 - head.length - tail.length)).fill('0'), ...tail] : head;
  return `${groups.slice(0, 4).map((g) => g.toLowerCase().replace(/^0+(?=.)/, '')).join(':')}::/64`;
}
