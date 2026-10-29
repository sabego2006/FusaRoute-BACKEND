#!/usr/bin/env bash
# Primera llamada real a Distance Matrix (SCRUM-168).
# Replica los parametros que envia GoogleMapsClient: origins, destinations, key.
# Uso: export GOOGLE_MAPS_API_KEY="AIza..." && ./docs/setup/probe-distance-matrix.sh
set -euo pipefail

ORIGIN="${1:-4.3379,-74.3627}"       # Parque Principal de Fusagasuga
DESTINATION="${2:-4.3113,-74.2988}"  # Parque Principal de Pasca

if [ -z "${GOOGLE_MAPS_API_KEY:-}" ]; then
  echo "Falta la variable de entorno GOOGLE_MAPS_API_KEY." >&2
  exit 1
fi

# No se usa -v ni se imprime la URL: lleva la clave.
RESPONSE=$(curl -sS --fail-with-body \
  "https://maps.googleapis.com/maps/api/distancematrix/json?origins=${ORIGIN}&destinations=${DESTINATION}&key=${GOOGLE_MAPS_API_KEY}")

if command -v jq >/dev/null 2>&1; then
  echo "$RESPONSE" | jq .
  STATUS=$(echo "$RESPONSE" | jq -r '.status')
  ELEMENT=$(echo "$RESPONSE" | jq -r '.rows[0].elements[0].status // "N/A"')
else
  echo "$RESPONSE"
  STATUS=$(echo "$RESPONSE" | grep -o '"status" *: *"[A-Z_]*"' | tail -1 | grep -o '[A-Z_]*"$' | tr -d '"')
  ELEMENT="$STATUS"
fi

if [ "$STATUS" != "OK" ] || [ "$ELEMENT" != "OK" ]; then
  echo "Google respondio status=${STATUS} / element=${ELEMENT}. Ver google-cloud-setup.md, seccion Troubleshooting." >&2
  exit 2
fi
