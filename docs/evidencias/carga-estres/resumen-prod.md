# Resumen de las corridas de JMeter

Generado por `perf/jmeter/scripts/summarize.mjs` a partir de los `.jtl`. Tiempos en milisegundos; rendimiento en peticiones por segundo.

## prod-e2

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 254 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 4737 | 4315 | 9583 | 13214 | 20075 | 0 | 0 | 1.2 |
| Q GET /api/routes/{id} | 300 | 5634 | 4901 | 12660 | 15114 | 20731 | 0 | 0 | 1.2 |
| Q GET /api/places | 300 | 12933 | 12756 | 20253 | 25102 | 31634 | 1 | 0.33 | 1.2 |
| **TOTAL** | 900 | 7768 | 6436 | 17874 | 22111 | 31634 | 1 | 0.11 | 3.5 |

Errores por tipo:

- 1 × Q GET /api/places → Non HTTP response message

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 237 | 7716 | 18821 | 0.42 | 2.8 |
| 2 | 217 | 8156 | 16959 | 0 | 2.7 |
| 3 | 214 | 8180 | 17714 | 0 | 2.9 |
| 4 | 210 | 7477 | 17997 | 0 | 3.1 |
| 5 | 22 | 3269 | 7531 | 0 | 1.6 |

## prod-e6

Propiedades: `-Jq.users=100 -Jq.rampup=20 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 100. Duración: 244 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 500 | 11220 | 5042 | 60002 | 60325 | 61071 | 226 | 45.2 | 2.1 |
| Q GET /api/routes/{id} | 500 | 13566 | 6436 | 60002 | 60049 | 60443 | 283 | 56.6 | 2.1 |
| Q GET /api/places | 500 | 16781 | 8979 | 60003 | 60075 | 61071 | 354 | 70.8 | 2.1 |
| **TOTAL** | 1500 | 13855 | 6407 | 60003 | 60079 | 61071 | 863 | 57.53 | 6.1 |

Errores por tipo:

- 226 × Q GET /api/places → HTTP 429
- 192 × Q GET /api/routes/{id} → HTTP 429
- 165 × Q GET /api/routes → HTTP 429
- 125 × Q GET /api/places → Non HTTP response message
- 86 × Q GET /api/routes/{id} → Non HTTP response message
- 58 × Q GET /api/routes → Non HTTP response message
- 5 × Q GET /api/routes/{id} → HTTP 503
- 3 × Q GET /api/routes → HTTP 503
- 3 × Q GET /api/places → HTTP 503

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 413 | 16096 | 39173 | 18.64 | 3.5 |
| 2 | 271 | 22341 | 41236 | 29.15 | 2.3 |
| 3 | 222 | 35595 | 60269 | 50.9 | 1.9 |
| 4 | 589 | 299 | 1116 | 100 | 10.4 |
| 5 | 5 | 642 | 1953 | 100 | 1.6 |
