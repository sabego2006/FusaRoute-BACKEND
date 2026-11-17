#!/usr/bin/env bash
# Mediciones de SCRUM-192 (lectura concurrente del catalogo). Cada corrida deja en
# perf/jmeter/resultados-t04/<fecha-hora>-<plan>-<etiqueta>/ el .jtl, el comando exacto,
# el consumo del contenedor y actualiza resultados-t04/resumen.md.
#
#   perf/jmeter/run-t04.sh <plan> <etiqueta>
#
# Planes:
#   m-local  prod-e2 identico (30 usuarios, 15 s de rampa, 10 vueltas, GET /api/routes,
#            /api/routes/{id} y /api/places) contra el backend local limitado como Render
#            free (docker-compose.t04.yml: 0,1 CPU y 512 MB)
#   m-qr     25 usuarios, 5 s de rampa, 1 vuelta: /api/routes, /api/places y
#            /api/routes/search/offline (la rafaga del QR de la sustentacion)
#   m-base   1 usuario, 20 vueltas de las mismas 3 lecturas: el tiempo por peticion sin competencia
#   m-prod   prod-e2 identico contra PROD. UNA sola vez, con el cambio ya desplegado.
#
# La etiqueta identifica el codigo medido (main, opt, opt-palanca-b...).
#
# LOOPS=5 acorta m-local a 5 vueltas para descartar una palanca rapido; la corrida que cuenta es la de 10.
#
# Variables: JMETER (ejecutable de JMeter), HOST PORT PROTOCOL (por defecto el compose
# fusaroute-t04 en http://localhost:18080), CONTAINER (para docker stats).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN="${1:?falta el plan: m-local, m-qr o m-prod}"
TAG="${2:?falta la etiqueta del codigo medido (main, opt...)}"
JMETER="${JMETER:-jmeter}"
HOST="${HOST:-localhost}"
PORT="${PORT:-18080}"
PROTOCOL="${PROTOCOL:-http}"
CONTAINER="${CONTAINER:-fusaroute-t04-backend-1}"

case "$PLAN" in
  m-local) PROPS="-Jq.users=30 -Jq.rampup=15 -Jq.loops=${LOOPS:-10} -Jq.auth=false" ;;
  m-base)  PROPS="-Jq.users=1 -Jq.rampup=1 -Jq.loops=20 -Jq.auth=false" ;;
  m-qr)    PROPS="-Jqr.users=25 -Jqr.rampup=5 -Jqr.loops=1" ;;
  # Solo GET publicos, 30 usuarios como tope y sin busqueda (analisis.md, recomendacion 7).
  m-prod)  HOST=fusaroute-backend.onrender.com; PORT=443; PROTOCOL=https
           PROPS="-Jq.users=30 -Jq.rampup=15 -Jq.loops=10 -Jq.auth=false" ;;
  *) echo "Plan desconocido: $PLAN" >&2; exit 2 ;;
esac
if [[ "$PLAN" != m-prod && ( "$HOST" == *onrender.com* || "$HOST" == *supabase* ) ]]; then
  echo "Contra PROD solo se corre m-prod." >&2; exit 2
fi

BASE="$PROTOCOL://$HOST:$PORT"
OUT="$HERE/resultados-t04/$(date +%Y%m%d-%H%M%S)-$PLAN-$TAG"
DATA="$OUT/data"
mkdir -p "$DATA"
cp "$HERE/data-example/"*.csv "$DATA/"
# Los ids de ruta se leen del catalogo publico del servidor bajo prueba.
curl -fsS --max-time 120 "$BASE/api/routes" | grep -o '"id":[0-9]*,"name"' | grep -o '[0-9]*' > "$DATA/routes.csv"
[[ -s "$DATA/routes.csv" ]] || { echo "El catalogo no devolvio rutas." >&2; exit 2; }

# En Windows (Git Bash) JMeter es un proceso nativo: necesita rutas C:/..., no /c/...
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

STATS_PID=""
if [[ "$PLAN" != m-prod ]]; then
  ( echo "hora,contenedor,cpu,memoria"
    while true; do
      docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' "$CONTAINER" 2>/dev/null \
        | sed "s/^/$(date +%H:%M:%S),/" || true
      sleep 5
    done ) > "$OUT/docker-stats.csv" &
  STATS_PID=$!
fi

CMD=("$JMETER" -n -t "$(native "$HERE/fusaroute.jmx")"
  "-Jhost=$HOST" "-Jport=$PORT" "-Jprotocol=$PROTOCOL" "-Jdata=$(native "$DATA")" $PROPS
  -l "$(native "$OUT/resultado.jtl")" -j "$(native "$OUT/jmeter.log")")
{
  echo "# $(date '+%Y-%m-%d %H:%M:%S %z') · plan $PLAN · etiqueta $TAG"
  echo "# commit del repo: $(git -C "$HERE" rev-parse --short HEAD 2>/dev/null || echo desconocido)"
  echo "perf/jmeter/run-t04.sh $PLAN $TAG"
  echo "# que por dentro ejecuta:"
  echo "${CMD[*]}"
} > "$OUT/comando.txt"
echo "$PROPS" > "$OUT/propiedades.txt"

echo "== $PLAN ($TAG) -> $BASE  ($PROPS)"
"${CMD[@]}" | grep -E "summary =|end of run" || true

[[ -n "$STATS_PID" ]] && kill "$STATS_PID" 2>/dev/null || true
rm -rf "$DATA"
node "$(native "$HERE/scripts/summarize.mjs")" "$(native "$HERE/resultados-t04")" >/dev/null
echo "== resultado en $OUT"
