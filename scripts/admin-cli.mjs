#!/usr/bin/env node
// Herramienta de línea de comandos para crear y usar la cuenta ADMIN de DEV (SCRUM-23).
//
// Hace por API todo el flujo que describe docs/datos/crear-administrador-en-dev.md:
// registro -> login (401 TOTP_ENROLLMENT_REQUIRED) -> enroll -> confirm, y guarda las
// credenciales en un archivo local para poder entrar después SIN celular.
//
// Node >= 20, sin dependencias. El TOTP es RFC 6238 con node:crypto.
// Nunca imprime la contraseña, el secreto TOTP ni los códigos de respaldo.

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_FILE = path.join(REPO_ROOT, '.admin.local.json');
const DEFAULT_API = 'http://localhost:8080';

const HELP = `Uso: node scripts/admin-cli.mjs <subcomando> [opciones]

Subcomandos
  crear   --email <e> --name <n>   Registra la cuenta (o sigue si ya existe), enrola el
                                   segundo factor TOTP y guarda las credenciales en el
                                   archivo local. No sube el rol: eso es un UPDATE aparte
                                   (ver docs/datos/crear-administrador-en-dev.md, paso 3).
  codigo                           Imprime el código TOTP vigente de 6 dígitos, para entrar
                                   a mano por la pantalla de login sin celular.
  token                            Login completo con TOTP; imprime solo el JWT por stdout
                                   (para curl). Hay que volver a pedirlo tras subir el rol.
  probar-totp                      Verifica el generador contra los vectores del RFC 6238.

Opciones
  --file <ruta>      Archivo de credenciales. Por defecto .admin.local.json en la raíz del
                     repo. Usa otro archivo (p. ej. .usuario.local.json) para una cuenta USER.
  --api <url>        URL del backend. Por defecto ${DEFAULT_API} (o la variable API_URL).
  --help             Muestra esta ayuda.

Variables de entorno
  ADMIN_PASSWORD     Contraseña a usar en 'crear'. Si falta, el script genera una de 24
                     caracteres con mayúscula y número.

Seguridad
  El archivo de credenciales está en .gitignore (.admin.local.json y *.local.json). Contiene
  la contraseña, el secreto TOTP y los 10 códigos de respaldo: se pasa por un canal privado,
  nunca por el repositorio, el chat de grupo ni una captura de pantalla.
`;

// ---------------------------------------------------------------- TOTP (RFC 6238)

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function base32Decode(input) {
  const clean = input.replace(/=+$/, '').replace(/\s+/g, '').toUpperCase();
  let bits = 0;
  let value = 0;
  const out = [];
  for (const ch of clean) {
    const idx = BASE32.indexOf(ch);
    if (idx === -1) throw new Error('El secreto TOTP no es base32 válido');
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

/** HOTP/TOTP con HMAC-SHA1 (RFC 4226 / 6238). `timeMs` en milisegundos. */
function totp(secretBase32, timeMs = Date.now(), digits = 6, period = 30, algorithm = 'sha1') {
  const counter = BigInt(Math.floor(timeMs / 1000 / period));
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64BE(counter);
  const hmac = crypto.createHmac(algorithm, base32Decode(secretBase32)).update(buf).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const binary =
    ((hmac[offset] & 0x7f) << 24) |
    (hmac[offset + 1] << 16) |
    (hmac[offset + 2] << 8) |
    hmac[offset + 3];
  return String(binary % 10 ** digits).padStart(digits, '0');
}

/** Vectores de prueba del RFC 6238 (apéndice B), variante SHA1, 8 dígitos. */
function selfTestTotp() {
  const secret = base32Encode(Buffer.from('12345678901234567890', 'ascii'));
  const vectors = [
    [59, '94287082'],
    [1111111109, '07081804'],
    [1111111111, '14050471'],
    [1234567890, '89005924'],
    [2000000000, '69279037'],
    [20000000000, '65353130'],
  ];
  const failed = vectors.filter(([t, expected]) => totp(secret, t * 1000, 8) !== expected);
  if (failed.length > 0) {
    throw new Error(`El generador TOTP NO pasa el vector del RFC 6238 (fallan ${failed.length}/${vectors.length})`);
  }
  return vectors.length;
}

function base32Encode(buf) {
  let bits = 0;
  let value = 0;
  let out = '';
  for (const byte of buf) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      out += BASE32[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += BASE32[(value << (5 - bits)) & 31];
  return out;
}

// ---------------------------------------------------------------- utilidades

function fail(message) {
  console.error(`Error: ${message}`);
  process.exit(1);
}

function parseArgs(argv) {
  const opts = {};
  const positional = [];
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--help' || arg === '-h') opts.help = true;
    else if (arg.startsWith('--')) {
      const value = argv[i + 1];
      if (value === undefined || value.startsWith('--')) fail(`Falta el valor de ${arg}`);
      opts[arg.slice(2)] = value;
      i++;
    } else positional.push(arg);
  }
  return { command: positional[0], opts };
}

function generatePassword(length = 24) {
  const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const lower = 'abcdefghijkmnopqrstuvwxyz';
  const digits = '23456789';
  const all = upper + lower + digits;
  const pick = (set) => set[crypto.randomInt(set.length)];
  // Garantiza mayúscula y número (política RF-01) y rellena el resto al azar.
  const chars = [pick(upper), pick(digits)];
  while (chars.length < length) chars.push(pick(all));
  for (let i = chars.length - 1; i > 0; i--) {
    const j = crypto.randomInt(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}

function readCredentials(file) {
  if (!fs.existsSync(file)) return null;
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    fail(`El archivo ${file} no es JSON válido`);
  }
}

function writeCredentials(file, data) {
  fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n', { mode: 0o600 });
}

async function call(api, method, route, { body, token } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(api + route, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    fail(`No se pudo conectar con ${api} (${e.cause?.code ?? e.message}). ¿Está levantado el backend?`);
  }
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* cuerpo no JSON */
  }
  return { status: res.status, json };
}

/** Resumen de un error HTTP sin volcar tokens ni secretos. */
function describe(res) {
  const d = res.json?.detail ?? res.json?.error ?? '';
  const extra = res.json?.errors ? ` ${JSON.stringify(res.json.errors)}` : '';
  return `HTTP ${res.status} ${d}${extra}`.trim();
}

// ---------------------------------------------------------------- subcomandos

async function crear(opts, file, api) {
  if (!opts.email) fail('Falta --email');
  const existing = readCredentials(file);
  if (existing && existing.email !== opts.email) {
    fail(`${file} ya guarda otra cuenta; usa --file para una nueva o bórralo a mano.`);
  }
  if (existing?.totpSecret) {
    console.log(`La cuenta ya está enrolada; credenciales en ${file}. Nada que hacer.`);
    return;
  }
  const name = opts.name ?? existing?.name;
  if (!name) fail('Falta --name');

  const password = existing?.password ?? process.env.ADMIN_PASSWORD ?? generatePassword();
  // Se guarda ANTES de registrar: si el flujo se corta a medias, la contraseña de una
  // cuenta que ya existe en la base no se pierde.
  writeCredentials(file, { ...existing, email: opts.email, name, password });

  const reg = await call(api, 'POST', '/api/auth/register', {
    body: { name, email: opts.email, password },
  });
  if (reg.status === 201) console.log(`Cuenta registrada (id ${reg.json?.id}).`);
  else if (reg.status === 409) console.log('La cuenta ya existía; sigo al login.');
  else fail(`El registro falló: ${describe(reg)}`);

  const login = await call(api, 'POST', '/api/auth/login', { body: { email: opts.email, password } });
  if (login.status !== 401 || login.json?.error !== 'TOTP_ENROLLMENT_REQUIRED') {
    fail(`Se esperaba 401 TOTP_ENROLLMENT_REQUIRED y llegó: ${describe(login)}. ` +
      'Si la cuenta ya tiene TOTP activo, sus credenciales no están en este archivo.');
  }
  const enrollmentToken = login.json.enrollmentToken;

  const enroll = await call(api, 'POST', '/api/users/me/totp/enroll', { token: enrollmentToken });
  if (enroll.status !== 200 || !enroll.json?.secret) fail(`El enrolamiento falló: ${describe(enroll)}`);
  const secret = enroll.json.secret;

  const confirm = await call(api, 'POST', '/api/users/me/totp/confirm', {
    token: enrollmentToken,
    body: { code: totp(secret) },
  });
  if (confirm.status !== 200) fail(`La confirmación TOTP falló: ${describe(confirm)}`);

  writeCredentials(file, {
    email: opts.email,
    name,
    password,
    totpSecret: secret,
    recoveryCodes: confirm.json.recoveryCodes,
    createdAt: new Date().toISOString(),
    api,
  });
  console.log(`TOTP activado. Credenciales guardadas en ${file}`);
  console.log('Si esta cuenta debe ser ADMIN, falta subir el rol con el UPDATE de SQL y volver a pedir el token.');
}

function requireCredentials(file) {
  const creds = readCredentials(file);
  if (!creds?.email || !creds.totpSecret) {
    fail(`No hay credenciales completas en ${file}. Corre 'crear' primero.`);
  }
  return creds;
}

function codigo(file) {
  const creds = requireCredentials(file);
  const remaining = 30 - (Math.floor(Date.now() / 1000) % 30);
  console.error(`(vence en ${remaining} s)`);
  console.log(totp(creds.totpSecret));
}

async function token(file, api) {
  const creds = requireCredentials(file);
  const res = await call(api, 'POST', '/api/auth/login', {
    body: { email: creds.email, password: creds.password, totpCode: totp(creds.totpSecret) },
  });
  if (res.status !== 200 || !res.json?.token) fail(`El login falló: ${describe(res)}`);
  process.stdout.write(res.json.token + '\n');
}

// ---------------------------------------------------------------- main

const { command, opts } = parseArgs(process.argv.slice(2));
if (opts.help || !command) {
  console.log(HELP);
  process.exit(command || opts.help ? 0 : 1);
}

const file = path.resolve(opts.file ?? DEFAULT_FILE);
const api = (opts.api ?? process.env.API_URL ?? DEFAULT_API).replace(/\/$/, '');

try {
  // El generador se verifica contra el RFC antes de usarlo para algo real.
  const checked = selfTestTotp();
  if (command === 'probar-totp') {
    console.log(`Generador TOTP correcto: ${checked}/${checked} vectores del RFC 6238.`);
  } else if (command === 'crear') await crear(opts, file, api);
  else if (command === 'codigo') codigo(file);
  else if (command === 'token') await token(file, api);
  else fail(`Subcomando desconocido: ${command}. Usa --help.`);
} catch (e) {
  fail(e.message);
}
