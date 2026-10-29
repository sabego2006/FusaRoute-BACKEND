# Contratos de referencia

Respuestas **reales** de APIs externas, guardadas tal cual las devolvio el
proveedor. Documentan la forma exacta que nuestros adaptadores saben parsear.

| Archivo | API | Par consultado | Adaptador que lo consume |
|---|---|---|---|
| `distance-matrix-fusagasuga-pasca.json` | Google Distance Matrix | Parque Principal de Fusagasuga (`4.3379,-74.3627`) → Parque Principal de Pasca (`4.3113,-74.2988`) | `GoogleMapsClient` / `DistanceMatrixResponse` |

## Por que se versionan

`GoogleMapsClientTest` usa `MockRestServiceServer` con respuestas escritas a
mano. Si Google cambia la forma de la respuesta, esos tests siguen en verde y
produccion falla. El contrato real es la referencia contra la cual comparar.

## Cuando regenerarlo

- Cambia la forma de la respuesta (campo nuevo, renombrado o ausente).
- Se cambia `GoogleMapsClient` para enviar parametros distintos (`mode`,
  `departure_time`, `units`...), porque la respuesta puede variar.
- Un test del adaptador falla por razones de forma, no de logica.

Para regenerarlo: `docs/setup/probe-distance-matrix.ps1` o `.sh` (ver
`docs/setup/google-cloud-setup.md`, seccion 7).

## Reglas

- El archivo es la respuesta cruda. No se edita a mano ni se reformatea mas alla
  de la indentacion.
- Antes de commitear, verificar que no contenga la clave API. La respuesta de
  Distance Matrix no la incluye; aun asi, buscar `AIza` en el archivo.
