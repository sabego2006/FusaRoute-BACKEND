# Google Cloud Setup — SCRUM-168 (spike)

Guía reproducible para dejar lista la infraestructura externa de Google Maps que
`GoogleMapsClient` consume. Lo ejecuta **Santiago** (dueño de la tarjeta y del
proyecto GCP). Angélica queda como editor del proyecto una vez creado.

**Lee esto antes de clicar nada.** Las decisiones ya están tomadas (ver el plan
en `~/.claude/plans/lee-claude-md-contexto-y-wise-noodle.md`); esta guía solo
las ejecuta.

---

## 0. Free tier y presupuestos (por qué la tarjeta no cobra)

Google Maps Platform da **USD 200 de crédito gratis cada mes** por cuenta de
facturación. Con el uso previsto del proyecto, eso equivale a:

| API | Precio aprox. | Llamadas gratis/mes |
|---|---|---|
| Distance Matrix | $5 / 1 000 elementos | ~40 000 |
| Geocoding | $5 / 1 000 requests | ~40 000 |
| Maps JavaScript (Dynamic Maps) | $7 / 1 000 cargas | ~28 500 |

**La tarjeta debe estar registrada** (si no, Google no sirve las APIs), **pero no
te cobran si no superas el crédito**. Los presupuestos de USD 1 y USD 5 que
creamos abajo son la red de seguridad por si un bug genera llamadas infinitas.

---

## 1. Crear el proyecto GCP

1. Abre https://console.cloud.google.com/ con tu cuenta Google.
2. Barra superior → selector de proyecto → **New Project**.
3. Nombre: `fusaroute`. Organization: deja la que te sugiera (si tu cuenta no
   está en una org, queda como "No organization").
4. Click **Create**. Espera unos 20 s.
5. Selector de proyecto superior → elige `fusaroute`. Verifica en la URL que el
   `project=fusaroute` esté activo antes de cualquier paso siguiente.

## 2. Vincular facturación

1. Menú lateral → **Billing**.
2. Si no tienes cuenta de facturación: **Create account** → nombre
   `fusaroute-billing`, país Colombia, tipo "Individual".
3. Agregar tarjeta: la **tuya** (Santiago). Google hace un cargo temporal
   verificativo de ~USD 1 que devuelven en 7-10 días hábiles.
4. **Link billing account** → selecciona `fusaroute-billing` → **Set account**.

## 3. Activar las tres APIs

Entra a **APIs & Services → Library** y busca y activa:

- **Distance Matrix API** → botón **Enable**.
- **Geocoding API** → botón **Enable**.
- **Maps JavaScript API** → botón **Enable**.

**No actives** Directions API ni Places API — no las usamos en este sprint y
subirían el riesgo de cuota.

## 4. Crear dos presupuestos con alertas

Menú lateral → **Billing → Budgets & alerts → Create Budget**.

### Presupuesto 1 — alerta temprana

- Name: `fusaroute-dev-usd1`
- Scope: billing account `fusaroute-billing`, projects → `fusaroute`,
  services → **All services**.
- Amount: Specified amount, **USD 1.00**.
- Actions → thresholds: **50 %, 90 %, 100 %** (checklist, no custom).
- Manage notifications → Email alerts to billing admins: **ON**. Agrega tu
  correo también como "Managed users" en la cuenta de facturación.
- **Create**.

### Presupuesto 2 — circuit breaker

- Name: `fusaroute-dev-usd5`
- Mismo scope.
- Amount: **USD 5.00**.
- Mismos thresholds (50/90/100 %).
- **Create**.

Si Google te muestra un presupuesto preexistente (por el crédito gratuito), no
lo borres — déjalo como presupuesto 3 informativo.

## 5. Crear las dos claves API

Menú lateral → **APIs & Services → Credentials**.

### 5.1 Clave backend

1. **Create credentials → API key**. Google genera `AIzaSy...`.
2. **Edit API key** (lápiz) en la fila nueva.
3. Name: `fusaroute-backend`.
4. Application restrictions: **None** (el backend llama server-to-server, no
   tiene referer HTTP).
5. API restrictions: **Restrict key** → en la lista marca únicamente
   **Distance Matrix API** y **Geocoding API** → **Save**.
6. Copia el valor completo de la clave. Lo pegas en `.env` del backend como
   `GOOGLE_MAPS_API_KEY=AIzaSy...`.

### 5.2 Clave frontend

1. **Create credentials → API key** otra vez.
2. Name: `fusaroute-frontend`.
3. Application restrictions: **HTTP referrers (web sites)**. Agrega estos
   patrones (uno por línea):

   ```
   http://localhost:4200/*
   http://localhost:*/*
   https://fusaroute.vercel.app/*
   ```

   Los dos primeros son para desarrollo; el tercero es el deploy real.
4. API restrictions: **Restrict key** → marca únicamente **Maps JavaScript API**
   (y **Geocoding API** si deciden que la barra de búsqueda haga fallback por
   geocodificación desde el browser; hoy la decisión es que el autocompletado
   es propio, así que Geocoding puede quedar fuera; dejar fuera cuesta cero y
   acota la superficie).
5. **Save**. Copia la clave. La pegas en `.env` del frontend como
   `GOOGLE_MAPS_API_KEY_FRONTEND=AIzaSy...`.

### 5.3 Verificación cruzada (recomendado, aún en consola)

En la fila de cada clave verás un ícono de ⚠️ si una restricción no está
guardada. Si está limpio, las dos claves quedaron listas.

## 6. Pegar las claves en los entornos

### Backend — local DEV

Abre `FusaRoute-BACKEND/.env` (no versionado). Agrega o actualiza:

```
GOOGLE_MAPS_API_KEY=AIzaSy...tu_clave_backend
```

Si todavía no tienes `.env` en el backend, cópialo desde `.env.example` primero.

### Backend — Render (producción)

1. https://dashboard.render.com/ → servicio `fusaroute-backend`.
2. **Environment** → **Add Environment Variable**.
3. Key: `GOOGLE_MAPS_API_KEY`. Value: la misma clave backend.
4. **Save Changes**. Render redespliega automáticamente (~2-3 min).
5. **No** la pegues en `render.yaml` — ese archivo sí se versiona.

### Frontend — local DEV

Abre `FusaRoute-FRONTEND/.env` (si Angular usa `.env`) o
`src/environments/environment.ts` según cómo esté organizado el frontend hoy.
Agrega:

```
GOOGLE_MAPS_API_KEY_FRONTEND=AIzaSy...tu_clave_frontend
```

### Frontend — Vercel

1. https://vercel.com/dashboard → proyecto `fusaroute-frontend` (o como se
   llame).
2. **Settings → Environment Variables**.
3. Key: `GOOGLE_MAPS_API_KEY_FRONTEND`. Value: la clave frontend. Scope:
   **Production + Preview + Development**.
4. **Save**. Luego **Deployments → ... → Redeploy** del último build para que
   tome la nueva env var.

## 7. Primera llamada real (smoke test)

En una terminal con la env var ya exportada:

**PowerShell** (Windows, por defecto en el equipo de Santiago):

```powershell
$env:GOOGLE_MAPS_API_KEY = "AIzaSy..."
./docs/setup/probe-distance-matrix.ps1
```

**bash** (Linux/Mac o Git Bash):

```bash
export GOOGLE_MAPS_API_KEY="AIzaSy..."
./docs/setup/probe-distance-matrix.sh
```

Resultado esperado: un JSON con `"status": "OK"`, un `rows[0].elements[0]` con
`status: "OK"`, `duration.value` entre ~1 200 y ~2 000 segundos (20-33 min) y
`distance.value` entre ~15 000 y ~25 000 metros.

**Si da `REQUEST_DENIED`**: la restricción de la clave no está bien, o la API
no está activada en el proyecto. Vuelve al paso 3 y 5.1.

**Si da `ZERO_RESULTS`**: para `driving` entre Fusa y Pasca no debería ocurrir;
si pasa, hay un typo en las coordenadas.

## 8. Guardar el contrato

Copia la respuesta JSON completa y pégala a Claude en el chat. Claude la
guardará como:

```
FusaRoute-BACKEND/docs/contracts/distance-matrix-fusagasuga-pasca.json
```

Ese archivo queda versionado en el repo como **contrato de referencia** —
documenta la forma exacta de la respuesta que `GoogleMapsClient` sabe parsear.

## 9. Verificar end-to-end desde el backend

```bash
# Local
cd FusaRoute-BACKEND
mvn spring-boot:run -Dspring-boot.run.profiles=dev
curl http://localhost:8080/health
curl http://localhost:8080/internal/maps/probe
```

El probe interno (SCRUM-127) hace dos llamadas al mismo par: la primera debe
reportar `cache: miss`, la segunda `cache: hit`, y la métrica
`fusaroute.maps.travel_time` debe sumar al menos un `ok`. En producción:

```bash
curl https://fusaroute-backend.onrender.com/actuator/health
# El probe interno está deshabilitado en PROD (MAPS_PROBE_ENABLED=false).
# Para validar en PROD, dispara una búsqueda real desde el frontend.
```

---

## Troubleshooting rápido

| Síntoma | Causa probable | Fix |
|---|---|---|
| `REQUEST_DENIED` + "API not enabled" | olvidaste activar Distance Matrix | paso 3 |
| `REQUEST_DENIED` + "This IP, site or mobile app..." | restricción de la clave backend mal puesta (quizás pusiste referer en vez de API) | paso 5.1 |
| `OVER_DAILY_LIMIT` | falta cuenta de facturación o la tarjeta falló | paso 2 |
| `API_KEY_HTTP_REFERRER_BLOCKED` en el frontend desde Vercel | el patrón de referer no incluye `fusaroute.vercel.app/*` | paso 5.2 |
| El mapa carga pero el autocompletado no funciona | olvidaste pegar la env var en Vercel y hacer redeploy | paso 6 → Vercel |
| Métricas `fusaroute.maps.travel_time` suman `error` siempre | la env var en Render no está, o se pegó mal | paso 6 → Render |

---

## Rotación de claves (fuera de alcance, documentado para después)

Si una clave se filtra:

1. Credentials → esa clave → **Regenerate** (crea un nuevo valor, mantiene las
   restricciones).
2. Actualiza env vars en Render o Vercel.
3. Confirma que el servicio redesplegó con la nueva clave.
4. **Delete** la clave vieja solo después de confirmar que ningún cliente usa
   la vieja (métrica `error` cae a cero).

Procedimiento completo cuando se abra el ticket de rotación.
