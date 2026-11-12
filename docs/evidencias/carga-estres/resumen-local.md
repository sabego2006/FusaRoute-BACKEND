# Resumen de las corridas de JMeter

Generado por `perf/jmeter/scripts/summarize.mjs` a partir de los `.jtl`. Tiempos en milisegundos; rendimiento en peticiones por segundo.

## e1

Propiedades: `-Jlogin.users=50 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 50. Duración: 85 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 250 | 14481 | 14603 | 22090 | 25600 | 26894 | 0 | 0 | 3 |
| **TOTAL** | 250 | 14481 | 14603 | 22090 | 25600 | 26894 | 0 | 0 | 3 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 321.8 | 337.5 | 340.6 | 100.73 | 13 |
| fusaroute-db-1 | 54.13 | 55.14 | 55.14 | 4.34 | 13 |

## e2

Propiedades: `-Jq.users=80 -Jq.rampup=15 -Jq.loops=10`. Hilos simultáneos máximos observados: 80. Duración: 182 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 800 | 1722 | 1108 | 4785 | 12799 | 19680 | 0 | 0 | 4.4 |
| Q GET /api/routes/{id} | 800 | 2354 | 1620 | 6802 | 14509 | 18799 | 0 | 0 | 4.4 |
| Q GET /api/places | 800 | 4530 | 3599 | 10302 | 19003 | 23987 | 0 | 0 | 4.4 |
| Q GET /api/routes/search | 800 | 5898 | 4887 | 15171 | 22101 | 30011 | 1 | 0.13 | 4.4 |
| Q GET /api/users/me/history | 800 | 1480 | 1001 | 4707 | 7884 | 14496 | 0 | 0 | 4.4 |
| **TOTAL** | 4000 | 3197 | 2109 | 9499 | 18000 | 30011 | 1 | 0.03 | 21.9 |

Errores por tipo:

- 1 × Q GET /api/routes/search → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 337.9 | 512 | 512.1 | 104.65 | 26 |
| fusaroute-db-1 | 55.39 | 56.31 | 60.98 | 13.79 | 26 |

## e3

Propiedades: `-Jr.users=40 -Jr.rampup=10 -Jr.loops=1 -Jrun=1111182252`. Hilos simultáneos máximos observados: 40. Duración: 29 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/register | 40 | 18039 | 19829 | 23394 | 24299 | 24299 | 0 | 0 | 1.4 |
| POST /api/auth/register (mismo correo: espera 409) | 40 | 3787 | 1677 | 17297 | 18624 | 18624 | 0 | 0 | 1.7 |
| **TOTAL** | 80 | 10913 | 12073 | 22886 | 24299 | 24299 | 0 | 0 | 2.7 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 508.1 | 511.9 | 512 | 102.1 | 5 |
| fusaroute-db-1 | 56.68 | 56.73 | 56.73 | 9.68 | 5 |

## e4

Propiedades: `-Jf.users=20 -Jf.rampup=10 -Jf.loops=5`. Hilos simultáneos máximos observados: 20. Duración: 75 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| F1 Login | 100 | 10810 | 11399 | 13790 | 14790 | 15008 | 0 | 0 | 1.3 |
| F2 Consulta: GET /api/routes/search | 100 | 1211 | 1203 | 2096 | 2801 | 2997 | 0 | 0 | 1.4 |
| F3 Registro: POST /api/comments | 100 | 533 | 498 | 904 | 2500 | 2795 | 0 | 0 | 1.4 |
| F4 Actualizacion: PUT /api/comments/{id} | 100 | 584 | 587 | 1103 | 1407 | 1597 | 0 | 0 | 1.4 |
| F5 Consulta final: GET /api/comments/me | 100 | 303 | 298 | 600 | 699 | 700 | 0 | 0 | 1.5 |
| **TOTAL** | 500 | 2688 | 603 | 12689 | 13790 | 15008 | 0 | 0 | 6.7 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 507.9 | 508.4 | 512 | 106.81 | 11 |
| fusaroute-db-1 | 56.66 | 58.06 | 58.06 | 4.8 | 11 |

## e5-25

Propiedades: `-Jlogin.users=25 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 25. Duración: 45 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 125 | 7176 | 7296 | 10704 | 12668 | 15099 | 0 | 0 | 2.8 |
| **TOTAL** | 125 | 7176 | 7296 | 10704 | 12668 | 15099 | 0 | 0 | 2.8 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 255.1 | 283.7 | 283.7 | 99.06 | 7 |
| fusaroute-db-1 | 50.59 | 56.88 | 56.88 | 4.09 | 7 |

## e5-50

Propiedades: `-Jlogin.users=50 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 50. Duración: 90 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 250 | 15804 | 15494 | 26795 | 30004 | 30016 | 4 | 1.6 | 2.8 |
| **TOTAL** | 250 | 15804 | 15494 | 26795 | 30004 | 30016 | 4 | 1.6 | 2.8 |

Errores por tipo:

- 4 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 306.9 | 350.3 | 350.3 | 105.82 | 14 |
| fusaroute-db-1 | 39.06 | 41.65 | 42.39 | 5.79 | 14 |

## e5-100

Propiedades: `-Jlogin.users=100 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 100. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 500 | 29471 | 30007 | 30016 | 30017 | 30019 | 435 | 87 | 3.1 |
| **TOTAL** | 500 | 29471 | 30007 | 30016 | 30017 | 30019 | 435 | 87 | 3.1 |

Errores por tipo:

- 435 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 308.6 | 406.2 | 406.2 | 107.22 | 23 |
| fusaroute-db-1 | 38.93 | 42.12 | 42.31 | 5.48 | 23 |

## e5-200

Propiedades: `-Jlogin.users=200 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 200. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 1000 | 30006 | 30009 | 30016 | 30018 | 30037 | 998 | 99.8 | 6.3 |
| **TOTAL** | 1000 | 30006 | 30009 | 30016 | 30018 | 30037 | 998 | 99.8 | 6.3 |

Errores por tipo:

- 998 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 262.8 | 369.9 | 369.9 | 108.27 | 23 |
| fusaroute-db-1 | 40.71 | 43 | 43.48 | 0.88 | 23 |

## e5-300

Propiedades: `-Jlogin.users=300 -Jlogin.rampup=10 -Jlogin.loops=5`. Hilos simultáneos máximos observados: 300. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| POST /api/auth/login | 1500 | 29834 | 30010 | 30016 | 30017 | 30050 | 1446 | 96.4 | 9.4 |
| **TOTAL** | 1500 | 29834 | 30010 | 30016 | 30017 | 30050 | 1446 | 96.4 | 9.4 |

Errores por tipo:

- 1446 × POST /api/auth/login → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 251.3 | 375.7 | 379 | 102.43 | 24 |
| fusaroute-db-1 | 40.9 | 42.45 | 46.3 | 3.9 | 24 |

## e6-100

Propiedades: `-Jq.users=100 -Jq.rampup=20 -Jq.loops=10`. Hilos simultáneos máximos observados: 100. Duración: 160 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 1000 | 1452 | 1206 | 3597 | 4997 | 7349 | 0 | 0 | 6.3 |
| Q GET /api/routes/{id} | 1000 | 2195 | 1898 | 5203 | 7089 | 11988 | 0 | 0 | 6.3 |
| Q GET /api/places | 1000 | 3508 | 3199 | 6701 | 9187 | 12217 | 0 | 0 | 6.3 |
| Q GET /api/routes/search | 1000 | 5912 | 5695 | 11004 | 14101 | 18397 | 0 | 0 | 6.5 |
| Q GET /api/users/me/history | 1000 | 1318 | 1184 | 3403 | 4903 | 6710 | 0 | 0 | 6.8 |
| **TOTAL** | 5000 | 2877 | 2197 | 8005 | 11196 | 18397 | 0 | 0 | 31.2 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 265.3 | 511.8 | 512.2 | 108.42 | 23 |
| fusaroute-db-1 | 41.52 | 46.6 | 46.63 | 16.05 | 23 |

## e6-200

Propiedades: `-Jq.users=200 -Jq.rampup=20 -Jq.loops=10`. Hilos simultáneos máximos observados: 200. Duración: 307 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 2000 | 2910 | 2424 | 7392 | 11101 | 19899 | 0 | 0 | 6.5 |
| Q GET /api/routes/{id} | 2000 | 4667 | 4203 | 11103 | 15184 | 30006 | 1 | 0.05 | 6.5 |
| Q GET /api/places | 2000 | 5996 | 5587 | 12597 | 15901 | 27591 | 0 | 0 | 6.6 |
| Q GET /api/routes/search | 2000 | 12082 | 11416 | 23198 | 29310 | 30013 | 18 | 0.9 | 6.6 |
| Q GET /api/users/me/history | 2000 | 2817 | 2299 | 7295 | 10599 | 14835 | 0 | 0 | 6.8 |
| **TOTAL** | 10000 | 5694 | 4300 | 16287 | 23200 | 30013 | 19 | 0.19 | 32.6 |

Errores por tipo:

- 18 × Q GET /api/routes/search → Non HTTP response message
- 1 × Q GET /api/routes/{id} → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 249.4 | 510.2 | 512.1 | 104.62 | 44 |
| fusaroute-db-1 | 41.43 | 47.28 | 47.64 | 15.31 | 44 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 1648 | 6806 | 19813 | 0.61 | 19.2 |
| 2 | 1930 | 6136 | 17000 | 0.21 | 22.3 |
| 3 | 2155 | 5559 | 15887 | 0.05 | 27.1 |
| 4 | 2036 | 5857 | 16500 | 0.2 | 23.6 |
| 5 | 2010 | 4855 | 13402 | 0 | 30.8 |
| 6 | 221 | 980 | 2997 | 0 | 31.9 |

## e6-300

Propiedades: `-Jq.users=300 -Jq.rampup=20 -Jq.loops=10`. Hilos simultáneos máximos observados: 300. Duración: 457 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 3000 | 5709 | 5096 | 11698 | 17091 | 23798 | 0 | 0 | 6.6 |
| Q GET /api/routes/{id} | 3000 | 7720 | 6978 | 16411 | 22567 | 30002 | 3 | 0.1 | 6.6 |
| Q GET /api/places | 3000 | 9179 | 8304 | 18191 | 24004 | 30003 | 2 | 0.07 | 6.6 |
| Q GET /api/routes/search | 3000 | 15377 | 14694 | 28195 | 30002 | 30004 | 101 | 3.37 | 6.7 |
| Q GET /api/users/me/history | 3000 | 5510 | 4949 | 11785 | 17104 | 29903 | 0 | 0 | 6.9 |
| **TOTAL** | 15000 | 8699 | 7082 | 20898 | 28605 | 30004 | 106 | 0.71 | 32.8 |

Errores por tipo:

- 101 × Q GET /api/routes/search → Non HTTP response message
- 3 × Q GET /api/routes/{id} → Non HTTP response message
- 2 × Q GET /api/places → Non HTTP response message

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 254.5 | 510.5 | 512.3 | 105.68 | 65 |
| fusaroute-db-1 | 44.34 | 48.39 | 49.01 | 18.63 | 65 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 1783 | 9162 | 22200 | 1.01 | 20.6 |
| 2 | 1868 | 10015 | 23317 | 1.12 | 22.5 |
| 3 | 1940 | 9046 | 20299 | 0.36 | 22.1 |
| 4 | 2136 | 8470 | 19835 | 0.33 | 24.4 |
| 5 | 1931 | 9840 | 25401 | 2.07 | 23.9 |
| 6 | 2101 | 8181 | 18996 | 0.29 | 24.5 |
| 7 | 2097 | 8407 | 18904 | 0.33 | 24.6 |
| 8 | 1144 | 5228 | 14208 | 0 | 30.8 |

## e7-50

Propiedades: `-Jc.users=50 -Jc.rampup=20 -Jc.loops=20`. Hilos simultáneos máximos observados: 29. Duración: 25 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| C1 Crear: POST /api/comments | 1000 | 158 | 187 | 279 | 301 | 383 | 0 | 0 | 40.8 |
| C2 Actualizar: PUT /api/comments/{id} | 1000 | 155 | 188 | 284 | 300 | 398 | 0 | 0 | 41.3 |
| C3 Eliminar: DELETE /api/comments/{id} | 1000 | 147 | 119 | 215 | 296 | 400 | 0 | 0 | 41.5 |
| **TOTAL** | 3000 | 153 | 184 | 281 | 300 | 400 | 0 | 0 | 122.1 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 249 | 298.2 | 298.2 | 97.84 | 4 |
| fusaroute-db-1 | 43.8 | 47.94 | 47.94 | 19.62 | 4 |

## e7-100

Propiedades: `-Jc.users=100 -Jc.rampup=20 -Jc.loops=20`. Hilos simultáneos máximos observados: 100. Duración: 43 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| C1 Crear: POST /api/comments | 2000 | 506 | 505 | 896 | 1099 | 1501 | 0 | 0 | 46.3 |
| C2 Actualizar: PUT /api/comments/{id} | 2000 | 498 | 502 | 897 | 1090 | 1782 | 0 | 0 | 47.2 |
| C3 Eliminar: DELETE /api/comments/{id} | 2000 | 469 | 489 | 895 | 1089 | 1598 | 0 | 0 | 47.3 |
| **TOTAL** | 6000 | 491 | 500 | 896 | 1096 | 1782 | 0 | 0 | 138.6 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 252 | 346 | 346 | 104.84 | 7 |
| fusaroute-db-1 | 44.79 | 47.42 | 47.42 | 21.76 | 7 |

## e7-150

Propiedades: `-Jc.users=150 -Jc.rampup=20 -Jc.loops=20`. Hilos simultáneos máximos observados: 150. Duración: 59 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| C1 Crear: POST /api/comments | 3000 | 780 | 794 | 1307 | 1606 | 2431 | 0 | 0 | 51.3 |
| C2 Actualizar: PUT /api/comments/{id} | 3000 | 764 | 791 | 1307 | 1590 | 2500 | 0 | 0 | 51.8 |
| C3 Eliminar: DELETE /api/comments/{id} | 3000 | 711 | 720 | 1301 | 1602 | 3311 | 0 | 0 | 52 |
| **TOTAL** | 9000 | 752 | 787 | 1306 | 1600 | 3311 | 0 | 0 | 153.6 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 257.5 | 423.4 | 423.4 | 103.58 | 9 |
| fusaroute-db-1 | 44.11 | 47.6 | 51.34 | 25.33 | 9 |

## e8

Propiedades: `-Je8.users=50 -Je8.rampup=20 -Je8.duration=600 -Je8.think=1000`. Hilos simultáneos máximos observados: 50. Duración: 602 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| E8 POST /api/auth/login | 360 | 7372 | 6995 | 11596 | 14202 | 14890 | 0 | 0 | 0.6 |
| E8 GET /api/routes | 3457 | 725 | 599 | 1700 | 2509 | 4596 | 0 | 0 | 5.8 |
| E8 GET /api/routes/{id} | 3454 | 900 | 704 | 2190 | 3107 | 6909 | 0 | 0 | 5.8 |
| E8 GET /api/places | 3452 | 2246 | 2096 | 3888 | 5002 | 7501 | 0 | 0 | 5.8 |
| E8 GET /api/routes/search | 3442 | 2309 | 2098 | 4499 | 6498 | 11608 | 0 | 0 | 5.8 |
| E8 GET /api/users/me/history | 3423 | 630 | 498 | 1690 | 2685 | 5503 | 0 | 0 | 5.8 |
| **TOTAL** | 17588 | 1485 | 1101 | 3888 | 7203 | 14890 | 0 | 0 | 29.2 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 258.8 | 494.5 | 512 | 105.88 | 85 |
| fusaroute-db-1 | 44.24 | 50.8 | 54.94 | 19.55 | 85 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 1179 | 2012 | 5513 | 0 | 18.2 |
| 2 | 1560 | 1768 | 4519 | 0 | 22.6 |
| 3 | 1893 | 1350 | 3202 | 0 | 28.3 |
| 4 | 1494 | 1807 | 5102 | 0 | 23.5 |
| 5 | 1806 | 1495 | 4004 | 0 | 27.6 |
| 6 | 2062 | 1250 | 2998 | 0 | 31.1 |
| 7 | 1809 | 1446 | 3798 | 0 | 29 |
| 8 | 1750 | 1508 | 4195 | 0 | 27.8 |
| 9 | 1921 | 1377 | 3501 | 0 | 29.9 |
| 10 | 2114 | 1211 | 3008 | 0 | 34.4 |

## e9-1

Propiedades: `-Jlogin.users=100 -Jlogin.rampup=20 -Jlogin.loops=5 -Jq.users=300 -Jq.rampup=20 -Jq.loops=10 -Jc.users=150 -Jc.rampup=20 -Jc.loops=10`. Hilos simultáneos máximos observados: 550. Duración: 1520 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 3000 | 28630 | 30002 | 30003 | 30012 | 30017 | 2746 | 91.53 | 2.1 |
| C1 Crear: POST /api/comments | 1500 | 29024 | 30002 | 30003 | 30012 | 30016 | 1397 | 93.13 | 1.7 |
| Q GET /api/routes/{id} | 3000 | 29473 | 30002 | 30003 | 30005 | 30016 | 2874 | 95.8 | 2.1 |
| C2 Actualizar: PUT /api/comments/{id} | 1500 | 29739 | 30002 | 30003 | 30010 | 30017 | 1468 | 97.87 | 1.7 |
| Q GET /api/places | 3000 | 29905 | 30002 | 30003 | 30004 | 30023 | 2972 | 99.07 | 2.1 |
| POST /api/auth/login | 500 | 29023 | 30001 | 30003 | 30003 | 30004 | 453 | 90.6 | 2.9 |
| C3 Eliminar: DELETE /api/comments/{id} | 1500 | 29973 | 30002 | 30004 | 30013 | 30017 | 1490 | 99.33 | 1.6 |
| Q GET /api/routes/search | 3000 | 29989 | 30002 | 30003 | 30006 | 30018 | 2997 | 99.9 | 2 |
| Q GET /api/users/me/history | 3000 | 30002 | 30002 | 30003 | 30011 | 30017 | 3000 | 100 | 2 |
| **TOTAL** | 20000 | 29581 | 30002 | 30003 | 30010 | 30023 | 19397 | 96.98 | 13.2 |

Errores por tipo:

- 3000 × Q GET /api/users/me/history → Non HTTP response message
- 2997 × Q GET /api/routes/search → Non HTTP response message
- 2972 × Q GET /api/places → Non HTTP response message
- 2874 × Q GET /api/routes/{id} → Non HTTP response message
- 2746 × Q GET /api/routes → Non HTTP response message
- 1490 × C3 Eliminar: DELETE /api/comments/{id} → Non HTTP response message
- 1455 × C2 Actualizar: PUT /api/comments/{id} → Non HTTP response message
- 1397 × C1 Crear: POST /api/comments → Non HTTP response message
- 453 × POST /api/auth/login → Non HTTP response message
- 13 × C2 Actualizar: PUT /api/comments/{id} → HTTP 400

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 270 | 511.7 | 512.1 | 111.45 | 214 |
| fusaroute-db-1 | 42.19 | 54.15 | 54.47 | 23.62 | 214 |

Por minuto:

| Minuto | Muestras | Promedio | p95 | % error | Pet./s |
|---:|---:|---:|---:|---:|---:|
| 1 | 1477 | 24300 | 30003 | 59.17 | 16.4 |
| 2 | 1100 | 30002 | 30003 | 100 | 12.2 |
| 3 | 966 | 30002 | 30003 | 100 | 10.7 |
| 4 | 900 | 30002 | 30003 | 100 | 10 |
| 5 | 900 | 30002 | 30003 | 100 | 10 |
| 6 | 900 | 30002 | 30003 | 100 | 10 |
| 7 | 900 | 30002 | 30003 | 100 | 10 |
| 8 | 900 | 30004 | 30014 | 100 | 10 |
| 9 | 900 | 30002 | 30003 | 100 | 10 |
| 10 | 900 | 30002 | 30003 | 100 | 10 |
| 11 | 900 | 30002 | 30003 | 100 | 10 |
| 12 | 900 | 30002 | 30004 | 100 | 10 |
| 13 | 900 | 30002 | 30003 | 100 | 10 |
| 14 | 898 | 30002 | 30003 | 100 | 10 |
| 15 | 815 | 30003 | 30012 | 100 | 9.1 |
| 16 | 600 | 30002 | 30003 | 100 | 6.7 |
| 17 | 600 | 30002 | 30003 | 100 | 6.7 |
| 18 | 600 | 30002 | 30003 | 100 | 6.7 |
| 19 | 600 | 30002 | 30003 | 100 | 6.7 |
| 20 | 600 | 30002 | 30003 | 100 | 6.7 |
| 21 | 600 | 30002 | 30003 | 100 | 6.7 |
| 22 | 600 | 30002 | 30003 | 100 | 6.7 |
| 23 | 600 | 30002 | 30003 | 100 | 6.7 |
| 24 | 588 | 30002 | 30003 | 100 | 6.5 |
| 25 | 356 | 30002 | 30003 | 100 | 4.4 |

## e9-recuperacion

Propiedades: `-Jq.users=30 -Jq.rampup=5 -Jq.loops=5 -Jlogin.users=10 -Jlogin.rampup=5 -Jlogin.loops=2`. Hilos simultáneos máximos observados: 40. Duración: 27 s.

| Petición | Muestras | Promedio | p50 | p95 | p99 | Máx | Errores | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Q GET /api/routes | 150 | 496 | 387 | 1473 | 2187 | 4199 | 0 | 0 | 5.8 |
| Q GET /api/routes/{id} | 150 | 509 | 302 | 1501 | 2702 | 2909 | 0 | 0 | 5.8 |
| Q GET /api/places | 150 | 1816 | 1706 | 2998 | 3599 | 4498 | 0 | 0 | 5.7 |
| Q GET /api/routes/search | 150 | 1300 | 1104 | 3498 | 4609 | 4998 | 0 | 0 | 5.7 |
| Q GET /api/users/me/history | 150 | 396 | 289 | 1081 | 2914 | 3302 | 0 | 0 | 5.7 |
| POST /api/auth/login | 20 | 6366 | 5968 | 8093 | 8392 | 8392 | 0 | 0 | 1.1 |
| **TOTAL** | 770 | 1045 | 600 | 3191 | 6987 | 8392 | 0 | 0 | 28.9 |

Contenedores (muestreo de `docker stats` cada 5 s):

| Contenedor | Memoria al inicio (MiB) | Memoria al final (MiB) | Memoria máxima (MiB) | CPU máxima (%) | Muestras |
|---|---:|---:|---:|---:|---:|
| fusaroute-backend-1 | 507.8 | 510.9 | 512.1 | 99.41 | 5 |
| fusaroute-db-1 | 53.86 | 55.59 | 55.89 | 13.94 | 5 |
