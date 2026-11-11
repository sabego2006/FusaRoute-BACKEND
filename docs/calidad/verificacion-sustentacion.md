# Verificación en PROD para la sustentación (P6)

> Verificado el **2026-11-10 entre las 22:20 y las 23:50, hora de Bogotá** (03:20–04:50 UTC del 11-nov), contra PROD: backend https://fusaroute-backend.onrender.com en el commit `3666d86` (merge de #88, confirmado por la fila INFO de arranque en `app_logs`), frontend https://fusaroute.vercel.app y BD Supabase `fusaroute-prod`. La BD se consultó **solo con SELECT**.
>
> ✅ = verificado con evidencia. ⏳ = pendiente, con la acción exacta y quién la hace.

## Resumen

| # | Punto | Estado |
|---|---|---|
| 1 | Flyway V20–V25 aplicadas (y V26) | ✅ |
| 2 | `traffic_sectors` con el Indio a 450 m | ✅ |
| 3 | `places` con ≥ 100 filas | ✅ (135) |
| 4 | Ruta 8 = "Pasca" con su trazado | ✅ |
| 5 | API: `/api/places`, búsquedas por `placeId` y por lat/lng, `longWalk`, 404 con `nearestRoutes`, `/api/routes/8` | ✅ |
| 6 | Tiempo con tráfico (`etaSource: TRAFFIC`) | ✅ |
| 7 | Congestión en vivo en la franja | ⏳ repetir entre 11:30–14:00 o 16:30–19:00 (Santiago) |
| 8 | Logs: `app_logs` con filas de arranque | ✅ |
| 9 | Logs: `search_events` con `SEARCH` y `BOARDED` | ⏳ PR #89 sin mergear (Angélica) y luego `BOARDED` con sesión (Santiago) |
| 10 | Frontend en PROD a 320/360/390/412 px, Chromium y WebKit | ✅ con 1 bug a 320 px, arreglado en el PR #51 del frontend (⏳ merge de Angélica) |

## 1. Flyway

```sql
SELECT version, description, success, installed_on
FROM fusaroute.flyway_schema_history WHERE version::text ~ '^(19|2[0-9])$' ORDER BY installed_rank;
```

| version | descripción | ok | aplicada (UTC) |
|---|---|---|---|
| 20 | sembrar sectores congestion | ✅ | 2026-11-09 03:34 |
| 25 | historial de viajes | ✅ | 2026-11-10 15:33 |
| 21 | lugares canonicos | ✅ | 2026-11-10 16:03 |
| 22 | congestion por circulo y velocidades | ✅ | 2026-11-10 16:03 |
| 24 | logs y eventos de uso | ✅ | 2026-11-10 16:03 |
| 23 | trazados osm y pasca | ✅ | 2026-11-10 23:19 |
| 26 | trazados osm rutas verificadas | ✅ | 2026-11-11 03:28 |

V21–V22 y V24 entraron después de V25: es el `spring.flyway.out-of-order=true` de #85, y el arranque deja el WARN esperado "outOfOrder mode is active" en `app_logs`.

## 2–4. Datos sembrados

```sql
SELECT (SELECT count(*) FROM fusaroute.places) AS places,
       (SELECT json_agg(json_build_object('name', name, 'lat', center_lat, 'lng', center_lng,
               'r', radius_m, 'start', start_time, 'end', end_time)) FROM fusaroute.traffic_sectors) AS sectors,
       (SELECT json_build_object('id', id, 'name', name, 'sign', sign_file, 'has_path', path_geojson IS NOT NULL)
          FROM fusaroute.routes WHERE id = 8) AS ruta8;
```

- `places`: **135** filas.
- `traffic_sectors`: "Indio (mediodia)" 12:00–14:00 y "Indio (tarde)" 17:00–19:00, las dos con centro (4.331682, -74.377517) y `radius_m = 450`.
- `routes` id 8: `name = 'Pasca'`, `sign_file = 'PASCA'`, `path_geojson` no nulo.

## 5. API

`GET /api/places` → 200, **135** lugares (5,8 s con el backend recién despertado).

`GET /api/routes/8` → 200: "Pasca", `signFile` PASCA, trazado de 156 vértices, recorrido Potosí › Bomberos › Colegio La Presentación › Pompas La Paz › Panadería Filipo (Pekín) › Corregimiento › Hogar Francisco y Clara › Escuela de los Sauces › Alaska › Buenas Tardes › Crucero La Angostura › La Capilla › Pasca, y la tarifa de **Alaska en $3.550** (vigente desde 2026-02-05).

Búsquedas (`GET /api/routes/search`):

| Caso | Parámetros | Ruta | min | `etaSource` | Caminata subir / bajar | Latencia |
|---|---|---|---|---|---|---|
| placeId | Monumento El Indio (99) → Terminal (128) | 14 Centro - Galería - Terminal | 12 | TRAFFIC | 31 m / 15 m | 10,4 s (primer request, backend dormido) |
| placeId | U. Hospital Éxito (130) → Galería (75) | 14 | 14 | TRAFFIC | 7 / 30 m | 1,3 s |
| placeId, intermunicipal | Potosí (112) → Pasca (109) | 8 Pasca | 28 | TRAFFIC | 10 / 98 m | 0,6 s |
| placeId, intermunicipal | Centro (50) → Arbeláez (4) | 7 Arbeláez | 50 | TRAFFIC | 152 / 5 m | 0,7 s |
| lat/lng | (4.3330, -74.3790) → Terminal | 14 | 23 | TRAFFIC | 287 / 15 m; `origin.label` = "Cerca de Monumento El Indio" | 1,0 s |
| lat/lng | (4.3420, -74.3640) → U. Hospital Éxito | 4 Hospital - Batallón - La Pampa - Llano Grande | 8 | TRAFFIC | 24 / 7 m; "Cerca de Carrera 9" | 0,5 s |
| `longWalk` | Academia Militar (1) → Monumento El Indio (99) | 7 Arbeláez, `longWalk: true` | 7 | TRAFFIC | 1.554 / 44 m | 2,0 s |
| `longWalk` | Centro (50) → Guavio (76) | 23 Palacios - Trinidad - Guavio, `longWalk: true` | 48 | TRAFFIC | 1.905 / 4 m | 0,9 s |
| 404 | Arbeláez (4) → Centro (50) | 404, `nearestRoutes`: origen "Arbeláez" a 5 m, destino "Cuja - Horizonte - Mesitas" a 44 m | — | — | — | 1,0 s |
| 404 | (4.40, -74.30) → Centro | 404, `nearestRoutes`: origen a 10.383 m | — | — | — | 0,2 s |

## 6. Tiempo con tráfico

✅ Todas las búsquedas online salieron con `etaSource: "TRAFFIC"`: la Routes API y la llave de Render funcionan. Latencia con el backend despierto: 0,2–2,0 s; la primera, con Render dormido, 10,4 s.

- `route_speed_observations` tenía **52** filas al terminar: la calibración está guardando lo que mide Google.
- Esta sesión hizo unas 50 llamadas a la Routes API, contra el tope de 150/día que Santiago configuró. Las pruebas del día de la sustentación deben contarse contra ese mismo tope.
- ⏳ **Santiago, el 20-nov:** deshabilitar la Routes API en Google Cloud (acción manual 2 del plan).

## 7. Congestión

La sesión corrió a las 22:20–23:50, fuera de las franjas, así que ninguna búsqueda trajo `congestionSectors`, y eso es lo correcto a esa hora.

- Evidencia con reloj fijo: `SearchRouteServiceTest.el_tramo_que_cruza_el_circulo_en_la_franja_trae_el_aviso` (17:10, un tramo que cruza el círculo trae "Indio (tarde)"), `fuera_de_la_franja_y_de_su_aviso_previo_no_hay_aviso` y `un_tramo_que_no_cruza_el_circulo_no_trae_aviso_aunque_la_ruta_completa_si_lo_cruce`. Verdes en `mvn -B verify` y en el CI del PR #89.
- ⏳ **Santiago:** repetir en vivo entre **11:30 y 14:00** o entre **16:30 y 19:00** (hora de Bogotá):
  ```
  https://fusaroute-backend.onrender.com/api/routes/search?originPlaceId=99&destinationPlaceId=128
  ```
  Se espera `congestionSectors: [{ "name": "Indio (mediodia)" … }]` o "Indio (tarde)", y en el frontend el aviso en condicional ("puede haber trancón…"). Anotar aquí la hora y la respuesta.

## 8–9. Logs

✅ `app_logs`: 41 filas (6 INFO, 35 WARN). Cada arranque deja su INFO, p. ej. `2026-11-11 03:29:52 UTC — FusaRoute iniciado | commit=3666d86… | perfil=prod`.

⏳ `search_events`: **0 filas**, y no por un fallo: P5 dejó el puerto sin llamadores a propósito, y la conexión es el PR **#89** (`feature/SCRUM-186-conectar-eventos`, CI en verde).
- **Angélica:** revisar y mergear #89. Render despliega solo.
- **Santiago, después del deploy:**
  1. Hacer dos búsquedas cualesquiera.
  2. Iniciar sesión y tocar "¡Ya me subí!" en un resultado. Sin sesión, el viaje se guarda solo en el navegador y no llega al backend.
  3. Correr:
     ```sql
     SELECT event_type, outcome, eta_source, latency_ms, congestion, origin_label, destination_label, ts
     FROM fusaroute.search_events ORDER BY ts DESC LIMIT 10;
     ```
     Se esperan filas `SEARCH` y una `BOARDED`, sin coordenadas ni datos del usuario.

## 10. Frontend en PROD

Playwright contra https://fusaroute.vercel.app, a **320, 360, 390 y 412 px**, en **Chromium y WebKit** (40 pruebas).

- Flujo completo: escribir "indio" y "termi", elegir, buscar. Llega al resultado con letrero, "Se demora 12 min con el tráfico de ahora", caminata "aprox." y mapa.
- "¡Ya me subí!" **sin sesión**: pasa a "✓ ¡Buen viaje!" y queda inhabilitado. Al volver a Buscar aparecen "Volver a: Terminal de Transporte" y "Tus últimos viajes (1)" con el letrero, el atajo llena el destino, y `localStorage['fusaroute.viajes.v1']` tiene el viaje.
- En cada pantalla (buscador, resultado, atajo e historial, `/rutas`, `/rutas/8`, login, registro): sin scroll horizontal, objetivos táctiles ≥ 44 px (sin contar los controles internos de Google Maps) y sin textos cortados.

Resultado: **37 de 40 en verde.** Las 3 fallas, todas a 320 px:

1. `/rutas/8` en Chromium y en WebKit: `scrollWidth` 321 > 320, por la tabla de tarifas de Pasca. En la captura además se ve la columna de fecha por fuera de la tarjeta, y la tarjeta "Cómo está el tráfico" vacía (Pasca no cruza el Indio). **Arreglado en el PR #51 del frontend** (fecha corta "16/01/2025" con la larga en `aria-label`, precio sin partir y mensaje "Esta ruta no pasa por ningún sector con trancón registrado"). Verificado con `ng serve` y la respuesta real de PROD: 320 a 320 px en los dos motores. ⏳ Angélica revisa y mergea; luego se repite la corrida en PROD.
2. Resultado en WebKit a 320 px: `scrollWidth` 529 una vez, mientras cargaba el mapa. **No se reprodujo** en una segunda corrida (320). Se registra como intermitente y no se arregló a ciegas.

Capturas revisadas a mano: buscador, resultado, atajo e historial y detalle de Pasca. Fuera de lo anterior, se ven bien.

## Observaciones de producto (no son fallas del contrato)

- **Una intermunicipal puede ganar un viaje urbano corto.** Centro → La Gran Colombia sale con la buseta de Arbeláez ($3.450) y Terminal → Indio también, y Centro → Cedritos sale con la de Pasca. El ranking es por tiempo y la intermunicipal se estima a 30 km/h contra 15 km/h. Es el criterio decidido, pero al usuario le cuesta más. Queda en el guion de demo como caso a explicar.
- **El destino Centro casi nunca tiene ruta desde veredas o municipios**: las rutas están cargadas en el sentido del letrero (saliendo). Por lo visto en la lista de pares fallidos de `cobertura-busqueda.md`, es una de las causas principales del 22,4 % sin ruta (no se contó por causa).
- **Alaska** no está en `/api/places` (P0 no pudo ubicarla con confianza). Se puede buscar Pasca o Crucero La Angostura, pero no Alaska como punta.
- **De las 8 rutas que P2 tenía que regenerar, solo #26 y #11b (V26) y Pasca (V23) tienen trazado nuevo.** Las otras 6 conservan el trazado viejo de V16 porque no pasaron los controles de retroceso y contravía, y requieren trabajo de campo (memoria del plan, 2026-11-10).
