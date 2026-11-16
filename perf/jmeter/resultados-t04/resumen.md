# Resumen de las corridas de JMeter

Generado por `perf/jmeter/scripts/summarize.mjs` a partir de los `.jtl`. Tiempos en milisegundos; rendimiento en peticiones por segundo.

## 20261115-164343-m-local-main-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 857 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 25277 | 30003 | 30018 | 30021 | 30711 | 168 | 56 | 0.4 |
| Q GET /api/routes/{id} | 300 | 25491 | 30003 | 30016 | 30025 | 30068 | 188 | 62.67 | 0.4 |
| Q GET /api/places | 300 | 29982 | 30007 | 30017 | 30021 | 30033 | 298 | 99.33 | 0.4 |
| **TOTAL** | 900 | 26917 | 30004 | 30017 | 30021 | 30711 | 654 | 72.67 | 1.1 |

Errores por tipo:

- 298 × Q GET /api/places → Non HTTP response message
- 188 × Q GET /api/routes/{id} → Non HTTP response message
- 168 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 271.9 | 399.7 | 399.7 | 12.78 | 128 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 92 | 21191 | 30017 | 45.65 | 1.1 |
| 2 | 65 | 28178 | 30019 | 78.46 | 0.7 |
| 3 | 68 | 27938 | 30021 | 79.41 | 0.8 |
| 4 | 63 | 28182 | 30019 | 82.54 | 0.7 |
| 5 | 68 | 26527 | 30018 | 63.24 | 0.8 |
| 6 | 59 | 29709 | 30017 | 91.53 | 0.7 |
| 7 | 65 | 27492 | 30017 | 73.85 | 0.7 |
| 8 | 68 | 27300 | 30015 | 77.94 | 0.8 |
| 9 | 65 | 27274 | 30017 | 72.31 | 0.7 |
| 10 | 70 | 25772 | 30011 | 58.57 | 0.8 |
| 11 | 66 | 27457 | 30005 | 80.3 | 0.8 |
| 12 | 64 | 27333 | 30005 | 76.56 | 0.7 |
| 13 | 63 | 27288 | 30004 | 69.84 | 0.7 |
| 14 | 24 | 29630 | 30005 | 95.83 | 0.3 |

## 20261115-165835-m-local-main

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 817 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 22925 | 26892 | 30005 | 30005 | 30094 | 144 | 48 | 0.4 |
| Q GET /api/routes/{id} | 300 | 23718 | 30001 | 30005 | 30006 | 30006 | 169 | 56.33 | 0.4 |
| Q GET /api/places | 300 | 29823 | 30003 | 30005 | 30005 | 30006 | 288 | 96 | 0.4 |
| **TOTAL** | 900 | 25489 | 30002 | 30005 | 30006 | 30094 | 601 | 66.78 | 1.1 |

Errores por tipo:

- 288 × Q GET /api/places → Non HTTP response message
- 169 × Q GET /api/routes/{id} → Non HTTP response message
- 144 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 393.3 | 493 | 500.1 | 13.47 | 124 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 105 | 18857 | 30004 | 36.19 | 1.2 |
| 2 | 68 | 26038 | 30006 | 70.59 | 0.8 |
| 3 | 71 | 25973 | 30005 | 64.79 | 0.8 |
| 4 | 68 | 27627 | 30005 | 75 | 0.8 |
| 5 | 63 | 28871 | 30005 | 90.48 | 0.7 |
| 6 | 60 | 30004 | 30005 | 100 | 0.7 |
| 7 | 62 | 28646 | 30005 | 93.55 | 0.7 |
| 8 | 67 | 26046 | 30005 | 68.66 | 0.7 |
| 9 | 78 | 23916 | 30005 | 57.69 | 0.9 |
| 10 | 68 | 25121 | 30005 | 58.82 | 0.8 |
| 11 | 76 | 24061 | 30005 | 47.37 | 0.8 |
| 12 | 67 | 25349 | 30004 | 65.67 | 0.8 |
| 13 | 45 | 25908 | 30005 | 66.67 | 0.6 |
| 14 | 2 | 30001 | 30001 | 100 | 0.1 |

## 20261115-171249-m-local-main

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 754 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 18533 | 18075 | 30004 | 30005 | 30006 | 71 | 23.67 | 0.4 |
| Q GET /api/routes/{id} | 300 | 21545 | 24389 | 30004 | 30005 | 30007 | 121 | 40.33 | 0.4 |
| Q GET /api/places | 300 | 29322 | 30002 | 30005 | 30005 | 30006 | 273 | 91 | 0.4 |
| **TOTAL** | 900 | 23133 | 30001 | 30004 | 30005 | 30007 | 465 | 51.67 | 1.2 |

Errores por tipo:

- 273 × Q GET /api/places → Non HTTP response message
- 121 × Q GET /api/routes/{id} → Non HTTP response message
- 71 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 490.6 | 501.3 | 508.3 | 12.22 | 115 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 123 | 16654 | 30003 | 30.89 | 1.4 |
| 2 | 73 | 24381 | 30005 | 52.05 | 0.8 |
| 3 | 77 | 23238 | 30004 | 49.35 | 0.9 |
| 4 | 72 | 23892 | 30005 | 54.17 | 0.9 |
| 5 | 77 | 24937 | 30005 | 59.74 | 0.9 |
| 6 | 73 | 24172 | 30005 | 57.53 | 0.8 |
| 7 | 70 | 25633 | 30005 | 64.29 | 0.8 |
| 8 | 75 | 24246 | 30004 | 45.33 | 0.9 |
| 9 | 73 | 24457 | 30005 | 58.9 | 0.9 |
| 10 | 71 | 24387 | 30004 | 56.34 | 0.8 |
| 11 | 69 | 24426 | 30004 | 59.42 | 0.8 |
| 12 | 41 | 22752 | 30003 | 51.22 | 0.5 |
| 13 | 6 | 8295 | 14901 | 0 | 0.2 |

## 20261115-172553-m-qr-main

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 25. Duración: 81 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 5720 | 3269 | 16972 | 23273 | 23273 | 0 | 0 | 1 |
| QR GET /api/places | 25 | 29606 | 30002 | 30003 | 30004 | 30004 | 23 | 92 | 0.5 |
| QR GET /api/routes/search/offline | 25 | 30004 | 30005 | 30006 | 30006 | 30006 | 25 | 100 | 0.5 |
| **TOTAL** | 75 | 21777 | 30002 | 30005 | 30006 | 30006 | 48 | 64 | 0.9 |

Errores por tipo:

- 25 × QR GET /api/routes/search/offline → Non HTTP response message
- 23 × QR GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 500.4 | 503 | 506.6 | 11.44 | 13 |

## 20261115-174507-m-local-b1-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 869 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 23506 | 30003 | 30005 | 30005 | 30008 | 157 | 52.33 | 0.4 |
| Q GET /api/routes/{id} | 300 | 25957 | 30002 | 30005 | 30005 | 30009 | 201 | 67 | 0.4 |
| Q GET /api/places | 300 | 30003 | 30003 | 30005 | 30006 | 30015 | 300 | 100 | 0.4 |
| **TOTAL** | 900 | 26489 | 30003 | 30005 | 30005 | 30015 | 658 | 73.11 | 1 |

Errores por tipo:

- 300 × Q GET /api/places → Non HTTP response message
- 201 × Q GET /api/routes/{id} → Non HTTP response message
- 157 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 316.8 | 403.4 | 410.6 | 25.97 | 130 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 116 | 17903 | 30004 | 33.62 | 1.3 |
| 2 | 63 | 28710 | 30005 | 88.89 | 0.7 |
| 3 | 60 | 29048 | 30005 | 86.67 | 0.7 |
| 4 | 64 | 28308 | 30005 | 78.13 | 0.7 |
| 5 | 60 | 29289 | 30005 | 90 | 0.7 |
| 6 | 62 | 28736 | 30005 | 88.71 | 0.7 |
| 7 | 66 | 26917 | 30005 | 69.7 | 0.8 |
| 8 | 73 | 24962 | 30004 | 60.27 | 0.8 |
| 9 | 73 | 25340 | 30005 | 63.01 | 0.9 |
| 10 | 62 | 29184 | 30005 | 87.1 | 0.7 |
| 11 | 61 | 29422 | 30005 | 88.52 | 0.7 |
| 12 | 65 | 27928 | 30005 | 89.23 | 0.7 |
| 13 | 55 | 27045 | 30004 | 65.45 | 0.6 |
| 14 | 20 | 25255 | 30011 | 70 | 0.2 |

## 20261115-180028-m-local-b1

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 855 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 22596 | 27364 | 30017 | 30019 | 30021 | 130 | 43.33 | 0.4 |
| Q GET /api/routes/{id} | 300 | 24752 | 30004 | 30017 | 30018 | 30019 | 175 | 58.33 | 0.4 |
| Q GET /api/places | 300 | 30010 | 30009 | 30018 | 30019 | 30021 | 300 | 100 | 0.4 |
| **TOTAL** | 900 | 25786 | 30006 | 30017 | 30019 | 30021 | 605 | 67.22 | 1.1 |

Errores por tipo:

- 300 × Q GET /api/places → Non HTTP response message
- 175 × Q GET /api/routes/{id} → Non HTTP response message
- 130 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 404 | 446.3 | 453.2 | 23.06 | 129 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 118 | 16454 | 30016 | 33.05 | 1.3 |
| 2 | 67 | 27430 | 30016 | 65.67 | 0.8 |
| 3 | 65 | 28074 | 30019 | 78.46 | 0.7 |
| 4 | 68 | 26024 | 30018 | 63.24 | 0.8 |
| 5 | 73 | 25522 | 30017 | 60.27 | 0.8 |
| 6 | 63 | 28896 | 30019 | 85.71 | 0.7 |
| 7 | 61 | 29542 | 30018 | 96.72 | 0.7 |
| 8 | 66 | 27960 | 30019 | 80.3 | 0.7 |
| 9 | 67 | 25829 | 30017 | 65.67 | 0.8 |
| 10 | 64 | 28246 | 30017 | 71.88 | 0.7 |
| 11 | 67 | 25355 | 30016 | 65.67 | 0.8 |
| 12 | 66 | 27787 | 30017 | 71.21 | 0.7 |
| 13 | 48 | 25804 | 30018 | 64.58 | 0.6 |
| 14 | 7 | 27572 | 30016 | 85.71 | 0.1 |

## 20261115-181516-m-local-b1

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 820 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 21856 | 26079 | 30017 | 30019 | 30019 | 136 | 45.33 | 0.4 |
| Q GET /api/routes/{id} | 300 | 23056 | 30002 | 30017 | 30019 | 30020 | 160 | 53.33 | 0.4 |
| Q GET /api/places | 300 | 30009 | 30009 | 30018 | 30019 | 30020 | 300 | 100 | 0.4 |
| **TOTAL** | 900 | 24974 | 30004 | 30017 | 30019 | 30020 | 596 | 66.22 | 1.1 |

Errores por tipo:

- 300 × Q GET /api/places → Non HTTP response message
- 160 × Q GET /api/routes/{id} → Non HTTP response message
- 136 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 445.4 | 483.4 | 485.2 | 20.59 | 124 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 135 | 14700 | 30015 | 31.11 | 1.5 |
| 2 | 79 | 23308 | 30015 | 49.37 | 0.9 |
| 3 | 68 | 25653 | 30018 | 60.29 | 0.8 |
| 4 | 66 | 27217 | 30018 | 71.21 | 0.7 |
| 5 | 66 | 27458 | 30018 | 77.27 | 0.7 |
| 6 | 79 | 24248 | 30018 | 59.49 | 0.9 |
| 7 | 69 | 25741 | 30016 | 72.46 | 0.8 |
| 8 | 61 | 28172 | 30013 | 81.97 | 0.7 |
| 9 | 67 | 27602 | 30005 | 77.61 | 0.7 |
| 10 | 62 | 29326 | 30015 | 87.1 | 0.7 |
| 11 | 61 | 29415 | 30019 | 93.44 | 0.7 |
| 12 | 56 | 27607 | 30018 | 75 | 0.6 |
| 13 | 28 | 28197 | 30018 | 75 | 0.3 |
| 14 | 3 | 30008 | 30016 | 100 | 0.1 |

## 20261115-182921-m-qr-b1

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 25. Duración: 73 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 4443 | 3753 | 8063 | 9477 | 9477 | 0 | 0 | 2 |
| QR GET /api/places | 25 | 30009 | 30008 | 30016 | 30016 | 30016 | 25 | 100 | 0.6 |
| QR GET /api/routes/search/offline | 25 | 30014 | 30015 | 30018 | 30019 | 30019 | 25 | 100 | 0.6 |
| **TOTAL** | 75 | 21488 | 30007 | 30017 | 30019 | 30019 | 50 | 66.67 | 1 |

Errores por tipo:

- 25 × QR GET /api/places → Non HTTP response message
- 25 × QR GET /api/routes/search/offline → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 478.7 | 479.2 | 482.8 | 11.33 | 12 |

## 20261115-183257-m-local-b2-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 817 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 18390 | 18684 | 30016 | 30019 | 30028 | 93 | 31 | 0.4 |
| Q GET /api/routes/{id} | 300 | 26096 | 30006 | 30016 | 30019 | 30022 | 204 | 68 | 0.4 |
| Q GET /api/places | 300 | 30011 | 30011 | 30018 | 30019 | 30031 | 300 | 100 | 0.4 |
| **TOTAL** | 900 | 24832 | 30006 | 30018 | 30019 | 30031 | 597 | 66.33 | 1.1 |

Errores por tipo:

- 300 × Q GET /api/places → Non HTTP response message
- 204 × Q GET /api/routes/{id} → Non HTTP response message
- 93 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 271.5 | 353.6 | 357.3 | 11.81 | 122 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 115 | 17648 | 30015 | 32.17 | 1.3 |
| 2 | 70 | 26258 | 30017 | 70 | 0.8 |
| 3 | 75 | 22262 | 30017 | 54.67 | 0.9 |
| 4 | 78 | 23680 | 30017 | 55.13 | 0.9 |
| 5 | 60 | 29445 | 30019 | 91.67 | 0.7 |
| 6 | 67 | 27790 | 30018 | 80.6 | 0.7 |
| 7 | 73 | 25530 | 30017 | 65.75 | 0.8 |
| 8 | 68 | 25356 | 30016 | 66.18 | 0.8 |
| 9 | 77 | 23429 | 30016 | 61.04 | 0.9 |
| 10 | 67 | 26506 | 30016 | 74.63 | 0.8 |
| 11 | 62 | 28844 | 30018 | 90.32 | 0.7 |
| 12 | 65 | 25994 | 30017 | 75.38 | 0.8 |
| 13 | 20 | 30015 | 30026 | 100 | 0.2 |
| 14 | 3 | 30016 | 30018 | 100 | 0.1 |

## 20261115-184729-m-local-b2

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 815 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 17927 | 17682 | 30016 | 30020 | 30023 | 104 | 34.67 | 0.4 |
| Q GET /api/routes/{id} | 300 | 25926 | 30005 | 30017 | 30019 | 30023 | 210 | 70 | 0.4 |
| Q GET /api/places | 300 | 30009 | 30009 | 30019 | 30021 | 30022 | 300 | 100 | 0.4 |
| **TOTAL** | 900 | 24621 | 30005 | 30018 | 30020 | 30023 | 614 | 68.22 | 1.1 |

Errores por tipo:

- 300 × Q GET /api/places → Non HTTP response message
- 210 × Q GET /api/routes/{id} → Non HTTP response message
- 104 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 347.1 | 382.1 | 393.5 | 13.89 | 118 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 105 | 18811 | 30012 | 36.19 | 1.2 |
| 2 | 97 | 19739 | 30017 | 46.39 | 1.1 |
| 3 | 82 | 21453 | 30016 | 53.66 | 0.9 |
| 4 | 74 | 25535 | 30018 | 67.57 | 0.8 |
| 5 | 68 | 25927 | 30019 | 69.12 | 0.8 |
| 6 | 66 | 26736 | 30019 | 81.82 | 0.8 |
| 7 | 60 | 30012 | 30019 | 100 | 0.7 |
| 8 | 60 | 30012 | 30018 | 100 | 0.7 |
| 9 | 76 | 24065 | 30016 | 65.79 | 0.9 |
| 10 | 64 | 28510 | 30006 | 92.19 | 0.7 |
| 11 | 71 | 23274 | 30007 | 56.34 | 0.9 |
| 12 | 51 | 28170 | 30019 | 88.24 | 0.6 |
| 13 | 24 | 28675 | 30019 | 83.33 | 0.3 |
| 14 | 2 | 30005 | 30005 | 100 | 0.1 |

## 20261115-190150-m-local-b2

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 219 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 4682 | 89 | 24581 | 30008 | 30021 | 197 | 65.67 | 1.4 |
| Q GET /api/routes/{id} | 300 | 6442 | 86 | 30005 | 30013 | 30020 | 232 | 77.33 | 1.4 |
| Q GET /api/places | 300 | 10060 | 83 | 30015 | 30020 | 30021 | 299 | 99.67 | 1.4 |
| **TOTAL** | 900 | 7061 | 86 | 30011 | 30017 | 30021 | 728 | 80.89 | 4.1 |

Errores por tipo:

- 299 × Q GET /api/places → Non HTTP response message
- 232 × Q GET /api/routes/{id} → Non HTTP response message
- 197 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 384.6 | 8.145 | 396.6 | 12.26 | 33 |

## 20261115-213306-m-local-main-c033-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 246 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 9858 | 7197 | 24903 | 30004 | 30005 | 4 | 2.67 | 0.6 |
| Q GET /api/routes/{id} | 150 | 11528 | 10193 | 25485 | 30002 | 30004 | 4 | 2.67 | 0.6 |
| Q GET /api/places | 150 | 24795 | 26796 | 30004 | 30005 | 30005 | 62 | 41.33 | 0.6 |
| **TOTAL** | 450 | 15393 | 13987 | 30002 | 30005 | 30005 | 70 | 15.56 | 1.8 |

Errores por tipo:

- 62 × Q GET /api/places → Non HTTP response message
- 4 × Q GET /api/routes/{id} → Non HTTP response message
- 4 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 320.8 | 383.9 | 383.9 | 43.81 | 34 |

## 20261115-213743-m-local-main-c033

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 332 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 5283 | 4799 | 10992 | 15987 | 18091 | 0 | 0 | 0.9 |
| Q GET /api/routes/{id} | 300 | 5753 | 4895 | 12093 | 19593 | 23697 | 0 | 0 | 0.9 |
| Q GET /api/places | 300 | 20031 | 19413 | 30002 | 30005 | 30011 | 20 | 6.67 | 0.9 |
| **TOTAL** | 900 | 10356 | 7096 | 26995 | 30003 | 30011 | 20 | 2.22 | 2.7 |

Errores por tipo:

- 20 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 384 | 456.7 | 459.5 | 37.72 | 50 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 198 | 9287 | 28197 | 3.03 | 2.3 |
| 2 | 152 | 11723 | 27397 | 3.29 | 1.8 |
| 3 | 172 | 10439 | 25502 | 0.58 | 2.2 |
| 4 | 171 | 10410 | 24508 | 0.58 | 2.2 |
| 5 | 164 | 10658 | 29785 | 4.27 | 2 |
| 6 | 43 | 8738 | 21606 | 0 | 1.4 |

## 20261115-214328-m-base-main-c033

Propiedades: `-Jq.users=1 -Jq.rampup=1 -Jq.loops=20 -Jq.auth=false`. Hilos simultáneos máximos observados: 1. Duración: 74 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 20 | 493 | 389 | 1059 | 1613 | 1613 | 0 | 0 | 0.3 |
| Q GET /api/routes/{id} | 20 | 522 | 270 | 1894 | 3712 | 3712 | 0 | 0 | 0.3 |
| Q GET /api/places | 20 | 2652 | 2214 | 5117 | 5671 | 5671 | 0 | 0 | 0.3 |
| **TOTAL** | 60 | 1222 | 403 | 3712 | 5671 | 5671 | 0 | 0 | 0.8 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 456.5 | 454.2 | 460.2 | 59.73 | 10 |

## 20261115-214531-m-local-main-c045

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 324 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 4466 | 3591 | 12043 | 15789 | 24180 | 0 | 0 | 0.9 |
| Q GET /api/routes/{id} | 300 | 5387 | 4496 | 13093 | 20682 | 30022 | 1 | 0.33 | 0.9 |
| Q GET /api/places | 300 | 20735 | 21089 | 30012 | 30017 | 30034 | 48 | 16 | 0.9 |
| **TOTAL** | 900 | 10196 | 6312 | 30002 | 30014 | 30034 | 49 | 5.44 | 2.8 |

Errores por tipo:

- 48 × Q GET /api/places → Non HTTP response message
- 1 × Q GET /api/routes/{id} → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 457.7 | 501.4 | 506.6 | 60.44 | 44 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 192 | 10377 | 30012 | 14.06 | 2.2 |
| 2 | 143 | 11911 | 27702 | 4.2 | 1.8 |
| 3 | 176 | 9897 | 27087 | 3.41 | 2 |
| 4 | 155 | 12044 | 30001 | 5.81 | 1.8 |
| 5 | 190 | 8896 | 23964 | 0.53 | 2.5 |
| 6 | 44 | 4139 | 12590 | 0 | 1.9 |

## 20261115-215119-m-base-main-c045

Propiedades: `-Jq.users=1 -Jq.rampup=1 -Jq.loops=20 -Jq.auth=false`. Hilos simultáneos máximos observados: 1. Duración: 19 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 20 | 111 | 76 | 237 | 310 | 310 | 0 | 0 | 1.1 |
| Q GET /api/routes/{id} | 20 | 67 | 59 | 153 | 171 | 171 | 0 | 0 | 1.2 |
| Q GET /api/places | 20 | 745 | 591 | 1285 | 1508 | 1508 | 0 | 0 | 1.1 |
| **TOTAL** | 60 | 308 | 100 | 1275 | 1508 | 1508 | 0 | 0 | 3.2 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 497.3 | 502.4 | 502.4 | 46.59 | 4 |

## 20261115-215224-m-local-main-c060

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 250 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 4589 | 1874 | 30012 | 30020 | 30026 | 25 | 8.33 | 1.2 |
| Q GET /api/routes/{id} | 300 | 3556 | 1988 | 12577 | 30014 | 30016 | 8 | 2.67 | 1.2 |
| Q GET /api/places | 300 | 15015 | 13491 | 30008 | 30015 | 30020 | 24 | 8 | 1.2 |
| **TOTAL** | 900 | 7720 | 3291 | 30008 | 30016 | 30026 | 57 | 6.33 | 3.6 |

Errores por tipo:

- 25 × Q GET /api/routes → Non HTTP response message
- 24 × Q GET /api/places → Non HTTP response message
- 8 × Q GET /api/routes/{id} → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 493.5 | 499.7 | 512 | 80.63 | 36 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 316 | 5651 | 19309 | 0 | 3.9 |
| 2 | 246 | 7408 | 22974 | 0 | 3.2 |
| 3 | 143 | 15240 | 30016 | 39.86 | 1.7 |
| 4 | 187 | 6057 | 14798 | 0 | 3.3 |
| 5 | 8 | 3511 | 7862 | 0 | 0.9 |

## 20261115-215926-m-local-main-c060-sinswap-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 122 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 3997 | 3503 | 8301 | 9889 | 11088 | 0 | 0 | 1.3 |
| Q GET /api/routes/{id} | 150 | 4204 | 3600 | 8832 | 10607 | 11394 | 0 | 0 | 1.3 |
| Q GET /api/places | 150 | 13672 | 13296 | 20979 | 24199 | 24292 | 0 | 0 | 1.3 |
| **TOTAL** | 450 | 7291 | 5256 | 17694 | 22990 | 24292 | 0 | 0 | 3.7 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 312.9 | 368.8 | 368.8 | 67.57 | 19 |

## 20261115-220156-m-local-main-c060-sinswap

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 188 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 2277 | 2069 | 4464 | 6685 | 7822 | 0 | 0 | 1.6 |
| Q GET /api/routes/{id} | 300 | 2381 | 1999 | 5401 | 6797 | 9696 | 0 | 0 | 1.6 |
| Q GET /api/places | 300 | 12555 | 12499 | 19292 | 20881 | 24169 | 0 | 0 | 1.6 |
| **TOTAL** | 900 | 5738 | 3096 | 16511 | 19908 | 24169 | 0 | 0 | 4.8 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 368.6 | 445.2 | 445.2 | 64.23 | 29 |

## 20261115-220537-m-local-main-c050-sinswap

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 204 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 2414 | 2192 | 4996 | 6802 | 7600 | 0 | 0 | 1.5 |
| Q GET /api/routes/{id} | 300 | 2521 | 2203 | 5598 | 6893 | 7402 | 0 | 0 | 1.5 |
| Q GET /api/places | 300 | 13852 | 13806 | 21401 | 25491 | 28590 | 0 | 0 | 1.5 |
| **TOTAL** | 900 | 6262 | 3300 | 19395 | 22603 | 28590 | 0 | 0 | 4.4 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 445 | 488.9 | 488.9 | 55.3 | 31 |

## 20261115-220913-m-local-main-c050-sinswap

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 96 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 1245 | 394 | 4379 | 6560 | 8090 | 155 | 51.67 | 3.1 |
| Q GET /api/routes/{id} | 300 | 1161 | 144 | 4294 | 7098 | 8384 | 159 | 53 | 3.2 |
| Q GET /api/places | 300 | 6405 | 767 | 19301 | 23293 | 29394 | 179 | 59.67 | 3.2 |
| **TOTAL** | 900 | 2937 | 294 | 15792 | 20588 | 29394 | 493 | 54.78 | 9.4 |

Errores por tipo:

- 179 × Q GET /api/places → Non HTTP response message
- 159 × Q GET /api/routes/{id} → Non HTTP response message
- 155 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 488.2 | 507.5 | 507.5 | 53.02 | 14 |

## 20261115-221407-m-local-diag-main-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 190 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 6911 | 6406 | 11693 | 19205 | 24291 | 0 | 0 | 0.8 |
| Q GET /api/routes/{id} | 150 | 8384 | 7319 | 16091 | 21195 | 22493 | 0 | 0 | 0.9 |
| Q GET /api/places | 150 | 19792 | 19686 | 29382 | 30009 | 30013 | 5 | 3.33 | 0.8 |
| **TOTAL** | 450 | 11696 | 9497 | 24603 | 30003 | 30013 | 5 | 1.11 | 2.4 |

Errores por tipo:

- 5 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 340.3 | 456 | 456 | 56.32 | 29 |

## 20261115-221737-m-local-diag-main

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 255 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 4516 | 4200 | 9486 | 11495 | 12499 | 26 | 8.67 | 1.2 |
| Q GET /api/routes/{id} | 300 | 5278 | 4605 | 12579 | 15400 | 20889 | 31 | 10.33 | 1.2 |
| Q GET /api/places | 300 | 14964 | 15791 | 22692 | 27696 | 30016 | 52 | 17.33 | 1.2 |
| **TOTAL** | 900 | 8252 | 5997 | 20691 | 24096 | 30016 | 109 | 12.11 | 3.5 |

Errores por tipo:

- 52 × Q GET /api/places → Non HTTP response message
- 31 × Q GET /api/routes/{id} → Non HTTP response message
- 26 × Q GET /api/routes → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 456.7 | 21.98 | 511.4 | 56.16 | 39 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 198 | 9202 | 22597 | 1.01 | 2.5 |
| 2 | 193 | 9200 | 20021 | 0 | 2.4 |
| 3 | 183 | 9757 | 20898 | 0 | 2.3 |
| 4 | 206 | 8543 | 20895 | 1.46 | 2.8 |
| 5 | 120 | 2368 | 11015 | 86.67 | 8 |

## 20261115-222448-m-local-diag2-main-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 148 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 4908 | 4304 | 9599 | 12296 | 12956 | 0 | 0 | 1 |
| Q GET /api/routes/{id} | 150 | 6053 | 5194 | 13000 | 20894 | 25790 | 0 | 0 | 1 |
| Q GET /api/places | 150 | 15724 | 15594 | 24592 | 29395 | 30012 | 1 | 0.67 | 1.1 |
| **TOTAL** | 450 | 8895 | 7093 | 20192 | 26201 | 30012 | 1 | 0.22 | 3 |

Errores por tipo:

- 1 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 267 | 321.9 | 321.9 | 55.76 | 23 |

## 20261115-222733-m-local-diag2-main

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 125 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 2902 | 2495 | 6200 | 8794 | 10101 | 0 | 0 | 1.2 |
| Q GET /api/routes/{id} | 150 | 3593 | 3293 | 8106 | 12900 | 13900 | 0 | 0 | 1.2 |
| Q GET /api/places | 150 | 15108 | 14894 | 24899 | 26895 | 30012 | 1 | 0.67 | 1.2 |
| **TOTAL** | 450 | 7201 | 4295 | 21418 | 25698 | 30012 | 1 | 0.22 | 3.6 |

Errores por tipo:

- 1 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 321.3 | 360.6 | 363.1 | 53.93 | 20 |

## 20261115-223256-m-local-b1-c050-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 140 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 1885 | 1561 | 4794 | 6476 | 6792 | 0 | 0 | 1.1 |
| Q GET /api/routes/{id} | 150 | 2578 | 1995 | 5510 | 7706 | 11798 | 0 | 0 | 1.1 |
| Q GET /api/places | 150 | 20991 | 21101 | 30013 | 30016 | 30017 | 32 | 21.33 | 1.1 |
| **TOTAL** | 450 | 8485 | 3082 | 30006 | 30014 | 30017 | 32 | 7.11 | 3.2 |

Errores por tipo:

- 32 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 310.4 | 338.1 | 338.1 | 53.24 | 22 |

## 20261115-223546-m-local-b1-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 232 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 1643 | 1293 | 3885 | 6197 | 7337 | 0 | 0 | 1.3 |
| Q GET /api/routes/{id} | 300 | 2216 | 1790 | 5709 | 7990 | 9500 | 0 | 0 | 1.3 |
| Q GET /api/places | 300 | 17546 | 17589 | 26114 | 29110 | 30008 | 1 | 0.33 | 1.3 |
| **TOTAL** | 900 | 7135 | 2589 | 22813 | 27583 | 30008 | 1 | 0.11 | 3.9 |

Errores por tipo:

- 1 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 337.3 | 359.6 | 361.7 | 58.66 | 35 |

## 20261115-224202-m-local-b2-c050-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 150 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 2439 | 1227 | 7571 | 9675 | 10355 | 0 | 0 | 1 |
| Q GET /api/routes/{id} | 150 | 2506 | 2004 | 5898 | 7303 | 8494 | 0 | 0 | 1 |
| Q GET /api/places | 150 | 22897 | 23085 | 30015 | 30017 | 30018 | 51 | 34 | 1 |
| **TOTAL** | 450 | 9281 | 3461 | 30012 | 30016 | 30018 | 51 | 11.33 | 3 |

Errores por tipo:

- 51 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 307.5 | 337.6 | 337.6 | 57.84 | 23 |

## 20261115-224502-m-local-b2-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 218 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 1000 | 673 | 2980 | 4217 | 8894 | 0 | 0 | 1.4 |
| Q GET /api/routes/{id} | 300 | 1612 | 1200 | 4500 | 6704 | 7579 | 0 | 0 | 1.4 |
| Q GET /api/places | 300 | 17545 | 17396 | 26083 | 30003 | 30005 | 3 | 1 | 1.4 |
| **TOTAL** | 900 | 6719 | 1697 | 23190 | 27696 | 30005 | 3 | 0.33 | 4.1 |

Errores por tipo:

- 3 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 337.9 | 354.9 | 355.7 | 55.82 | 32 |

## 20261115-225200-m-local-b3-c050-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 57 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 1196 | 897 | 3038 | 4456 | 4694 | 0 | 0 | 2.7 |
| Q GET /api/routes/{id} | 150 | 2076 | 1795 | 4807 | 8597 | 9495 | 0 | 0 | 2.7 |
| Q GET /api/places | 150 | 6120 | 1594 | 24292 | 27300 | 30015 | 1 | 0.67 | 2.8 |
| **TOTAL** | 450 | 3131 | 1299 | 18302 | 26795 | 30015 | 1 | 0.22 | 7.9 |

Errores por tipo:

- 1 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 305.6 | 322.7 | 322.7 | 51.39 | 10 |

## 20261115-225328-m-local-b3-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 42 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 642 | 593 | 1488 | 1987 | 2096 | 0 | 0 | 7.2 |
| Q GET /api/routes/{id} | 300 | 1365 | 1298 | 2697 | 3679 | 4798 | 0 | 0 | 7.2 |
| Q GET /api/places | 300 | 869 | 707 | 1806 | 2602 | 3498 | 0 | 0 | 7.2 |
| **TOTAL** | 900 | 959 | 797 | 2198 | 3098 | 4798 | 0 | 0 | 21.4 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 322.3 | 330.2 | 330.2 | 53.96 | 8 |

## 20261115-225433-m-local-b3-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 38 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 626 | 493 | 1396 | 2093 | 2796 | 0 | 0 | 7.9 |
| Q GET /api/routes/{id} | 300 | 1183 | 909 | 2805 | 3506 | 3701 | 0 | 0 | 7.9 |
| Q GET /api/places | 300 | 852 | 591 | 2120 | 4201 | 5107 | 0 | 0 | 8 |
| **TOTAL** | 900 | 887 | 690 | 2295 | 3286 | 5107 | 0 | 0 | 23.4 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 330 | 334 | 334 | 71.96 | 7 |

## 20261115-225528-m-qr-b3-c050

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 25. Duración: 7 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 599 | 296 | 2430 | 2436 | 2436 | 0 | 0 | 3.9 |
| QR GET /api/places | 25 | 488 | 296 | 1097 | 2310 | 2310 | 0 | 0 | 4 |
| QR GET /api/routes/search/offline | 25 | 3495 | 3806 | 5979 | 5986 | 5986 | 0 | 0 | 3.7 |
| **TOTAL** | 75 | 1527 | 564 | 5883 | 5986 | 5986 | 0 | 0 | 10.5 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 332.9 | 338.7 | 338.7 | 50.46 | 3 |

## 20261115-225804-m-local-b4-c050-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 66 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 1742 | 1125 | 5389 | 9976 | 9991 | 0 | 0 | 2.3 |
| Q GET /api/routes/{id} | 150 | 1420 | 1001 | 3998 | 6700 | 10906 | 0 | 0 | 2.3 |
| Q GET /api/places | 150 | 7886 | 1195 | 30011 | 30018 | 30022 | 24 | 16 | 2.4 |
| **TOTAL** | 450 | 3683 | 1097 | 30004 | 30014 | 30022 | 24 | 5.33 | 6.8 |

Errores por tipo:

- 24 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 313.4 | 332.7 | 332.7 | 53.64 | 11 |

## 20261115-225939-m-local-b4-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 37 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 830 | 696 | 1895 | 2575 | 3589 | 0 | 0 | 8.1 |
| Q GET /api/routes/{id} | 300 | 778 | 602 | 1801 | 2595 | 3491 | 0 | 0 | 8.1 |
| Q GET /api/places | 300 | 848 | 701 | 1888 | 2807 | 3714 | 0 | 0 | 8.2 |
| **TOTAL** | 900 | 819 | 692 | 1893 | 2702 | 3714 | 0 | 0 | 24.1 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 332.3 | 337.5 | 337.8 | 57.64 | 7 |

## 20261115-230042-m-local-b4-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 29. Duración: 30 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 586 | 496 | 1297 | 1896 | 2088 | 0 | 0 | 10.2 |
| Q GET /api/routes/{id} | 300 | 599 | 499 | 1398 | 1613 | 2495 | 0 | 0 | 10.3 |
| Q GET /api/places | 300 | 596 | 501 | 1491 | 1804 | 2105 | 0 | 0 | 10.3 |
| **TOTAL** | 900 | 594 | 499 | 1399 | 1804 | 2495 | 0 | 0 | 30.3 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 335.4 | 337.7 | 337.7 | 53.5 | 6 |

## 20261115-230125-m-qr-b4-c050

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 25. Duración: 7 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 404 | 311 | 997 | 1008 | 1008 | 0 | 0 | 5.1 |
| QR GET /api/places | 25 | 655 | 805 | 1299 | 1300 | 1300 | 0 | 0 | 4.5 |
| QR GET /api/routes/search/offline | 25 | 2227 | 2184 | 4497 | 4575 | 4575 | 0 | 0 | 3.9 |
| **TOTAL** | 75 | 1095 | 825 | 4390 | 4575 | 4575 | 0 | 0 | 11 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 337.6 | 339.9 | 339.9 | 50.73 | 3 |

## 20261115-231251-m-qr-b5-c050-frio

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 25. Duración: 10 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 1097 | 1232 | 1738 | 2258 | 2258 | 0 | 0 | 4.6 |
| QR GET /api/places | 25 | 1992 | 1793 | 3694 | 4000 | 4000 | 0 | 0 | 3.4 |
| QR GET /api/routes/search/offline | 25 | 4881 | 4189 | 8100 | 8486 | 8486 | 0 | 0 | 2.8 |
| **TOTAL** | 75 | 2657 | 1901 | 7996 | 8486 | 8486 | 0 | 0 | 7.4 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 272.4 | 280.8 | 280.8 | 54.56 | 4 |

## 20261115-231318-m-local-b5-c050-frio

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 41 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 938 | 785 | 2187 | 2795 | 3284 | 0 | 0 | 7.4 |
| Q GET /api/routes/{id} | 300 | 933 | 800 | 2087 | 2689 | 3599 | 0 | 0 | 7.4 |
| Q GET /api/places | 300 | 971 | 802 | 2295 | 2799 | 4405 | 0 | 0 | 7.5 |
| **TOTAL** | 900 | 948 | 799 | 2190 | 2799 | 4405 | 0 | 0 | 21.9 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 281 | 286.8 | 287.7 | 50.58 | 8 |

## 20261115-231414-m-local-b5-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 27. Duración: 29 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 589 | 494 | 1392 | 1606 | 2093 | 0 | 0 | 10.2 |
| Q GET /api/routes/{id} | 300 | 594 | 502 | 1299 | 1596 | 1999 | 0 | 0 | 10.4 |
| Q GET /api/places | 300 | 553 | 496 | 1214 | 1795 | 2200 | 0 | 0 | 10.4 |
| **TOTAL** | 900 | 579 | 499 | 1297 | 1695 | 2200 | 0 | 0 | 30.6 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 288.5 | 297 | 297.9 | 51.48 | 6 |

## 20261115-231458-m-local-b5-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 24. Duración: 27 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 449 | 392 | 894 | 1292 | 2090 | 0 | 0 | 11.4 |
| Q GET /api/routes/{id} | 300 | 471 | 401 | 1094 | 1303 | 1690 | 0 | 0 | 11.6 |
| Q GET /api/places | 300 | 448 | 395 | 998 | 1603 | 2591 | 0 | 0 | 11.6 |
| **TOTAL** | 900 | 456 | 396 | 1000 | 1596 | 2591 | 0 | 0 | 33.7 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 298.1 | 299.8 | 299.8 | 51.05 | 6 |

## 20261115-231539-m-qr-b5-c050

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 11. Duración: 5 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 213 | 198 | 434 | 434 | 434 | 0 | 0 | 5.7 |
| QR GET /api/places | 25 | 256 | 200 | 495 | 598 | 598 | 0 | 0 | 6.1 |
| QR GET /api/routes/search/offline | 25 | 657 | 596 | 1295 | 1492 | 1492 | 0 | 0 | 5.8 |
| **TOTAL** | 75 | 375 | 309 | 1086 | 1492 | 1492 | 0 | 0 | 15.7 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 299.7 | 301 | 301 | 46.48 | 3 |

## 20261115-231558-m-base-b5-c050

Propiedades: `-Jq.users=1 -Jq.rampup=1 -Jq.loops=20 -Jq.auth=false`. Hilos simultáneos máximos observados: 1. Duración: 4 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 20 | 56 | 73 | 102 | 111 | 111 | 0 | 0 | 5.7 |
| Q GET /api/routes/{id} | 20 | 51 | 53 | 91 | 91 | 91 | 0 | 0 | 6 |
| Q GET /api/places | 20 | 53 | 71 | 89 | 92 | 92 | 0 | 0 | 6.1 |
| **TOTAL** | 60 | 53 | 73 | 93 | 111 | 111 | 0 | 0 | 16.6 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 301 | 301.8 | 301.8 | 47.89 | 2 |

## 20261115-231858-m-local-main-heap50-c050-calentamiento

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=5 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 162 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 5149 | 4597 | 9687 | 12092 | 17687 | 0 | 0 | 1 |
| Q GET /api/routes/{id} | 150 | 6006 | 5398 | 10797 | 15109 | 15199 | 0 | 0 | 1 |
| Q GET /api/places | 150 | 18239 | 17293 | 27464 | 30010 | 30016 | 3 | 2 | 1 |
| **TOTAL** | 450 | 9798 | 6991 | 23795 | 28793 | 30016 | 3 | 0.67 | 2.8 |

Errores por tipo:

- 3 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 263.5 | 320.1 | 320.1 | 55.68 | 25 |

## 20261115-232208-m-local-main-heap50-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 244 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 2880 | 2698 | 5799 | 8589 | 11928 | 0 | 0 | 1.2 |
| Q GET /api/routes/{id} | 300 | 3283 | 2903 | 6999 | 10092 | 10995 | 0 | 0 | 1.2 |
| Q GET /api/places | 300 | 16488 | 16399 | 25088 | 28805 | 30017 | 2 | 0.67 | 1.2 |
| **TOTAL** | 900 | 7551 | 3809 | 21790 | 26094 | 30017 | 2 | 0.22 | 3.7 |

Errores por tipo:

- 2 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 319.9 | 391.4 | 395.1 | 53.62 | 37 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 256 | 7127 | 21790 | 0.39 | 3 |
| 2 | 214 | 8282 | 23093 | 0 | 2.8 |
| 3 | 225 | 7968 | 22098 | 0.44 | 2.9 |
| 4 | 201 | 6959 | 19999 | 0 | 3.2 |
| 5 | 4 | 1771 | 3589 | 0 | 1 |

## 20261115-232637-m-local-main-heap50-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 224 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 2423 | 2100 | 5004 | 6196 | 8589 | 0 | 0 | 1.4 |
| Q GET /api/routes/{id} | 300 | 2390 | 2101 | 5214 | 6493 | 7499 | 0 | 0 | 1.4 |
| Q GET /api/places | 300 | 15837 | 15792 | 23693 | 26698 | 26889 | 0 | 0 | 1.3 |
| **TOTAL** | 900 | 6883 | 3199 | 20786 | 24787 | 26889 | 0 | 0 | 4 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 391.7 | 489.7 | 489.7 | 54.83 | 33 |

## 20261115-233043-m-local-main-heap50-c050

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 30. Duración: 203 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 2196 | 2090 | 4299 | 6117 | 6786 | 0 | 0 | 1.5 |
| Q GET /api/routes/{id} | 300 | 2260 | 1990 | 4699 | 6497 | 8608 | 0 | 0 | 1.5 |
| Q GET /api/places | 300 | 14027 | 14096 | 21191 | 24197 | 27794 | 0 | 0 | 1.5 |
| **TOTAL** | 900 | 6161 | 2899 | 18796 | 22195 | 27794 | 0 | 0 | 4.4 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 485.8 | 489.6 | 495.8 | 53.15 | 31 |

## 20261115-233828-m-qr-b5-heap50-frio

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 25. Duración: 11 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 832 | 835 | 1448 | 1644 | 1644 | 0 | 0 | 4.6 |
| QR GET /api/places | 25 | 1611 | 1200 | 3801 | 4398 | 4398 | 0 | 0 | 2.9 |
| QR GET /api/routes/search/offline | 25 | 5737 | 6393 | 8586 | 8695 | 8695 | 0 | 0 | 2.7 |
| **TOTAL** | 75 | 2727 | 1346 | 8401 | 8695 | 8695 | 0 | 0 | 7.1 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 273.7 | 284 | 284 | 49.85 | 3 |

## 20261115-233853-m-local-b5-heap50-frio

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 29. Duración: 39 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 851 | 699 | 1796 | 2498 | 3202 | 0 | 0 | 7.8 |
| Q GET /api/routes/{id} | 300 | 808 | 698 | 1800 | 2490 | 3396 | 0 | 0 | 7.8 |
| Q GET /api/places | 300 | 871 | 797 | 1891 | 2397 | 3215 | 0 | 0 | 7.8 |
| **TOTAL** | 900 | 843 | 702 | 1891 | 2492 | 3396 | 0 | 0 | 23.1 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 283.6 | 287.9 | 288.7 | 52.27 | 7 |

## 20261115-233945-m-local-b5-heap50

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 28. Duración: 29 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 590 | 499 | 1295 | 1786 | 2594 | 0 | 0 | 10.4 |
| Q GET /api/routes/{id} | 300 | 590 | 498 | 1301 | 1602 | 1989 | 0 | 0 | 10.5 |
| Q GET /api/places | 300 | 549 | 502 | 1102 | 1405 | 1808 | 0 | 0 | 10.6 |
| **TOTAL** | 900 | 576 | 499 | 1287 | 1695 | 2594 | 0 | 0 | 31 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 288.4 | 290.8 | 290.9 | 52.99 | 6 |

## 20261115-234027-m-local-b5-heap50

Propiedades: `-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false`. Hilos simultáneos máximos observados: 24. Duración: 25 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 300 | 412 | 392 | 895 | 1098 | 1501 | 0 | 0 | 12.2 |
| Q GET /api/routes/{id} | 300 | 382 | 304 | 797 | 1167 | 1497 | 0 | 0 | 12.2 |
| Q GET /api/places | 300 | 397 | 381 | 804 | 1103 | 1306 | 0 | 0 | 12.1 |
| **TOTAL** | 900 | 397 | 383 | 804 | 1111 | 1501 | 0 | 0 | 35.9 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 290.7 | 301.8 | 301.8 | 63.4 | 5 |

## 20261115-234102-m-qr-b5-heap50

Propiedades: `-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1`. Hilos simultáneos máximos observados: 8. Duración: 5 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QR GET /api/routes | 25 | 134 | 110 | 434 | 519 | 519 | 0 | 0 | 5.7 |
| QR GET /api/places | 25 | 140 | 97 | 398 | 402 | 402 | 0 | 0 | 6 |
| QR GET /api/routes/search/offline | 25 | 336 | 299 | 682 | 693 | 693 | 0 | 0 | 6 |
| **TOTAL** | 75 | 203 | 132 | 607 | 693 | 693 | 0 | 0 | 16.4 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-t04-backend-1 | 298.6 | 298.6 | 298.6 | 43 | 2 |
