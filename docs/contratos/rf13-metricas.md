# Contrato RF-13: panel de métricas de uso (SCRUM-26)

Backend: este repo. Consume el frontend (A8, Angélica). Si algo de aquí cambia, se edita este archivo **antes** de abrir el PR.

## `GET /api/admin/metrics`

Requiere `scope=full` + `ROLE_ADMIN` (lo exige `SecurityConfig` sobre todo `/api/admin/**`; no hay lógica de rol en el controlador).

Respuestas: `200 AdminMetricsResponse` · `401` sin token · `403` sin rol de administrador (o token sin `scope=full`).

Sin parámetros. **Sin caché y sin actualización en vivo:** cada `GET` recalcula todo, así que el panel refleja el momento en que se abre o se recarga.

```json
{
  "totalUsers": 120,
  "searchesToday": 37,
  "topRoutes": [
    { "routeId": 3, "routeName": "Centro - Galería - Terminal", "searches": 19 },
    { "routeId": 8, "routeName": "Chinauta", "searches": 11 }
  ],
  "pendingComments": 4,
  "activeSuspensions": 2,
  "generatedAt": "2026-11-13T10:15:30-05:00",
  "timezone": "America/Bogota",
  "topRoutesWindowDays": 30
}
```

El cuerpo tiene **exactamente ocho campos**: las cinco métricas y tres de metadatos. No hay datos personales (ni correos ni nombres de usuarios); solo conteos y nombres de ruta.

| Campo | Qué es | Cómo se calcula |
|---|---|---|
| `totalUsers` | **Métrica 1.** Cuentas registradas | `count(*)` de `users`. Incluye **todas** las cuentas, también las de administrador |
| `searchesToday` | **Métrica 2.** Búsquedas del día de hoy | `search_events` con `event_type = 'SEARCH'` y `00:00 <= ts < 24:00` del día calendario actual **en `America/Bogota`** (borde inferior inclusivo, superior exclusivo). Cuenta toda búsqueda sin importar el resultado (`FOUND`, `LONG_WALK`, `NOT_FOUND`, `ERROR`) y las dos variantes, con y sin red; **no** cuenta el toque de «Ya me subí» (`BOARDED`) |
| `topRoutes` | **Métrica 3.** Las rutas más consultadas | ver «Desviación» abajo. Máximo 5, por `searches` descendente y, a igual número, por `routeName` (sin distinguir mayúsculas y, si aún empata, por `routeId`). Lista vacía si no hubo búsquedas con ruta |
| `pendingComments` | **Métrica 4.** Comentarios pendientes de respuesta | el mismo `FeedbackStatus.PENDING` de RF-08/RF-14 (`CountPendingFeedbackUseCase`), el que también bloquea la edición del comentario |
| `activeSuspensions` | **Métrica 5.** Suspensiones vigentes ahora | `starts_at <= ahora < ends_at` y `lifted_at IS NULL` en `route_suspensions` (RF-12), con el mismo predicado que usa el catálogo público |
| `generatedAt` | Instante del cálculo | ISO-8601 **con offset** de Colombia (`-05:00`) |
| `timezone` | Zona con la que se decide «hoy» | siempre `"America/Bogota"` |
| `topRoutesWindowDays` | Días que mira el ranking | siempre `30` |

### El día es el de Colombia

El backend corre en Render (UTC). «Hoy» se calcula con `America/Bogota` y un `Clock` inyectado: a las 21:00 del 13-nov en Colombia (02:00Z del 14) el día sigue siendo el 13. Un evento a las 23:59 `-05:00` cuenta para ese día; uno a las 00:00 `-05:00` del día siguiente, para el siguiente. No hay selector de rango.

### Una ruta inactiva o suspendida sí aparece

El ranking es histórico: una ruta `INACTIVA` o suspendida que acumuló búsquedas se muestra igual, con su nombre actual. Si la ruta **ya no existe** en `routes` (se borró), se omite, y el ranking se completa con la siguiente (no queda corto por culpa de una ruta borrada).

## Desviación respecto de RF-13 (métrica 3)

RF-13 dice que las 5 rutas más consultadas salen del **historial acumulado**. No es posible tal cual:

- `search_history` (RF-07) no guarda la ruta: solo origen y destino, y únicamente las últimas 6 búsquedas por usuario.
- `search_events` (SCRUM-186) sí guarda `route_id`, pero **se purga a 30 días** (`MonitoringDataPurger`) por el tope de 500 MB del plan gratis de Supabase.

Por eso la métrica 3 sale de `search_events`: eventos `SEARCH` con `route_id` no nulo y `ts >= ahora − 30 días`. Es el máximo historial que existe. Esto está dicho en tres sitios: el JavaDoc de `GetAdminMetricsUseCase`/`GetAdminMetricsService`, este documento y el PR. El frontend debe rotular el ranking como «últimos 30 días», no como «histórico» (por eso `topRoutesWindowDays` viaja en la respuesta).

Si el comité exige historial acumulado de verdad, haría falta una tabla de agregados diarios que no se purgue (migración nueva, V29): **no** se hizo porque el prompt no lo pide y una migración se acuerda con Santiago.

## Qué hace la base (sin traer filas a memoria)

- Usuarios: `count(*)`.
- Búsquedas de hoy: `SELECT count(*) FROM search_events WHERE event_type = 'SEARCH' AND ts >= ? AND ts < ?`. Usa el índice `ix_search_events_ts` (V24).
- Ranking: `SELECT route_id, count(*) FROM search_events WHERE event_type = 'SEARCH' AND route_id IS NOT NULL AND ts >= ? GROUP BY route_id`. Usa el mismo índice para el rango de fechas; **no hay índice por `route_id`**, y no hace falta: la tabla no pasa de 30 días de eventos. No se creó migración. Después el caso de uso pide los nombres (`SELECT id, name FROM routes WHERE id IN (…)`, sin cargar trazados), descarta las rutas que no existen, ordena y recorta a 5.
- Suspensiones: `count(*)` con el predicado de `SpringDataRouteSuspensionRepository.EFFECTIVE`.

## Cómo se prueba

Los tests no dependen de la hora real: el caso de uso recibe un `Clock` fijo y los puertos van mockeados (o con un puerto en memoria que aplica el rango semiabierto, para probar el borde de medianoche de verdad). `AdminMetricsControllerTest` verifica 401, 403 y el cuerpo completo del 200 con el `SecurityConfig` real.

## Dependencia

Usa `route_suspensions` (V28) de SCRUM-25. Ese PR (#95) ya está mergeado en `main`; este PR parte de su último commit y no agrega migraciones.
