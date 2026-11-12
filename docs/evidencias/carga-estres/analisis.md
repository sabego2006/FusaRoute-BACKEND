# Pruebas de carga y estrés — análisis

Corridas del 11 de noviembre de 2026 con Apache JMeter 5.6.3 en modo no-GUI, sobre el backend
en `main` (commit `3666d86`). El plan, los scripts y la forma de reproducir están en
[`perf/jmeter/`](../../../perf/jmeter/README.md). Todas las cifras de este documento salen de
los `.jtl` de esta carpeta, resumidos por `perf/jmeter/scripts/summarize.mjs`; no hay cifras
estimadas. Los tiempos van en segundos salvo que se indique otra cosa.

Qué hay en esta carpeta:

| Ruta | Contenido |
|---|---|
| `resumen-local.md`, `resumen-prod.md`, `resumen-saturacion-sin-reinicio.md` | tablas completas por petición (promedio, p50, p95, p99, máximo, errores, peticiones por segundo), consumo de los contenedores y serie por minuto |
| `local/<escenario>/` | `resultado.jtl.gz` (muestras crudas), `propiedades.txt` (parámetros `-J` usados), `docker-stats.csv` (CPU y memoria cada 5 s), `estadisticas-dashboard.json` (tabla del dashboard de JMeter) y, donde aplica, las verificaciones en base y el sondeo de `/health` |
| `prod/` | lo mismo para las dos corridas de solo lectura contra PROD, más `arranque-prod.txt` |
| `saturacion-sin-reinicio/` | primera pasada del escenario 5 y el estado del backend 33 minutos después |
| `capturas/` | 10 capturas de los dashboards HTML de JMeter |

Los dashboards HTML completos no se versionan (unos 3 MB cada uno). Se regeneran con
`jmeter -g resultado.jtl -o dashboard/` después de descomprimir el `.jtl`.

## 20.4 Configuración de las pruebas

### Ambiente

| Elemento | Valor |
|---|---|
| Sistema bajo prueba | `docker-compose.yml` del repo más `perf/jmeter/docker-compose.perf.yml`: PostgreSQL, backend y frontend en contenedores |
| Límites del backend | 1 CPU, 512 MB de memoria (el contenedor puede usar además hasta 512 MB de swap), pool Hikari de 5 conexiones, 200 hilos de Tomcat (valor por defecto), JVM con `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC` |
| Base de datos | PostgreSQL del compose, sin límites, con las 24 migraciones de Flyway aplicadas (hasta la V26) |
| Equipo | portátil AMD Ryzen 7 5825U (8 núcleos, 16 hilos), 15,4 GB de RAM, Windows 11 Pro, conectado a la corriente; Docker Desktop 29.9.2 con 16 CPU y 7,8 GB asignados |
| Generador de carga | JMeter 5.6.3 sobre OpenJDK 25, **en la misma máquina** que el sistema bajo prueba |
| Espera máxima por respuesta | 30 s (conexión: 5 s); pasado ese tiempo JMeter cuenta la petición como error |
| Datos de prueba | 300 cuentas con segundo factor activo creadas por `seed-users.mjs` contra la API local, 34 rutas, 40 pares origen-destino con ruta |
| Google Maps | sin clave: la búsqueda usa el tiempo estimado y no llama a Google |
| Otros contenedores | SonarQube y otro stack local del equipo estuvieron detenidos durante las corridas |

Estado inicial de cada corrida: los escenarios 1 a 4 se corrieron seguidos sobre el mismo
backend, con 20 s de pausa. Los escenarios 5 a 9 se corrieron con `RESET=1`, que reinicia el
backend y pone en cero los contadores de intentos fallidos y bloqueos de las cuentas de prueba
antes de cada nivel. La corrida de recuperación del escenario 9 se hizo sin reinicio.

### Escenarios

| # | Escenario | Usuarios | Ramp-up | Vueltas | Peticiones |
|---|---|---:|---:|---:|---|
| 1 | Login concurrente | 50 | 10 s | 5 | `POST /api/auth/login` (contraseña + código TOTP) |
| 2 | Consulta masiva | 80 | 15 s | 10 | `GET /api/routes`, `/api/routes/{id}`, `/api/places`, `/api/routes/search`, `/api/users/me/history` |
| 3 | Registro concurrente | 40 | 10 s | 1 | `POST /api/auth/register` y un segundo intento con el mismo correo (espera `409`) |
| 4 | Flujo completo | 20 | 10 s | 5 | login → búsqueda → crear comentario → editarlo → `GET /api/comments/me` |
| 5 | Saturación de login | 25, 50, 100, 200, 300 | 10 s | 5 | `POST /api/auth/login` |
| 6 | Sobrecarga de consultas | 100, 200, 300 | 20 s | 10 | las del escenario 2 |
| 7 | Sobrecarga CRUD | 50, 100, 150 | 20 s | 20 | `POST`, `PUT` y `DELETE /api/comments` |
| 8 | Resistencia continua | 50 | 20 s | 10 min | las consultas del escenario 2 en cada vuelta, login en 1 de cada 10, pausa de 1 s |
| 9 | Falla controlada | 550 (100 login + 300 consultas + 150 CRUD) | 20 s | 5, 10 y 10 | los tres grupos a la vez; después, una corrida pequeña de recuperación (30 de consultas + 10 de login) |

Contra PROD (Render + Supabase) solo se corrieron dos variantes reducidas y de solo lectura
pública, sin cuentas, sin búsqueda y sin escrituras:

| Corrida | Usuarios | Ramp-up | Vueltas | Peticiones |
|---|---:|---:|---:|---|
| `prod-e2` | 30 | 15 s | 10 | `GET /api/routes`, `/api/routes/{id}`, `/api/places` |
| `prod-e6` | 100 | 20 s | 5 | las mismas |

## 20.5 Resultados

### Local

| # | Escenario | Usuarios | Peticiones | Promedio | p95 | % error | Pet./s |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | Login concurrente | 50 | 250 | 14,5 | 22,1 | 0 | 3,0 |
| 2 | Consulta masiva | 80 | 4.000 | 3,2 | 9,5 | 0,03 | 21,9 |
| 3 | Registro concurrente | 40 | 80 | 10,9 | 22,9 | 0 | 2,7 |
| 4 | Flujo completo | 20 | 500 | 2,7 | 12,7 | 0 | 6,7 |
| 5 | Saturación de login | 25 | 125 | 7,2 | 10,7 | 0 | 2,8 |
| 5 | | 50 | 250 | 15,8 | 26,8 | 1,6 | 2,8 |
| 5 | | 100 | 500 | 29,5 | 30,0 | 87,0 | 3,1 * |
| 5 | | 200 | 1.000 | 30,0 | 30,0 | 99,8 | 6,3 * |
| 5 | | 300 | 1.500 | 29,8 | 30,0 | 96,4 | 9,4 * |
| 6 | Sobrecarga de consultas | 100 | 5.000 | 2,9 | 8,0 | 0 | 31,2 |
| 6 | | 200 | 10.000 | 5,7 | 16,3 | 0,19 | 32,6 |
| 6 | | 300 | 15.000 | 8,7 | 20,9 | 0,71 | 32,8 |
| 7 | Sobrecarga CRUD | 50 | 3.000 | 0,15 | 0,28 | 0 | 122,1 |
| 7 | | 100 | 6.000 | 0,49 | 0,90 | 0 | 138,6 |
| 7 | | 150 | 9.000 | 0,75 | 1,31 | 0 | 153,6 |
| 8 | Resistencia continua (10 min) | 50 | 17.588 | 1,5 | 3,9 | 0 | 29,2 |
| 9 | Falla controlada | 550 | 20.000 | 29,6 | 30,0 | 96,98 | 13,2 * |
| 9 | Recuperación | 40 | 770 | 1,0 | 3,2 | 0 | 28,9 |

\* En los niveles con casi todo en error, las «peticiones por segundo» son esperas agotadas a
los 30 s, no trabajo atendido por el servidor.

Detalle por petición de los escenarios con varias (tabla completa en `resumen-local.md`):

| Escenario | Petición | Promedio | p95 | % error |
|---|---|---:|---:|---:|
| 2 (80 usuarios) | `GET /api/routes` | 1,7 | 4,8 | 0 |
| | `GET /api/routes/{id}` | 2,4 | 6,8 | 0 |
| | `GET /api/places` | 4,5 | 10,3 | 0 |
| | `GET /api/routes/search` | 5,9 | 15,2 | 0,13 |
| | `GET /api/users/me/history` | 1,5 | 4,7 | 0 |
| 3 (40 usuarios) | `POST /api/auth/register` | 18,0 | 23,4 | 0 |
| | mismo correo (espera `409`) | 3,8 | 17,3 | 0 |
| 4 (20 usuarios) | login | 10,8 | 13,8 | 0 |
| | `GET /api/routes/search` | 1,2 | 2,1 | 0 |
| | `POST /api/comments` | 0,5 | 0,9 | 0 |
| | `PUT /api/comments/{id}` | 0,6 | 1,1 | 0 |
| | `GET /api/comments/me` | 0,3 | 0,6 | 0 |
| 6 (300 usuarios) | `GET /api/routes` | 5,7 | 11,7 | 0 |
| | `GET /api/routes/{id}` | 7,7 | 16,4 | 0,10 |
| | `GET /api/places` | 9,2 | 18,2 | 0,07 |
| | `GET /api/routes/search` | 15,4 | 28,2 | 3,37 |
| | `GET /api/users/me/history` | 5,5 | 11,8 | 0 |
| 8 (50 usuarios) | login | 7,4 | 11,6 | 0 |
| | `GET /api/routes/search` | 2,3 | 4,5 | 0 |
| | `GET /api/places` | 2,2 | 3,9 | 0 |

Consumo de los contenedores (`docker stats` cada 5 s): el backend estuvo cerca del 100 % de su
CPU en todos los escenarios. La base no pasó del 26 % de una CPU en ninguno (máximo: 25,3 % en
el nivel de 150 del escenario 7). La memoria del backend parte de unos 255 MB tras un reinicio
y llega al límite de 512 MB en los escenarios 2, 6, 8 y 9.

Verificaciones en la base:

- **Escenario 3:** 40 registros y 40 correos distintos (`local/e3/verificacion-bd.txt`); los 40
  segundos intentos recibieron `409`. No hubo duplicados.
- **Escenario 7:** al terminar los tres niveles la tabla `feedback` tenía 100 filas, las del
  escenario 4; todo lo creado por el CRUD se borró. Es un solo conteo al final de los tres
  niveles, no uno por nivel.
- **Escenario 9:** la tabla pasó de 332 a 1.658 filas: 1.326 comentarios creados cuya edición o
  borrado no alcanzó a completarse. (Las 232 filas de más al inicio venían de un intento
  anterior del mismo escenario, interrumpido y descartado.) 0 cuentas bloqueadas; 200 con
  intentos fallidos acumulados.

### PROD (solo lectura pública)

| Corrida | Usuarios | Peticiones | Promedio | p95 | % error | Pet./s |
|---|---:|---:|---:|---:|---:|---:|
| `prod-e2` | 30 | 900 | 7,8 | 17,9 | 0,11 | 3,5 |
| `prod-e6` | 100 | 1.500 | 13,9 | 60,0 | 57,5 | 6,1 * |

El servicio estaba despierto al empezar (`GET /health` en 2,4 s), así que estas cifras no
incluyen arranque en frío. Durante `prod-e6` el servicio de producción **dejó de responder unos
6 minutos**; el detalle está en 20.6.

## 20.6 Análisis de resultados

### Dónde empezó la degradación

- **Login:** el rendimiento se queda en unas 3 peticiones por segundo desde el nivel más bajo
  probado. El tiempo crece con los usuarios (7,2 s de promedio con 25; 15,8 s con 50), los
  primeros errores aparecen con 50 (1,6 %) y con 100 el servicio se cae (87 %). El quiebre está
  entre 50 y 100 usuarios simultáneos.
- **Consultas:** el rendimiento se aplana en unas 32 peticiones por segundo desde 100 usuarios.
  A partir de ahí solo crece la espera: p95 de 8,0 s con 100, 16,3 s con 200 y 20,9 s con 300.
  Los primeros errores aparecen con 200 (0,19 %).
- **CRUD de comentarios:** no se degradó en lo probado. Con 150 usuarios el rendimiento seguía
  subiendo (153,6 peticiones por segundo) con p95 de 1,3 s y sin errores.
- **Resistencia:** en 10 minutos con 50 usuarios no hubo degradación progresiva. El promedio
  por minuto se movió entre 1,2 y 2,0 s sin tendencia al alza y sin errores.
- **Carga combinada:** con 550 usuarios el primer minuto ya tuvo 59 % de error y del segundo en
  adelante el 100 %.

### Petición más lenta

- En términos absolutos, `POST /api/auth/login` y `POST /api/auth/register`: entre 7 y 18 s de
  promedio en todos los escenarios donde aparecen, incluso con 20 usuarios.
- Entre las consultas, `GET /api/routes/search` tuvo el p95 más alto en todos los niveles
  (11,0 s con 100 usuarios, 23,2 s con 200, 28,2 s con 300) y concentró 119 de los 125 errores
  del escenario 6. Le sigue `GET /api/places`.

**No comparable con RNF-01.** El requisito pide p95 menor a 8 s en la consulta de ruta medido
extremo a extremo (cliente, backend, Google Maps y render) sobre red 4G, con Actuator y
Micrometer como instrumento. Aquí no hay Google Maps ni red, y sí hay decenas o cientos de
usuarios compitiendo por una CPU. Los p95 de `/api/routes/search` de este documento describen
capacidad bajo carga, no el cumplimiento de RNF-01.

### Causas probables

1. **bcrypt satura la única CPU (login y registro).** Las contraseñas se guardan con bcrypt de
   costo 12. Durante los escenarios de login el backend estuvo al 100 % de CPU y la base por
   debajo del 6 %, y el rendimiento no pasa de unas 3 peticiones por segundo sin importar la
   carga. No se usó un perfilador; la atribución a bcrypt se apoya en esos datos y en el
   volcado de hilos del punto siguiente.
2. **Un código TOTP vencido multiplica el costo del login.** El volcado de hilos tomado con el
   backend caído (`saturacion-sin-reinicio/estado-backend-tras-saturacion.txt`) muestra 199 de
   los 200 hilos de Tomcat dentro de `LoginService.tryConsumeRecoveryCode`, calculando bcrypt.
   Cuando el login espera en cola más de lo que tolera la verificación del TOTP (el periodo
   actual y uno a cada lado, de 30 s cada uno), el código llega vencido; al fallar, el servicio
   lo compara contra los 10 códigos de respaldo del usuario, cada uno con bcrypt de costo 12.
   Un login fallido cuesta así hasta 11 verificaciones bcrypt en vez de una. Los códigos de
   respaldo tienen 10 caracteres y el TOTP 6 dígitos, de modo que esa comparación no puede
   acertar con un código de 6 dígitos.
3. **El servidor sigue procesando peticiones que el cliente ya abandonó.** JMeter corta a los
   30 s, pero la petición sigue en la cola de Tomcat y consume CPU cuando le llega el turno.
   Por eso el backend no responde ni al `/health` un buen rato después de terminada la carga.
4. **CPU en las consultas.** En el escenario 6 el backend también estuvo al 100 % de CPU, con
   la base por debajo del 19 %. El límite parece estar en el backend y no en la base; no se
   midió qué parte del tiempo de `/api/routes/search` es cálculo y cuál es acceso a datos.
5. **Pool de conexiones.** No se midió el tiempo de espera por conexión, así que no hay
   evidencia a favor ni en contra de que el pool de 5 sea un límite. El escenario 7, que es
   todo escrituras, llegó a 153,6 peticiones por segundo con ese pool.
6. **Memoria.** El backend llega al límite de 512 MB en cualquier corrida sostenida y usa swap
   (224 MB en el volcado). No hubo ningún cierre por falta de memoria (`oom_kill 0`).
7. **Arranque en frío.** No aplica a estas mediciones: en local el servicio siempre estuvo
   arriba, y en PROD estaba despierto al empezar. No se midió el arranque en frío de Render.

### Tipos de error observados

| Tipo | Dónde | Cantidad |
|---|---|---:|
| Espera agotada a los 30 s (`Read timed out`) | prácticamente todos los errores locales: escenario 2 (1), 5 (4, 435, 998 y 1.446 según el nivel), 6 (19 y 106) y 9 (19.384) | — |
| `HTTP 400` en `PUT /api/comments/{id}` | escenario 9: la edición se envió sin identificador porque la creación previa no había respondido | 13 |
| `HTTP 429` | PROD, `prod-e6` | 583 |
| Espera agotada | PROD: `prod-e2` (1) y `prod-e6` (269) | 270 |
| `HTTP 503` | PROD, `prod-e6` | 11 |

En local no hubo ningún `HTTP 5xx` ni respuestas con contenido incorrecto: las peticiones que
el servidor alcanzó a responder dentro de los 30 s fueron correctas.

### Recuperación

- **Tras la falla controlada (escenario 9):** el backend no respondió al `/health` durante los
  primeros 186 s después de terminar la carga y volvió a responder a los 201 s, **sin
  reinicio** (`local/e9-1/recuperacion-health.txt`). La corrida de recuperación posterior dio 0
  errores en 770 peticiones, con promedio de 1,0 s. Ninguna cuenta quedó bloqueada.
- **Tras la saturación de login con reinicio entre niveles:** con 25 y 50 usuarios el `/health`
  respondía a los 5 s de terminar. Con 100, 200 y 300 seguía sin responder a los 120 s, que fue
  lo último que se sondeó antes de reiniciar para el nivel siguiente.
- **Primera pasada del escenario 5, sin reinicio entre niveles** (`saturacion-sin-reinicio/`):
  los cuatro niveles de 50 a 300 corrieron seguidos, justo después de los escenarios 1 a 4. El
  nivel de 50 dio 86 % de error y los demás 100 %. A los 33 minutos de terminar, sin carga
  entrante, el backend seguía al 98 % de CPU y sin responder. Hubo que reiniciarlo. Esa pasada
  dejó 3.250 logins encolados en un backend que ya venía al tope de memoria; no se aisló cuál
  de los dos factores pesó más.
- **Consistencia de datos tras la falla:** quedaron 1.326 comentarios a medio flujo (creados,
  sin editar o sin borrar). No se encontraron registros duplicados ni corruptos.

### Incidente en PROD

`prod-e2` (30 usuarios) terminó con 1 error en 900 peticiones y el `/health` respondía después.

El primer intento de `prod-e6` no llegó al servidor: 1.037 de sus 1.178 errores fueron fallos
de resolución de DNS del propio portátil. Se descartó y se repitió. En la repetición, con 100
usuarios:

| Hora | Qué pasó |
|---|---|
| minuto 1 a 3 | errores crecientes: 18,6 %, 29,2 % y 50,9 % |
| 21:56:05 | última respuesta `200` de la corrida; después, solo `429`, `503` o espera agotada |
| 21:58:27 a 21:59:54 | `GET /health` responde `503` con la cabecera `x-render-routing: hibernate-wake-error` |
| 22:00:17 | `GET /health` responde `429` |
| 22:02:19 | `GET /health` responde `200`. Se recuperó solo |

Fueron unos 6 minutos sin servicio en producción. No se revisó el panel de Render, así que no
se sabe si la instancia se reinició por memoria o por otra causa. No se ejecutó nada más contra
PROD.

## 20.7 Limitaciones

- **El generador de carga comparte la máquina** con el sistema: compite por CPU con él y no hay
  latencia de red. Con 300 a 550 hilos de JMeter esto puede afectar los tiempos; no se midió
  cuánto.
- **El límite de 1 CPU y 512 MB es una elección de la prueba.** La instancia gratuita de Render
  tiene 512 MB y 0,1 CPU, así que los topes en PROD son más bajos que los medidos en local. La
  única referencia directa es `prod-e2`: 30 lectores, 3,5 peticiones por segundo.
- **Cada escenario se corrió una sola vez.** La variación entre corridas no está medida. Como
  referencia, el mismo nivel de 50 logins dio 0 % de errores en el escenario 1 y 1,6 % en el 5.
- **Los escenarios 1 a 4 no parten de un backend recién reiniciado**, a diferencia del 5 al 9.
- **Sin Google Maps.** La búsqueda no hace la llamada externa, que en PROD suma tiempo y está
  sujeta a cuota.
- **Pocos datos:** 34 rutas y unos cientos de usuarios y comentarios. Con más volumen las
  consultas pueden comportarse distinto; no se probó.
- **Pantalla apagada.** Windows registró la pantalla apagada entre 19:50 y 20:19 y entre 20:28
  y 20:29, durante el nivel de 50 del escenario 5 y los niveles de 200 y 300 del 6. Las
  duraciones y el rendimiento de esas corridas son coherentes con las demás, pero queda
  declarado.
- **Corridas descartadas y repetidas:** una tanda del escenario 5 (el portátil entró en
  suspensión estando en batería), un intento del escenario 9 (una actualización automática de
  WSL detuvo Docker a mitad de la corrida) y el primer `prod-e6` (fallo de DNS local). Ninguna
  de sus cifras aparece en este documento.
- **No se midió:** arranque en frío de Render, tiempo de espera del pool de conexiones, tiempos
  de consulta dentro de PostgreSQL, uso del heap de la JVM, ni el frontend.

## 20.8 Propuestas de mejora

Ninguna está implementada ni decidida; son propuestas a partir de lo medido.

| # | Propuesta | Qué observación la respalda |
|---|---|---|
| 1 | En `LoginService`, probar los códigos de respaldo solo cuando el código recibido no tenga el formato del TOTP (6 dígitos) | 199 de 200 hilos en `tryConsumeRecoveryCode`; hasta 11 bcrypt por login fallido |
| 2 | Limitar el login: tope de peticiones por IP y por cuenta, y un máximo de verificaciones bcrypt simultáneas con rechazo rápido (`429` o `503`) al excederlo | el backend no tiene ningún límite y 100 logins simultáneos dejan sin servicio también a quienes solo consultan |
| 3 | Acotar la cola del servidor: menos hilos y cola de Tomcat, y tiempo máximo por petición | el servidor procesa peticiones ya abandonadas; recuperación de 201 s en el mejor caso y ninguna tras 33 minutos en el peor |
| 4 | Caché en memoria del catálogo (`/api/places`, `/api/routes`), que casi no cambia, y perfilar `/api/routes/search` | son las consultas más lentas y las primeras en fallar; hoy solo llevan cabeceras de caché para el navegador |
| 5 | Revisar el tamaño del heap frente a los 512 MB de la instancia | el backend llega al tope de memoria en toda corrida sostenida y en Render no hay swap |
| 6 | Escalado: una instancia de Render con más CPU ataca directamente la causa 1 | toda la degradación medida es por CPU del backend, con la base muy por debajo de su capacidad |
| 7 | No ejecutar pruebas de estrés contra PROD; si hace falta un ambiente parecido, usar un servicio aparte | 100 lectores simultáneos lo dejaron sin responder unos 6 minutos |
| 8 | Medir RNF-01 con su propio instrumento (Actuator y Micrometer, con Google Maps) | esta batería no lo cubre |

Sobre las mejoras habituales que **no** quedan respaldadas por estas mediciones:

- **Índices y consultas SQL:** la base nunca pasó del 26 % de una CPU. No hay evidencia de que
  sea el límite con el volumen de datos actual.
- **Tamaño del pool:** sin medición del tiempo de espera por conexión no se puede recomendar
  subirlo ni bajarlo.
- **Paginación:** no se evaluó. Los catálogos devueltos son pequeños (34 rutas).
