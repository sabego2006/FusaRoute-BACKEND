-- V5 — Maiz Amarillo: barrios reales (letrero de la buseta) y trazado GeoJSON.
--
-- Migracion NUEVA en vez de tocar V2 (ya aplicada, con checksum guardado por
-- Flyway) o V1 (crea la tabla). Dos cosas distintas en un mismo archivo porque
-- las dos nacen de la misma verificacion de campo.
--
--
-- 1. BARRIOS — por que cambian
--
-- V2 sembro 'Ebenezer, Maiz Amarillo, Gran Colombia, Centro' marcado como
-- "ORDEN SIN CONFIRMAR" y "sentido sin confirmar". Angelica Aranguren verifico
-- el letrero fisico de la buseta de Maiz Amarillo el 2026-10-21 y no coincide:
-- el letrero lista 'Hospital, Maiz Amarillo, Comfenalco, Ciudad Ebenezer'.
-- 'Gran Colombia' y 'Centro' no aparecen en el letrero y se retiran.
--
-- 'Comfenalco' es un conjunto residencial (punto de referencia, igual que
-- 'Exito' u 'Hospital' en otras rutas de esta semilla), no un barrio formal
-- — asi se deja sembrado, con el mismo criterio que ya usa route_neighborhoods
-- para nombres que son sitio y no barrio.
--
-- HUECO dejado a proposito, mismo patron que 'Llano Largo - La Clarita' en
-- V2: el recorrido trazado en Google My Maps (ver mas abajo) nunca llego a
-- marcar un punto para Comfenalco ni a cerrar el tramo final hasta el pin de
-- Ciudad Ebenezer — se decidio no completarlo por ahora. Se deja el hueco en
-- order_index 2 (Comfenalco) en vez de renumerar, para que la propia tabla
-- registre que ahi falta el dato, igual que se hizo con La Clarita.
--
-- 'Hospital' se siembra como 'Hospital San Rafael', que es el nombre con el
-- que Angelica lo marco en el mapa — mas especifico que el 'Hospital' generico
-- del letrero, y es el nombre que el usuario va a reconocer en el catalogo.
--
--
-- 2. TRAZADO — de donde sale
--
-- Dibujado en Google My Maps con "Anadir indicaciones de conduccion" y
-- corregido a mano contra el recorrido real, mismo metodo acordado con
-- Santiago Bermudez para La Clarita. Exportado como KMZ el 2026-10-22
-- ('Maiz Amarillo.kmz'), con el recorrido partido en tres indicaciones
-- consecutivas (Linea 31 / Linea 41 / Linea 51) que se verificaron
-- coordenada por coordenada: el ultimo punto de cada tramo es exactamente
-- el primero del siguiente, sin salto ni superposicion.
--
-- El trazado SI tiene el mismo hueco que los barrios: el ultimo punto queda
-- a unos 300 m del pin de Ciudad Ebenezer, sin llegar a tocarlo. No se
-- extiende a mano para que coincida "a la fuerza" — el dato se deja corto y
-- documentado, en vez de inventar el tramo que falta.
-- ===========================================================================

DELETE FROM route_neighborhoods
 WHERE route_id = (SELECT id FROM routes WHERE name = 'Maíz Amarillo');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Hospital San Rafael'),
               (1, 'Maíz Amarillo'),
               -- index 2 ausente a proposito: falta el punto de Comfenalco.
               (3, 'Ciudad Ebenezer')) AS v(order_index, neighborhood_name)
 WHERE name = 'Maíz Amarillo';

UPDATE routes
   SET path_geojson = '{
         "type": "LineString",
         "coordinates": [
           [-74.3776432, 4.3461628], [-74.3762441, 4.3437836], [-74.3766904, 4.343621],
           [-74.3764587, 4.3432872], [-74.3760467, 4.3432444], [-74.3755918, 4.3434327],
           [-74.3722873, 4.343407],  [-74.3688369, 4.3421147], [-74.3677748, 4.3415241],
           [-74.3667534, 4.3411647], [-74.3652084, 4.341276],  [-74.3639124, 4.341875],
           [-74.3631828, 4.3422259], [-74.3631742, 4.3411647], [-74.3631056, 4.3405742],
           [-74.3631056, 4.3403602], [-74.3627537, 4.3399152], [-74.3637502, 4.3391926],
           [-74.3658865, 4.3363805], [-74.366144,  4.3361152], [-74.3693293, 4.3339831],
           [-74.3703282, 4.3332887], [-74.370676,  4.3332187], [-74.3749288, 4.3318402],
           [-74.377023,  4.3316091], [-74.3772977, 4.3319857], [-74.377641,  4.3320371],
           [-74.3778556, 4.3316434], [-74.3778813, 4.331438],  [-74.3787124, 4.3306922],
           [-74.3791302, 4.3302804], [-74.3793705, 4.3300322], [-74.3802417, 4.3300635],
           [-74.3895574, 4.3281504], [-74.3896396, 4.3281588], [-74.3923648, 4.3275897],
           [-74.3952862, 4.3267638], [-74.3958312, 4.3266439], [-74.4005155, 4.3266825],
           [-74.4004842, 4.3268214], [-74.3998577, 4.3280752], [-74.3990637, 4.3297441],
           [-74.3980702, 4.331811],  [-74.3980488, 4.3319351], [-74.3970574, 4.3319351],
           [-74.3963751, 4.3318025], [-74.3951639, 4.3315897], [-74.3940534, 4.3313018],
           [-74.3936886, 4.3321748], [-74.3948645, 4.3325085], [-74.3949331, 4.3322347],
           [-74.3972978, 4.3329087]
         ]
       }'::jsonb
 WHERE name = 'Maíz Amarillo';
