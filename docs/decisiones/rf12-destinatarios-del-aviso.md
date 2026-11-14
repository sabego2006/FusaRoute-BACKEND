# RF-12: a quién le llega el aviso de ruta suspendida

> **Estado: pendiente de aprobación de Angélica (Product Owner).** Es una decisión de producto que el backend ya implementa con esta definición; si Angélica la cambia, se ajusta el caso de uso `GetUserNotificationsService`, no el contrato HTTP.

## El problema

La historia de RF-12 pide avisar (in-app, no push) a (a) los usuarios que tienen la ruta como **favorita** y (b) los que la tienen en **≥ 3 de sus últimas 6 búsquedas guardadas**. Pero los datos no lo permiten literalmente:

| Tabla | Qué guarda | ¿Tiene `route_id`? |
|---|---|---|
| `favorite_destination` | el **texto** del destino favorito (un barrio o lugar) | no |
| `search_history` | **texto** de origen y destino de las últimas 6 búsquedas | no |
| `ride_history` | viajes confirmados con «Ya me subí» (tope 10 por usuario) | **sí** |

Una búsqueda guardada es una pregunta; la ruta que se le devolvió no queda anotada. No se inventa un `route_id` para favoritos ni búsquedas.

## Definición implementada

- **`FAVORITE`:** el usuario tiene un destino favorito y la ruta suspendida **sirve ese destino**. «Sirve» es cualquiera de:
  1. el favorito coincide con un barrio del recorrido de la ruta (`route_neighborhoods`), con la regla de coincidencia del buscador (`PlaceName.normalize` + `Route.indexOfPlace`: sin tildes, mayúsculas ni puntos; igualdad exacta antes que «contenido en»);
  2. el favorito corresponde a un lugar canónico (`places`) —por nombre o alias, con la misma normalización— y ese lugar queda a **≤ 800 m en línea recta del trazado** de la ruta (el mismo umbral de caminata que usa la búsqueda y el `routeCount` de `GET /api/places`).

  La segunda forma reutiliza la geometría de la búsqueda en vez de depender de que el nombre del lugar aparezca escrito como barrio: los barrios se cargaron a mano y el favorito se elige del selector de lugares.
- **`FREQUENT`:** la ruta aparece en **≥ 3 de los últimos 6** registros de `ride_history` del usuario (viajes que confirmó, más recientes primero). Es el sustituto de «búsquedas guardadas», porque `ride_history` es lo único que sabe en qué ruta se subió.

Una suspensión genera **como máximo un aviso por usuario**: si cumple los dos motivos sale `FAVORITE`.

## Consecuencias que Angélica debe conocer

1. **`FREQUENT` mide viajes, no búsquedas.** Un usuario que busca mucho una ruta pero nunca toca «Ya me subí» no recibe aviso; uno que se sube siempre sí.
2. **`FAVORITE` es por destino, no por ruta.** Un favorito como «Centro» alcanza a todas las rutas que sirven el Centro, no solo a la que el usuario usa; avisa de más antes que de menos. Un usuario sin favorito no recibe `FAVORITE`.
3. El aviso se **calcula al leer** (`GET /api/users/me/notifications`), solo para suspensiones vigentes en ese momento; no hay tabla de notificaciones ni «leído» en el servidor.
4. Alternativas si no le sirve esta definición: (a) guardar el `route_id` ganador con cada búsqueda y cada favorito (migración nueva y cambio en esos casos de uso: solo avisaría a quien busque *después*); (b) avisar a todos los usuarios con sesión (ruidoso).

## Decisión pedida

¿Aprueba esta definición de `FAVORITE` y `FREQUENT` para el Sprint actual? Queda sin firmar hasta que responda en la revisión del PR de SCRUM-25.
