-- V3 — Ruta Fusagasuga - Pasca: recorrido completo y tabla de tarifas de Cootransfusa.
--
-- Migracion NUEVA en vez de una correccion de V2, porque V2 ya esta aplicada y
-- Flyway tiene su checksum guardado: editarla rompe el arranque. Es la regla
-- escrita en el CLAUDE.md de este repositorio.
--
-- FUENTE: cartel oficial de tarifas de COOTRANSFUSA (NIT 890.600.323),
-- "PASCA-FUSA VICEVERSA", vigente a partir del 16 de enero de 2025 segun
-- acuerdo de Junta Directiva del 14 de enero de 2025. Aportado por Santiago
-- Bermudez el 2026-09-27.
--
-- Cierra el hueco que V2 dejo anotado: el pasaje completo hasta Pasca ($4.300)
-- no lo publica el decreto municipal porque lo fija la empresa transportadora.
-- Con esto, RF-06 ya puede responder "cuanto cuesta ir a Pasca".
--
--
-- COMO SE DEDUJO EL ORDEN DEL RECORRIDO
--
-- El cartel trae las tarifas en los dos sentidos, y eso permite CONFIRMAR el
-- orden en vez de inferirlo: desde Fusagasuga los precios suben con la
-- distancia (Sauces 3.000, Alaska 3.300, La Capilla 3.600) y desde Pasca suben
-- en sentido contrario (La Capilla 3.000, Alaska 3.300, Sauces 3.600). Las dos
-- mitades se validan entre si. Es el unico recorrido de la semilla cuyo orden
-- no depende de la lectura de un mapa.
--
-- Lo que SIGUE sin confirmar: el orden relativo de Corregimiento, Hogar
-- Francisco y Clara y Escuela de los Sauces, que cuestan lo mismo ($3.000)
-- desde Fusagasuga. Se respeta el orden en que los lista el cartel; dentro de
-- ese tramo el orden es una suposicion.
--
--
-- DOS COSAS DEL CARTEL QUE NO SE SIEMBRAN
--
-- * Tarifa de estudiantes ($3.500, lunes a viernes de 6 AM a 4 PM): las
--   tarifas diferenciales estan declaradas FUERA DE ALCANCE este semestre en
--   la Actividad 3 y en el CLAUDE.md. El modelo no tiene donde ponerla sin
--   inventar una columna que ninguna historia pide.
-- * "Parada minima $3.000": es un piso de cobro, no un punto de bajada. No
--   tiene representacion en el modelo actual y ninguna historia lo pide.
--
--
-- DISCREPANCIA ABIERTA, dejada a la vista y no resuelta
--
-- Alaska aparece en las dos fuentes con precio distinto: $3.300 en este cartel
-- de Cootransfusa (2025-01-16) y $3.550 en la tabla veredal del Decreto
-- 16/2026 (2026-02-05), que es la que sembro V2. Lo mas probable es que sea la
-- misma tarifa un ano despues, y por eso conviven como dos filas con
-- valid_from distinto -- que es justo para lo que existe esa columna: un
-- cambio de tarifa es una fila nueva, no un UPDATE.
--
-- Pero hay una segunda lectura posible: que el servicio veredal del decreto y
-- la linea Pasca-Fusa de Cootransfusa sean dos servicios distintos que pasan
-- por el mismo punto. Si resulta ser asi, son dos rutas y no una, y esto hay
-- que rehacerlo. Queda por confirmar con la empresa.
--
-- Consecuencia practica mientras tanto: RF-06 debe mostrar SIEMPRE la tarifa
-- mas reciente por punto junto con su fecha de vigencia. Para Alaska eso es
-- $3.550 (2026); para Pasca, $4.300 con fecha 2025 -- y esa fecha visible es
-- justamente la advertencia de que el dato puede haber subido. No se extrapola
-- ningun precio: subir el de 2025 por un porcentaje seria inventar.


-- ---------------------------------------------------------------------------
-- Recorrido completo. Se reemplaza la lista de V2 (cinco puntos) por la de
-- nueve que se desprende del cartel. Se borra y se vuelve a insertar en vez de
-- renumerar, porque order_index es parte de la llave primaria y un UPDATE
-- parcial chocaria consigo mismo a mitad de camino.
--
-- Se corrige tambien 'Escuela de Sauces' a 'Escuela de los Sauces', que es
-- como la nombra la empresa en su propio cartel.
-- ---------------------------------------------------------------------------
DELETE FROM route_neighborhoods
 WHERE route_id = (SELECT id FROM routes WHERE name = 'Fusagasugá - Pasca');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Fusagasugá'),
               (1, 'Corregimiento'),
               (2, 'Hogar Francisco y Clara'),
               (3, 'Escuela de los Sauces'),
               (4, 'Alaska'),
               (5, 'Buenas Tardes'),
               (6, 'Crucero La Angostura'),
               (7, 'La Capilla'),
               (8, 'Pasca')) AS v(order_index, neighborhood_name)
 WHERE name = 'Fusagasugá - Pasca';


-- ---------------------------------------------------------------------------
-- Tarifas del cartel de Cootransfusa, sentido Fusagasuga -> Pasca.
--
-- No se tocan las filas que sembro V2: conviven por tener valid_from distinto,
-- y la restriccion uq_fares_route_point incluye valid_from justamente para
-- permitirlo.
--
-- El cartel agrupa "ALASKA - BUENAS TARDES - CRUCERO - ANGOSTURA" en un solo
-- renglon de $3.300. Se abre en tres filas, una por punto, porque el modelo
-- cobra por punto de bajada y agrupar obligaria a buscar por una cadena que
-- nadie va a escribir. Se toma 'Crucero La Angostura' como UN punto, no dos,
-- porque asi lo escribe el renglon del sentido contrario ("PASCA - CRUCERO LA
-- ANGOSTURA"), que es el que no deja lugar a duda.
--
-- Las filas del sentido Pasca -> Fusagasuga (PASCA - LA CAPILLA, PASCA -
-- SAUCES, etc.) NO se siembran aqui: el sentido contrario es una ruta
-- independiente en el modelo y todavia no existe como registro.
--
-- LIMITACION QUE ESTO DEJA A LA VISTA: order_index ordena las tarifas de menor
-- a mayor DENTRO de una ruta, pero esta ruta pasa a tener dos series de
-- vigencia (2025-01-16 y la de 2026-02-05 que sembro V2), y sus order_index se
-- solapan -- hay dos filas con order_index 0. Ordenar solo por order_index
-- mezcla las dos series. Quien implemente RF-06 debe agrupar primero por
-- valid_from y ordenar por 'ORDER BY valid_from DESC, order_index'. Si con el
-- tiempo se acumulan mas vigencias, vale reconsiderar si order_index deberia
-- ser unico por (route_id, valid_from) en vez de por ruta; hoy no se cambia
-- porque seria tocar V1, que ya esta aplicada.
-- ---------------------------------------------------------------------------
INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, v.amount, v.reference_point, v.order_index, DATE '2025-01-16'
  FROM routes,
       (VALUES (0, 'Corregimiento',           3000),
               (1, 'Hogar Francisco y Clara', 3000),
               (2, 'Escuela de los Sauces',   3000),
               (3, 'Alaska',                  3300),
               (4, 'Buenas Tardes',           3300),
               (5, 'Crucero La Angostura',    3300),
               (6, 'La Capilla',              3600),
               (7, 'Pasca',                   4300))
           AS v(order_index, reference_point, amount)
 WHERE name = 'Fusagasugá - Pasca';
