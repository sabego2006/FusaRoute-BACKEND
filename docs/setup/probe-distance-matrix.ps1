# Primera llamada real a Distance Matrix (SCRUM-168).
# Replica los parametros que envia GoogleMapsClient: origins, destinations, key.
# Uso: $env:GOOGLE_MAPS_API_KEY = "AIza..." ; ./docs/setup/probe-distance-matrix.ps1

param(
    [string]$Origin = "4.3379,-74.3627",      # Parque Principal de Fusagasuga
    [string]$Destination = "4.3113,-74.2988"  # Parque Principal de Pasca
)

if (-not $env:GOOGLE_MAPS_API_KEY) {
    Write-Error "Falta la variable de entorno GOOGLE_MAPS_API_KEY."
    exit 1
}

$base = "https://maps.googleapis.com/maps/api/distancematrix/json"
$uri = "${base}?origins=$Origin&destinations=$Destination&key=$($env:GOOGLE_MAPS_API_KEY)"

try {
    $response = Invoke-RestMethod -Uri $uri -Method Get
} catch {
    # No se imprime $uri: lleva la clave.
    Write-Error "Fallo la llamada HTTP: $($_.Exception.Message)"
    exit 1
}

$response | ConvertTo-Json -Depth 10

if ($response.status -ne "OK" -or $response.rows[0].elements[0].status -ne "OK") {
    Write-Error "Google respondio status=$($response.status) / element=$($response.rows[0].elements[0].status). Ver google-cloud-setup.md, seccion Troubleshooting."
    exit 2
}
