// Resume los .jtl de una tanda de corridas en tablas Markdown y un JSON.
// Sin dependencias (Node 22).
//
//   node perf/jmeter/scripts/summarize.mjs <carpeta-con-una-subcarpeta-por-escenario> [salida.md]
//
// Cada subcarpeta debe traer resultado.jtl (y, si se uso STATS=1, docker-stats.csv).
// Todas las cifras salen de las muestras del .jtl; no se estima nada.

import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = process.argv[2];
const OUT = process.argv[3] ?? join(ROOT, 'resumen.md');
if (!ROOT) { console.error('Falta la carpeta de resultados.'); process.exit(1); }

/** CSV con comillas y saltos de linea dentro de un campo (los mensajes de fallo los traen). */
function parseCsv(text) {
  const rows = []; let row = []; let field = ''; let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
    else if (c !== '\r') field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  return rows;
}

const pct = (sorted, p) => (sorted.length ? sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)] : 0);

function stats(samples) {
  const t = samples.map((s) => s.elapsed).sort((a, b) => a - b);
  const start = Math.min(...samples.map((s) => s.ts));
  const end = Math.max(...samples.map((s) => s.ts + s.elapsed));
  const errors = samples.filter((s) => !s.ok);
  return {
    n: samples.length,
    avg: Math.round(t.reduce((a, b) => a + b, 0) / t.length),
    p50: pct(t, 50), p90: pct(t, 90), p95: pct(t, 95), p99: pct(t, 99), max: t[t.length - 1],
    errors: errors.length,
    errorPct: +((errors.length / samples.length) * 100).toFixed(2),
    throughput: +(samples.length / Math.max(0.001, (end - start) / 1000)).toFixed(1),
    seconds: Math.round((end - start) / 1000),
  };
}

function errorKinds(samples) {
  const kinds = {};
  for (const s of samples.filter((x) => !x.ok)) {
    const msg = /^\d+$/.test(s.code) ? `HTTP ${s.code}` : (s.message.split(':')[0].split('.').pop() || s.code || 'sin codigo');
    const key = `${s.label} → ${msg}`;
    kinds[key] = (kinds[key] ?? 0) + 1;
  }
  return Object.entries(kinds).sort((a, b) => b[1] - a[1]);
}

function memoryRange(dir) {
  const f = join(dir, 'docker-stats.csv');
  if (!existsSync(f)) return null;
  const mb = (v) => { const m = /([\d.]+)(MiB|GiB)/.exec(v); return m ? (m[2] === 'GiB' ? +m[1] * 1024 : +m[1]) : null; };
  const out = {};
  for (const line of readFileSync(f, 'utf8').split('\n').slice(1)) {
    const [, name, cpu, mem] = line.split(',');
    if (!name || !mem) continue;
    const o = (out[name] ??= { mem: [], cpu: [] });
    const m = mb(mem); if (m !== null) o.mem.push(m);
    o.cpu.push(parseFloat(cpu));
  }
  return Object.fromEntries(Object.entries(out).map(([k, v]) => [k, {
    memInicio: v.mem[0], memFin: v.mem[v.mem.length - 1], memMax: Math.max(...v.mem), cpuMax: Math.max(...v.cpu), muestras: v.mem.length,
  }]));
}

const result = {};
let md = '# Resumen de las corridas de JMeter\n\nGenerado por `perf/jmeter/scripts/summarize.mjs` a partir de los `.jtl`. Tiempos en milisegundos; rendimiento en peticiones por segundo.\n';

for (const name of readdirSync(ROOT).sort((a, b) => a.localeCompare(b, 'es', { numeric: true }))) {
  const jtl = join(ROOT, name, 'resultado.jtl');
  if (!existsSync(jtl)) continue;
  const rows = parseCsv(readFileSync(jtl, 'utf8'));
  const head = rows[0];
  const col = (n) => head.indexOf(n);
  const samples = rows.slice(1).filter((r) => r.length >= head.length && /^\d{13}$/.test(r[0])).map((r) => ({
    ts: +r[col('timeStamp')], elapsed: +r[col('elapsed')], label: r[col('label')], code: r[col('responseCode')],
    message: r[col('responseMessage')], ok: r[col('success')] === 'true', threads: +r[col('allThreads')],
  }));
  if (!samples.length) continue;

  const labels = [...new Set(samples.map((s) => s.label))];
  const perLabel = Object.fromEntries(labels.map((l) => [l, stats(samples.filter((s) => s.label === l))]));
  const total = stats(samples);
  const kinds = errorKinds(samples);
  const mem = memoryRange(join(ROOT, name));
  const props = existsSync(join(ROOT, name, 'propiedades.txt')) ? readFileSync(join(ROOT, name, 'propiedades.txt'), 'utf8').trim() : '';

  // Serie por minuto: sirve para ver degradacion progresiva (escenario 8).
  const t0 = Math.min(...samples.map((s) => s.ts));
  const minutes = {};
  for (const s of samples) (minutes[Math.floor((s.ts - t0) / 60000)] ??= []).push(s);
  const series = Object.entries(minutes).map(([m, ss]) => ({ minuto: +m + 1, ...stats(ss) }));

  result[name] = { props, total, perLabel, errorKinds: kinds, docker: mem, series };

  md += `\n## ${name}\n\n`;
  if (props) md += `Propiedades: \`${props}\`. Hilos simultáneos máximos observados: ${Math.max(...samples.map((s) => s.threads))}. Duración: ${total.seconds} s.\n\n`;
  md += '| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n';
  for (const [l, s] of Object.entries(perLabel)) md += `| ${l} | ${s.n} | ${s.avg} | ${s.p50} | ${s.p95} | ${s.p99} | ${s.max} | ${s.errors} | ${s.errorPct} | ${s.throughput} |\n`;
  md += `| **TOTAL** | ${total.n} | ${total.avg} | ${total.p50} | ${total.p95} | ${total.p99} | ${total.max} | ${total.errors} | ${total.errorPct} | ${total.throughput} |\n`;
  if (kinds.length) md += '\nErrores por tipo:\n\n' + kinds.slice(0, 12).map(([k, n]) => `- ${n} × ${k}`).join('\n') + '\n';
  if (mem) {
    md += '\nContenedores (muestreo de `docker stats` cada 5 s):\n\n| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |\n|---|---:|---:|---:|---:|---:|\n';
    for (const [c, v] of Object.entries(mem)) md += `| ${c} | ${v.memInicio} | ${v.memFin} | ${v.memMax} | ${v.cpuMax} | ${v.muestras} |\n`;
  }
  if (series.length >= 5) {
    md += '\nPor minuto:\n\n| Minuto | Muestras | Promedio | p95 | % error | Pet./s |\n|---:|---:|---:|---:|---:|---:|\n';
    for (const s of series) md += `| ${s.minuto} | ${s.n} | ${s.avg} | ${s.p95} | ${s.errorPct} | ${s.throughput} |\n`;
  }
}

writeFileSync(OUT, md);
writeFileSync(OUT.replace(/\.md$/, '.json'), JSON.stringify(result, null, 2));
console.log(`Resumen de ${Object.keys(result).length} corridas -> ${OUT}`);
