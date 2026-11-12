# Pruebas de carga y estrés con Apache JMeter

Batería de los 9 escenarios obligatorios (4 de carga, 5 de estrés) contra la API de
FusaRoute. Los resultados y el análisis de la corrida que se entrega están en
[`docs/evidencias/carga-estres/`](../../docs/evidencias/carga-estres/).

## Qué hay aquí

| Archivo | Para qué |
|---|---|
| `fusaroute.jmx` | el plan de pruebas: un grupo de hilos por tipo de escenario, todos con 0 usuarios por defecto; cada corrida enciende los suyos con propiedades `-J` |
| `run.sh` | ejecuta un escenario en modo no-GUI y deja `.jtl`, dashboard HTML y consumo de los contenedores |
| `scripts/seed-users.mjs` | crea las cuentas de prueba (con segundo factor activo) y los CSV de datos |
| `scripts/summarize.mjs` | resume los `.jtl` en tablas (promedio, p95, errores, peticiones por segundo) |
| `docker-compose.perf.yml` | límites del backend para la prueba: 512 MB, 1 CPU y pool de 5 conexiones |
| `data-example/` | forma de los CSV, con valores de ejemplo |
| `data/`, `results/` | datos reales de prueba y resultados crudos; **no se versionan** |

## Escenarios

| # | Escenario | Grupo de hilos | Usuarios | Ramp-up | Vueltas | Peticiones |
|---|---|---|---|---|---|---|
| 1 | Login concurrente | E1 / E5 | 50 | 10 s | 5 | `POST /api/auth/login` |
| 2 | Consulta masiva | E2 / E6 | 80 | 15 s | 10 | `GET /api/routes`, `/api/routes/{id}`, `/api/places`, `/api/routes/search`, `/api/users/me/history` |
| 3 | Registro concurrente | E3 | 40 | 10 s | 1 | `POST /api/auth/register` y un segundo intento con el mismo correo (espera `409`) |
| 4 | Flujo completo | E4 | 20 | 10 s | 5 | login → búsqueda → crear comentario → editarlo → mis comentarios |
| 5 | Saturación de login | E1 / E5 | 25, 50, 100, 200, 300 | 10 s | 5 | `POST /api/auth/login` |
| 6 | Sobrecarga de consultas | E2 / E6 | 100, 200, 300 | 20 s | 10 | las del escenario 2 |
| 7 | Sobrecarga CRUD | E7 | 50, 100, 150 | 20 s | 20 | `POST`, `PUT` y `DELETE /api/comments` |
| 8 | Resistencia continua | E8 | 50 | 20 s | 10 min | consultas en cada vuelta, login en 1 de cada 10, pausa de 1 s |
| 9 | Falla controlada | E1 + E2 + E7 a la vez | 550 × nivel | 20 s | 5 a 10 | login, consultas y CRUD simultáneos; después una corrida pequeña de recuperación |

Todas las peticiones validan el código HTTP esperado; el login valida además que llegue el
token, y el flujo completo valida que el comentario editado se lea de vuelta. Tiempo máximo
de espera por respuesta: 30 s (pasado ese tiempo JMeter la cuenta como error).

## Cómo reproducir

Requisitos: Docker, Node 22, Java 17 o superior y [Apache JMeter 5.6.3](https://jmeter.apache.org/download_jmeter.cgi)
(descomprimir y apuntar `JMETER` a `bin/jmeter` o `bin/jmeter.bat`). Los comandos son de Bash
(en Windows, Git Bash) y se ejecutan desde la raíz del repo.

```bash
# 1. Levantar el sistema con los límites de la prueba (requiere el .env del compose)
docker compose -f docker-compose.yml -f perf/jmeter/docker-compose.perf.yml up --build -d

# 2. Crear las cuentas de prueba y los CSV (unos minutos: cada registro paga bcrypt)
node perf/jmeter/scripts/seed-users.mjs --base http://localhost:8080 --count 300

# 3. Ejecutar un escenario
JMETER=/ruta/a/apache-jmeter-5.6.3/bin/jmeter STATS=1 perf/jmeter/run.sh e1

# 4. Resumir todas las corridas de una carpeta
node perf/jmeter/scripts/summarize.mjs perf/jmeter/results
```

`run.sh` lanza por dentro el comando no-GUI de JMeter, por ejemplo para el escenario 1:

```bash
jmeter -n -t perf/jmeter/fusaroute.jmx -Jhost=localhost -Jport=8080 -Jprotocol=http \
  -Jdata=perf/jmeter/data -Jlogin.users=50 -Jlogin.rampup=10 -Jlogin.loops=5 \
  -l resultado.jtl -e -o dashboard/
```

Los niveles de estrés (escenarios 5 a 9) se corren con `RESET=1`, por ejemplo
`RESET=1 STATS=1 perf/jmeter/run.sh e5-100`: reinicia el backend y limpia los intentos fallidos
y bloqueos de las cuentas de prueba, para que cada nivel parta del mismo estado. Sin eso, la
cola que deja un nivel saturado contamina el siguiente. La corrida `e9-recuperacion` va sin
`RESET`, justo después de `e9-1`.

Mantener el equipo conectado a la corriente y sin suspenderse: una suspensión a mitad de corrida
desfasa el reloj del contenedor (los códigos TOTP dejan de coincidir) e invalida los tiempos.

Si el compose usa otros puertos (`BACKEND_PORT` en el `.env`), pasar `PORT=...` a `run.sh` y
`--base` al script de cuentas.

## Datos de prueba

- **Cuentas:** `seed-users.mjs` las crea por la API real (registro, login, enrolamiento y
  confirmación del segundo factor) con correos `carga.<corrida>.<n>@example.com` y contraseñas
  aleatorias. Guarda en `data/users.csv` el correo, la contraseña, el secreto TOTP y un token
  de sesión. Ese archivo **no se versiona** y solo sirve en la base donde se generó.
- **Regenerarlas:** `docker compose down -v`, volver a levantar y repetir el paso 2.
- **Segundo factor:** el backend lo exige en todo login, así que el plan calcula el código de
  6 dígitos (RFC 6238) antes de cada login a partir del secreto del CSV. El cálculo va en un
  preprocesador BeanShell porque el Groovy 3 que trae JMeter 5.6.3 no funciona con Java 25.
- **Sin credenciales reales:** el script se niega a correr contra `onrender.com` o Supabase.

## Qué se puede ejecutar contra PROD

Solo `prod-e2` y `prod-e6`: carga reducida, únicamente `GET` públicos (`/api/routes`,
`/api/routes/{id}`, `/api/places`), sin cuentas, sin búsqueda (consume cuota de Google Routes)
y sin escrituras. `run.sh` rechaza cualquier otro escenario contra PROD. Los escenarios de
registro, CRUD y falla controlada se ejecutan **solo** contra el compose local.
