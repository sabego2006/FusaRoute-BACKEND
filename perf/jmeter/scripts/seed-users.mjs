// Crea las cuentas de prueba que usan los planes de JMeter y deja listos los
// CSV de datos. Sin dependencias: solo Node 22 (fetch y crypto vienen incluidos).
//
//   node perf/jmeter/scripts/seed-users.mjs --base http://localhost:8080 --count 300
//
// Por cada cuenta hace el camino real de la API: registro, login (que responde
// TOTP_ENROLLMENT_REQUIRED), enrolamiento del segundo factor y confirmacion. Con
// eso obtiene el secreto TOTP, que JMeter necesita para calcular el codigo de 6
// digitos en cada login.
//
// Salida (carpeta perf/jmeter/data, ignorada por git):
//   users.csv  email,password,totpSecret,token
//   pairs.csv  originPlaceId,destinationPlaceId   (pares con ruta, para /search)
//   routes.csv routeId
//
// Las contrasenas son aleatorias y distintas en cada corrida. Estas cuentas solo
// existen en la base donde se ejecute el script: usarlo UNICAMENTE contra el
// compose local o un ambiente de pruebas, nunca contra PROD.

import { createHmac, randomBytes } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc), []),
);
const BASE = (args.base ?? 'http://localhost:8080').replace(/\/+$/, '');
const COUNT = Number(args.count ?? 300);
const CONCURRENCY = Number(args.concurrency ?? 4);
const PAIRS = Number(args.pairs ?? 40);
const OUT = args.out ?? join(dirname(fileURLToPath(import.meta.url)), '..', 'data');
const RUN = args.run ?? Date.now().toString(36);

if (/onrender\.com|vercel\.app|supabase/.test(BASE)) {
  console.error('Este script crea cuentas: no se ejecuta contra PROD ni contra Supabase.');
  process.exit(1);
}

/** Codigo TOTP estandar (RFC 6238): SHA1, 6 digitos, periodo de 30 s. */
export function totp(secret, now = Date.now()) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of secret.replace(/[\s=]/g, '').toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, '0');
  const key = Buffer.from((bits.match(/.{8}/g) ?? []).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(now / 30000)));
  const h = createHmac('sha1', key).update(counter).digest();
  const o = h[h.length - 1] & 15;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

async function call(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* cuerpo no JSON */ }
  return { status: res.status, json, text };
}

async function createUser(i) {
  const email = `carga.${RUN}.${String(i).padStart(4, '0')}@example.com`;
  const password = `Aa1${randomBytes(9).toString('hex')}`;
  const reg = await call('POST', '/api/auth/register', { name: `Carga ${i}`, email, password });
  if (reg.status !== 201) throw new Error(`registro ${email}: HTTP ${reg.status} ${reg.text.slice(0, 120)}`);
  const login = await call('POST', '/api/auth/login', { email, password });
  if (login.json?.error !== 'TOTP_ENROLLMENT_REQUIRED') throw new Error(`login ${email}: HTTP ${login.status}`);
  const enroll = await call('POST', '/api/users/me/totp/enroll', null, login.json.enrollmentToken);
  if (enroll.status !== 200) throw new Error(`enroll ${email}: HTTP ${enroll.status}`);
  // Hasta 3 intentos: si el codigo se calcula justo al cambiar la ventana de 30 s
  // y el servidor va cargado, puede llegar vencido. Se espera y se recalcula.
  let confirm;
  for (let attempt = 0; attempt < 3; attempt++) {
    confirm = await call('POST', '/api/users/me/totp/confirm', { code: totp(enroll.json.secret) }, login.json.enrollmentToken);
    if (confirm.status === 200) break;
    await new Promise((r) => setTimeout(r, 2000));
  }
  if (confirm.status !== 200 || !confirm.json?.token) throw new Error(`confirm ${email}: HTTP ${confirm.status} ${confirm.text.slice(0, 120)}`);
  return { email, password, secret: enroll.json.secret, token: confirm.json.token };
}

async function main() {
  mkdirSync(OUT, { recursive: true });
  const started = Date.now();
  const users = new Array(COUNT);
  let next = 0;
  await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
    while (next < COUNT) {
      const i = next++;
      users[i] = await createUser(i + 1);
      if ((i + 1) % 50 === 0) console.log(`  ${i + 1}/${COUNT} cuentas`);
    }
  }));
  writeFileSync(join(OUT, 'users.csv'), users.map((u) => [u.email, u.password, u.secret, u.token].join(',')).join('\n') + '\n');

  const routes = (await call('GET', '/api/routes')).json;
  writeFileSync(join(OUT, 'routes.csv'), routes.map((r) => r.id).join('\n') + '\n');

  // Pares origen-destino que SI tienen ruta: un 404 de "no hay ruta" es una
  // respuesta valida del negocio, pero en la prueba de carga contaria como error.
  const places = (await call('GET', '/api/places')).json.filter((p) => p.routeCount > 0).sort((a, b) => b.routeCount - a.routeCount);
  const pairs = [];
  for (let a = 0; a < places.length && pairs.length < PAIRS; a++) {
    for (let b = a + 1; b < places.length && pairs.length < PAIRS; b += 3) {
      const r = await call('GET', `/api/routes/search?originPlaceId=${places[a].id}&destinationPlaceId=${places[b].id}`, null, users[0].token);
      if (r.status === 200) pairs.push(`${places[a].id},${places[b].id}`);
    }
  }
  writeFileSync(join(OUT, 'pairs.csv'), pairs.join('\n') + '\n');

  console.log(`Listo en ${((Date.now() - started) / 1000).toFixed(0)} s: ${COUNT} cuentas, ${routes.length} rutas, ${pairs.length} pares -> ${OUT}`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((e) => { console.error(e.message); process.exit(1); });
}
