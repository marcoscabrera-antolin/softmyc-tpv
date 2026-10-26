const MIN = 12, MAX = 200;
// Palabras y secuencias que no pueden formar parte de la contraseña (se comparan sin signos ni mayúsculas).
const COMUNES = ['password', 'contrasena', 'contraseña', 'qwerty', 'abc123', 'letmein', 'welcome', 'admin', 'iloveyou',
  '123456', '111111', '000000', 'monkey', 'dragon', 'football', 'master', 'login', 'softmyc'];

/** Política de contraseñas (prima la longitud sobre la composición). Devuelve null si es válida o el motivo del rechazo. */
export function validatePassword(password, username = '') {
  if (typeof password !== 'string' || password.length < MIN) return `La contraseña debe tener al menos ${MIN} caracteres`;
  if (password.length > MAX) return `La contraseña debe tener como máximo ${MAX} caracteres`;
  const u = String(username).trim().toLowerCase();
  if (u.length >= 3 && password.toLowerCase().includes(u)) return 'La contraseña no debe contener el nombre de usuario';
  if (new Set(password).size <= 3) return 'La contraseña no puede ser una repetición de pocos caracteres';
  if (/^\d+$/.test(password)) return 'La contraseña es una secuencia de dígitos demasiado simple';
  const plano = password.toLowerCase().replace(/[^a-z0-9ñ]/g, '');
  if (COMUNES.some((w) => plano.includes(w))) return 'La contraseña contiene una palabra o secuencia demasiado común';
  return null;
}
