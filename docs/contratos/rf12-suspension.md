# Contrato RF-12: suspensión temporal de rutas y aviso (SCRUM-25)

Backend: este repo. Consume el frontend (A6). Si algo de aquí cambia, se edita este archivo **antes** de abrir el PR.

Todos los instantes salen en ISO-8601 UTC con `Z` (`2026-11-20T14:00:00Z`). En la entrada se acepta cualquier offset (`-05:00`, `Z`…). La zona de negocio es `America/Bogota`; en base se guarda `timestamptz`.

## Cómo funciona (para entender las respuestas)

- **Sin scheduler.** `routes.status` no cambia. Una ruta está *efectivamente suspendida* si existe una suspensión con `startsAt <= ahora < endsAt` y sin levantar. Mientras lo esté, desaparece del catálogo público, el detalle, la tarifa, la búsqueda (online y offline), los lugares con servicio y el "Ya me subí"; **reaparece sola** al pasar `endsAt` o al levantarla.
- **El catálogo se cachea en el navegador hasta 10 min** (`Cache-Control` del listado): una ruta recién suspendida puede seguir viéndose ese rato en un cliente que ya la tenía. El detalle se cachea 60 s.
- **Sin tabla de notificaciones.** El aviso se calcula al leer `GET /api/users/me/notifications`. No hay estado de "leído" en el servidor: el cliente descarta por sesión.

## Endpoints de administrador (`scope=full` + `ROLE_ADMIN`)

### `POST /api/admin/routes/{id}/suspensions`

Body:

```json
{ "startsAt": "2026-11-20T08:00:00-05:00", "endsAt": "2026-11-22T20:00:00-05:00", "reason": "Obras en la Cra. 6" }
```

| Campo | Regla |
|---|---|
| `startsAt` | obligatorio, ISO-8601 **con offset** |
| `endsAt` | obligatorio, ISO-8601 con offset, posterior a `startsAt` y **en el futuro** |
| `reason` | opcional, ≤ 200 caracteres, sin enlaces |
| ventana | duración máxima **90 días** (caza errores de año) |

Respuestas: `201 SuspensionResponse` · `400` (lista por campo en `errors`: `[{field, message}]`, igual que el CRUD de rutas) · `404` (la ruta no existe) · `409` (la ruta está `INACTIVA`, o la ventana se solapa con otra suspensión **no levantada** de la misma ruta) · `401` · `403`.

### `GET /api/admin/suspensions?state=active|upcoming|past|all`

Por defecto `all`. Orden: vigentes, próximas, pasadas (dentro de cada grupo, por `startsAt`). `past` incluye las terminadas y las levantadas. Un `state` desconocido responde `400`.

`200 SuspensionResponse[]`

### `DELETE /api/admin/suspensions/{id}`

Reactivar a mano: fija `liftedAt = ahora`. Responde `200 SuspensionResponse` (ya con `state: "LIFTED"`) · `404` · `409` (ya terminó o ya estaba levantada). Se puede levantar una **próxima** (aún no empezó).

### `SuspensionResponse`

```json
{
  "id": 7, "routeId": 12, "routeName": "Centro - Galería - Terminal",
  "startsAt": "2026-11-20T13:00:00Z", "endsAt": "2026-11-23T01:00:00Z",
  "reason": "Obras en la Cra. 6", "liftedAt": null,
  "state": "ACTIVE"
}
```

`state` se calcula al responder con el reloj del servidor, con esta precedencia: `LIFTED` (tiene `liftedAt`) › `ENDED` (`ahora >= endsAt`) › `ACTIVE` (`startsAt <= ahora < endsAt`) › `UPCOMING`.

## Endpoint de usuario (con sesión)

### `GET /api/users/me/notifications`

`200 UserNotification[]`, `[]` si nada aplica. Solo suspensiones **efectivamente vigentes**; una próxima o una terminada no avisa. Una notificación por suspensión (si el usuario cumple los dos motivos sale `FAVORITE`). Orden: la que termina antes, primero. `401` sin sesión.

```json
{
  "type": "ROUTE_SUSPENDED", "suspensionId": 7, "routeId": 12, "routeName": "Centro - Galería - Terminal",
  "startsAt": "2026-11-20T13:00:00Z", "endsAt": "2026-11-23T01:00:00Z",
  "reason": "Obras en la Cra. 6", "because": "FAVORITE"
}
```

`because` es `FAVORITE` o `FREQUENT`. La definición de cada motivo (decisión de producto, **pendiente de aprobación de Angélica**) está en [`docs/decisiones/rf12-destinatarios-del-aviso.md`](../decisiones/rf12-destinatarios-del-aviso.md). El aviso aparece mientras la suspensión siga vigente: el cliente lo pide al abrir la app y lo descarta por sesión.

## Búsqueda: «la única opción estaba suspendida»

`GET /api/routes/search` y `GET /api/routes/search/offline` siguen respondiendo igual cuando hay ruta. Cuando **no hay ninguna ruta activa** pero sí hay rutas **candidatas para el trayecto que están efectivamente suspendidas**, el `404` de «sin ruta» gana un campo y el mensaje lo dice.

- **Dónde va:** propiedad de primer nivel del cuerpo del `404` (`application/problem+json`), al lado de `nearestRoutes`.
- **Nombre:** `suspendedCandidates`.
- **Forma:** `[{ "routeId": number, "routeName": string, "endsAt": ISO-8601 UTC }]`. Una entrada por ruta, ordenadas por `endsAt` (la que vuelve antes, primero). `endsAt` es el fin de la ventana vigente.
- **Cuándo aparece:** solo si hay candidatas suspendidas. Si no había candidatas, el cuerpo es el de siempre y la propiedad **no existe** (no viene vacía).
- **Qué es «candidata»:** la misma regla de la búsqueda — con trazado, que `matchBetween` encuentre abordaje antes que bajada a ≤ 1.500 m; sin trazado, que origen y destino estén en ese orden en sus barrios.
- **`detail`:** el texto de siempre más una oración, p. ej. `No hay rutas disponibles que conecten Centro con Terminal. La ruta Centro - Galería - Terminal está suspendida hasta el 23 de noviembre a las 1:00 a. m.` (hora de Bogotá; varias rutas se enumeran).

```json
{
  "type": "about:blank", "title": "Not Found", "status": 404,
  "detail": "No hay rutas disponibles que conecten Centro con Terminal. La ruta Centro - Galería - Terminal está suspendida hasta el 22 de noviembre a las 8:00 p. m.",
  "nearestRoutes": { "...": "como hoy" },
  "suspendedCandidates": [
    { "routeId": 12, "routeName": "Centro - Galería - Terminal", "endsAt": "2026-11-23T01:00:00Z" }
  ]
}
```

El listado de lugares (`GET /api/places`) cuenta solo rutas no suspendidas en `routeCount`.
