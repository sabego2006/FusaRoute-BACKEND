#!/usr/bin/env bash
# Ejecuta un escenario de carga o estres de FusaRoute con JMeter en modo no-GUI
# y deja el .jtl, el dashboard HTML y (en local) el consumo de los contenedores.
#
#   perf/jmeter/run.sh <escenario> [carpeta-de-salida]
#
# Escenarios: e1 e2 e3 e4 | e5-50 e5-100 e5-200 e5-300 | e6-100 e6-200 e6-300 |
#             e7-50 e7-100 e7-150 | e8 | e9-1 e9-2 e9-3 e9-4 e9-recuperacion |
#             prod-e2 prod-e6 (solo lectura publica, contra PROD)
#
# Variables de entorno:
#   JMETER   ruta al ejecutable de JMeter (por defecto `jmeter` del PATH)
#   HOST PORT PROTOCOL   servidor bajo prueba (por defecto http://localhost:8080)
#   STATS=1  muestrea `docker stats` cada 5 s mientras corre (solo en local)
#   RESET=1  reinicia el backend y limpia bloqueos de las cuentas de prueba antes
#            de correr, para que cada nivel de estres parta del mismo estado
#   E8_DURATION  segundos del escenario 8 (por defecto 600)
#
# Cada corrida equivale a:
#   jmeter -n -t fusaroute.jmx -J<propiedades> -l resultado.jtl -e -o dashboard/
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIO="${1:?falta el escenario (ver la cabecera de este archivo)}"
OUT="${2:-$HERE/results/$(date +%Y%m%d-%H%M%S)-$SCENARIO}"
JMETER="${JMETER:-jmeter}"
HOST="${HOST:-localhost}"
PORT="${PORT:-8080}"
PROTOCOL="${PROTOCOL:-http}"
RUN_ID="$(date +%m%d%H%M%S)"

case "$SCENARIO" in
  # --- Carga ---
  e1)      PROPS="-Jlogin.users=50 -Jlogin.rampup=10 -Jlogin.loops=5" ;;
  e2)      PROPS="-Jq.users=80 -Jq.rampup=15 -Jq.loops=10" ;;
  e3)      PROPS="-Jr.users=40 -Jr.rampup=10 -Jr.loops=1 -Jrun=$RUN_ID" ;;
  e4)      PROPS="-Jf.users=20 -Jf.rampup=10 -Jf.loops=5" ;;
  # --- Estres ---
  e5-*)    PROPS="-Jlogin.users=${SCENARIO#e5-} -Jlogin.rampup=10 -Jlogin.loops=5" ;;
  e6-*)    PROPS="-Jq.users=${SCENARIO#e6-} -Jq.rampup=20 -Jq.loops=10" ;;
  e7-*)    PROPS="-Jc.users=${SCENARIO#e7-} -Jc.rampup=20 -Jc.loops=20" ;;
  e8)      PROPS="-Je8.users=50 -Je8.rampup=20 -Je8.duration=${E8_DURATION:-600} -Je8.think=1000" ;;
  # Falla controlada: login, consultas y CRUD a la vez; cada nivel multiplica la carga.
  e9-recuperacion) PROPS="-Jq.users=30 -Jq.rampup=5 -Jq.loops=5 -Jlogin.users=10 -Jlogin.rampup=5 -Jlogin.loops=2" ;;
  e9-*)    N="${SCENARIO#e9-}"
           PROPS="-Jlogin.users=$((100 * N)) -Jlogin.rampup=20 -Jlogin.loops=5 -Jq.users=$((300 * N)) -Jq.rampup=20 -Jq.loops=10 -Jc.users=$((150 * N)) -Jc.rampup=20 -Jc.loops=10" ;;
  # --- PROD: solo GET publicos, carga reducida. Sin cuentas, sin busqueda (consume
  #     cuota de Google) y sin escrituras. ---
  prod-e2) HOST=fusaroute-backend.onrender.com; PORT=443; PROTOCOL=https
           PROPS="-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false" ;;
  prod-e6) HOST=fusaroute-backend.onrender.com; PORT=443; PROTOCOL=https
           PROPS="-Jq.users=100 -Jq.rampup=20 -Jq.loops=5 -Jq.auth=false" ;;
  *) echo "Escenario desconocido: $SCENARIO" >&2; exit 2 ;;
esac

case "$SCENARIO" in
  prod-*) ;;
  *) if [[ "$HOST" == *onrender.com* || "$HOST" == *supabase* ]]; then
       echo "Este escenario escribe o usa cuentas de prueba: no se ejecuta contra PROD." >&2; exit 2
     fi
     [[ -s "$HERE/data/users.csv" ]] || { echo "Falta data/users.csv: correr scripts/seed-users.mjs primero." >&2; exit 2; } ;;
esac

mkdir -p "$OUT"
# En PROD no hay cuentas: los CSV de ejemplo bastan porque q.auth=false no los usa.
DATA="$HERE/data"
if [[ "$SCENARIO" == prod-* ]]; then
  DATA="$OUT/data"
  mkdir -p "$DATA"
  cp "$HERE/data-example/"*.csv "$DATA/"
  # Los ids de ruta de PROD se leen del propio catalogo publico (tambien sirve
  # para despertar el servicio si Render lo tenia dormido).
  curl -fsS --max-time 120 "$PROTOCOL://$HOST/api/routes" | grep -o '"id":[0-9]*,"name"' | grep -o '[0-9]*' > "$DATA/routes.csv"
fi

# RESET=1: cada corrida parte del mismo estado. Reinicia el backend (descarta la
# cola que haya dejado la corrida anterior), espera el /health y limpia los
# contadores de intentos fallidos y bloqueos de las cuentas de prueba.
if [[ "${RESET:-0}" == "1" && "$SCENARIO" != prod-* ]]; then
  ( cd "$HERE/../.." && docker compose restart backend >/dev/null 2>&1
    for _ in $(seq 1 60); do
      curl -fsS --max-time 3 "$PROTOCOL://$HOST:$PORT/health" 2>/dev/null | grep -q '"UP"' && break; sleep 3
    done
    docker compose exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "update fusaroute.users set failed_login_attempts = 0, locked_until = null where failed_login_attempts > 0 or locked_until is not null"' )
  sleep 10
fi

STATS_PID=""
if [[ "${STATS:-0}" == "1" ]]; then
  ( echo "hora,contenedor,cpu,memoria"
    while true; do
      docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' 2>/dev/null \
        | grep -E '^fusaroute-(backend|db)-1,' | sed "s/^/$(date +%H:%M:%S),/" || true
      sleep 5
    done ) > "$OUT/docker-stats.csv" &
  STATS_PID=$!
fi

echo "== $SCENARIO -> $PROTOCOL://$HOST:$PORT  ($PROPS)"
# shellcheck disable=SC2086
"$JMETER" -n -t "$HERE/fusaroute.jmx" \
  -Jhost="$HOST" -Jport="$PORT" -Jprotocol="$PROTOCOL" -Jdata="$DATA" $PROPS \
  -l "$OUT/resultado.jtl" -j "$OUT/jmeter.log" -e -o "$OUT/dashboard" \
  | grep -E "summary =|Err:.*Active: 0|end of run" || true

[[ -n "$STATS_PID" ]] && kill "$STATS_PID" 2>/dev/null || true
echo "$PROPS" > "$OUT/propiedades.txt"
echo "== resultado en $OUT"
