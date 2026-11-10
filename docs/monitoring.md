# Guía de Monitoreo de Disponibilidad - FusaRoute Backend

Para cumplir con el requisito de disponibilidad ($\ge 95\%$) de SCRUM-128, se debe configurar el monitoreo externo mediante **UptimeRobot**.

## Configuración de UptimeRobot

Cree un nuevo monitor con los siguientes parámetros:

| Campo | Valor | Notas |
| :--- | :--- | :--- |
| **Monitor Type** | HTTP(s) | Monitoreo estándar de endpoint |
| **Friendly Name** | FusaRoute Backend Health | Identificador del servicio |
| **URL (or IP)** | `https://fusaroute-backend.onrender.com/health` | Endpoint público de salud |
| **Monitoring Interval** | 5 minutes | Intervalo mínimo recomendado en plan gratuito |
| **Timeout** | 30 seconds | **Crítico**: Render tiene "cold start" en tier gratuito (~60s) |

## Verificación
El monitor debe marcarse como **UP** siempre que el endpoint responda con un código HTTP `200 OK`.

## Decisión sobre el cold start (2026-11-08)

El tier gratis de Render duerme el servicio tras ~15 min sin tráfico y el siguiente
usuario espera hasta ~60 s. Es incompatible con `LCP < 2.5 s` (RNF-01) y ninguna
optimización de payload o de caché lo arregla. Opciones evaluadas: ping de calentamiento
($0), plan pago de Render (~7 USD/mes) o declarar el RNF "con el servicio caliente".

**Decisión: ping de calentamiento.** `.github/workflows/keep-warm.yml` hace `GET /health`
cada 10 min entre las 06:00 y las 23:00 (hora de Bogotá). UptimeRobot (arriba) también
consulta `/health` cada 5 min, pero es un paso manual que este repo no puede verificar:
no se cuenta con él para mantener el servicio despierto.

**Es un parche, y así se declara ante el comité.** Lo que no cubre:

- El primer request tras un despliegue o un reinicio sigue siendo en frío.
- Fuera de la ventana 06:00–23:00 el servicio puede dormirse.
- GitHub Actions no garantiza la puntualidad del `schedule`: bajo carga puede retrasar o
  saltarse ejecuciones, y en repositorios públicos desactiva los workflows programados
  tras 60 días sin actividad. Si falla, el run queda en rojo y llega un correo.
- Mantener el servicio despierto consume las 750 h/mes gratis de Render (~510 h con esta
  ventana). Otro servicio en la misma cuenta resta de ese cupo.

El LCP de 2.5 s se cumple con el servicio caliente. Con el servicio dormido no, y la
evidencia de RNF-01 debe decirlo así en vez de omitirlo.

## Acción Requerida
El usuario debe aplicar esta configuración manualmente en su cuenta de [UptimeRobot](https://uptimerobot.com/).

---

# Logs y eventos de uso guardados en la base de datos (SCRUM-186)

Además de la disponibilidad (UptimeRobot), el backend guarda **solo** en PostgreSQL, sin
intervención de nadie, dos cosas. Para la sustentación basta con abrir las tablas en
Supabase y mostrar que se llenan solas.

| Tabla | Qué guarda | Quién la escribe |
| :--- | :--- | :--- |
| `app_logs` | Todo `WARN` y `ERROR` de la aplicación y una fila `INFO` por cada arranque (versión, commit y perfil). | Un appender de Logback (`DatabaseLogAppender`). |
| `search_events` | Una fila por búsqueda de ruta y por toque de «Ya me subí». **Sin IP y sin id de usuario.** | `UsageEventRecorderPort` (ver el estado abajo). |

> **Estado de `search_events`:** la tabla, el puerto, el adaptador asíncrono y el endpoint
> de lectura existen, pero **la búsqueda y «Ya me subí» todavía no llaman al puerto**: esa
> conexión es de P6. Hasta que P6 se mergee, `search_events` estará vacía y las consultas
> 2 y 3 de abajo devolverán cero filas. `app_logs` funciona desde ya.

## Cómo se ve en Supabase

1. Entrar al proyecto `fusaroute-dev` (o `fusaroute-prod`) → **SQL Editor**.
2. Las tablas están en el esquema `fusaroute`, así que las consultas llevan el prefijo
   `fusaroute.` (el SQL Editor del dashboard no usa el `search_path` del rol de la app).
3. Alternativa sin SQL: **Table Editor** → esquema `fusaroute` → `app_logs`, ordenar por
   `ts` descendente.

### Consulta 1 — últimos errores

```sql
SELECT ts AT TIME ZONE 'America/Bogota' AS hora_bogota,
       level,
       logger,
       left(message, 200) AS mensaje
  FROM fusaroute.app_logs
 WHERE level = 'ERROR'
 ORDER BY ts DESC
 LIMIT 20;
```

Para ver también las advertencias, cambiar la condición por `level IN ('WARN', 'ERROR')`.
La columna `exception` trae la pila recortada a 4.000 caracteres.

### Consulta 2 — búsquedas por hora del día

```sql
SELECT extract(hour FROM ts AT TIME ZONE 'America/Bogota')::int AS hora,
       count(*) AS busquedas
  FROM fusaroute.search_events
 WHERE event_type = 'SEARCH'
   AND ts >= now() - interval '7 days'
 GROUP BY 1
 ORDER BY 1;
```

### Consulta 3 — porcentaje de búsquedas con resultado

«Con resultado» es que el sistema entregó una ruta: `FOUND` o `LONG_WALK` (hay ruta, pero
la caminata es larga). `NOT_FOUND` y `ERROR` no cuentan.

```sql
SELECT count(*) AS busquedas,
       count(*) FILTER (WHERE outcome IN ('FOUND', 'LONG_WALK')) AS con_resultado,
       round(100.0 * count(*) FILTER (WHERE outcome IN ('FOUND', 'LONG_WALK'))
             / NULLIF(count(*), 0), 1) AS porcentaje_con_resultado
  FROM fusaroute.search_events
 WHERE event_type = 'SEARCH'
   AND ts >= now() - interval '7 days';
```

## Por la API (solo administrador)

```http
GET /api/admin/logs?level=ERROR&since=2026-11-08T00:00:00Z&limit=50
GET /api/admin/search-events?since=2026-11-08T00:00:00Z&limit=50
Authorization: Bearer <token de administrador>
```

- `level`: `INFO`, `WARN` o `ERROR`, **exacto** (no «este nivel o superior»).
- `since`: ISO-8601 con zona (`2026-11-08T00:00:00Z`).
- `limit`: de 1 a 500, por defecto 100. Fuera de rango responde 400 (no se recorta en silencio).
- Orden: del más reciente al más antiguo. Sin token responde 401; con un usuario normal, 403.
- Para obtener un token de administrador en DEV: `docs/datos/crear-administrador-en-dev.md`.

## Cómo funciona y qué NO garantiza

- **Escritura asíncrona y acotada.** Quien loguea (o registra un evento) solo copia el dato
  a una cola en memoria y sigue. Un hilo aparte la vacía por lotes con el **mismo pool de
  Hikari** que usa JPA; no abre conexiones propias. Si la base está lenta o caída y la cola
  se llena, **se descartan eventos**; la petición del usuario nunca espera ni falla por un log.
- **Se pueden perder eventos, y eso es deliberado.** Hay tres caminos: cola llena, lote
  que falla al escribir, y proceso que muere con eventos aún en memoria (Render puede
  apagar el servicio sin avisar; lo que esté en cola en ese instante no se guarda). Por eso **esto es monitoreo, no una bitácora de auditoría**.
- **Contador de descartes:** `fusaroute_monitoring_dropped_total{queue="app_logs"|"search_events"}`
  en `/actuator/prometheus` (o `fusaroute.monitoring.dropped` en `/actuator/metrics`), y la
  cola pendiente en `fusaroute.monitoring.queue.size`. Los dos exigen JWT (SCRUM-127).
- **Los WARN del arranque no se pierden:** el appender encola desde el primer instante y
  empieza a escribir cuando Spring está listo y Flyway ya creó las tablas (hasta el
  tamaño de la cola; más allá se descartan y se cuentan).
- **Secretos:** antes de guardar se quitan la llave de Google, los JWT, el encabezado
  `Authorization`, contraseñas y claves con nombre (`password=`, `secret`, `token`,
  `api_key`…) y credenciales dentro de URLs de conexión. Es una red de seguridad por
  patrones, no una garantía: **la regla de fondo sigue siendo no loguear secretos.**
- **Retención:** se borra lo mayor a 30 días en las dos tablas, una vez al día (03:15
  hora de Colombia) **y una vez en cada arranque**, porque Render duerme el servicio y el
  cron diario puede no llegar a dispararse. El plan gratis de Supabase tiene 500 MB para
  todo el proyecto.
- **Render duerme el servicio:** mientras duerme no hay procesos que logueen, así que no
  hay filas; una fila INFO de arranque marca cada despertar (cold start).

## Propiedades (`application.properties`)

| Propiedad | Defecto | Efecto |
| :--- | :--- | :--- |
| `app.logs.retention-days` | `30` | Días que se conservan los datos. Mínimo 1. |
| `app.logs.queue-size` | `1000` | Tamaño de cada cola (logs y eventos). Más grande = más memoria y más eventos que sobreviven a una base lenta. |
| `app.logs.batch-size` | `50` | Filas por `INSERT` en lote. |
| `app.logs.flush-interval-ms` | `1000` | Cada cuánto despierta el escritor si no hay trabajo. |
| `app.logs.purge-cron` | `0 15 3 * * *` | Cuándo corre la purga diaria (hora de Colombia). |
