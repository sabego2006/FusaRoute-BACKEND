# Resumen de las corridas de JMeter

Generado por `perf/jmeter/scripts/summarize.mjs` a partir de los `.jtl`. Tiempos en milisegundos; rendimiento en peticiones por segundo.

## e5-50

Propiedades: `-Jlogin.users=50 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 50. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 250 | 28961 | 30009 | 30018 | 30020 | 30030 | 215 | 86 | 1.6 |
| **TOTAL** | 250 | 28961 | 30009 | 30018 | 30020 | 30030 | 215 | 86 | 1.6 |

Errores por tipo:

- 215 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 508.1 | 510.9 | 512 | 106.66 | 22 |
| fusaroute-db-1 | 55.74 | 55.68 | 58.37 | 9.2 | 22 |

## e5-100

Propiedades: `-Jlogin.users=100 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 100. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 500 | 30011 | 30009 | 30018 | 30034 | 30090 | 500 | 100 | 3.1 |
| **TOTAL** | 500 | 30011 | 30009 | 30018 | 30034 | 30090 | 500 | 100 | 3.1 |

Errores por tipo:

- 500 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 508.6 | 511.8 | 512 | 107.33 | 23 |
| fusaroute-db-1 | 53.17 | 51.18 | 53.94 | 10.44 | 23 |

## e5-200

Propiedades: `-Jlogin.users=200 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 200. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 1000 | 30011 | 30010 | 30018 | 30030 | 30089 | 1000 | 100 | 6.3 |
| **TOTAL** | 1000 | 30011 | 30010 | 30018 | 30030 | 30089 | 1000 | 100 | 6.3 |

Errores por tipo:

- 1000 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 508.2 | 510.7 | 512 | 107.26 | 23 |
| fusaroute-db-1 | 51.05 | 51.16 | 51.48 | 11.4 | 23 |

## e5-300

Propiedades: `-Jlogin.users=300 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 300. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 1500 | 30011 | 30009 | 30018 | 30028 | 30096 | 1500 | 100 | 9.4 |
| **TOTAL** | 1500 | 30011 | 30009 | 30018 | 30028 | 30096 | 1500 | 100 | 9.4 |

Errores por tipo:

- 1500 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 511.9 | 509.1 | 511.9 | 107.67 | 23 |
| fusaroute-db-1 | 50.47 | 51.07 | 54.55 | 11.09 | 23 |
