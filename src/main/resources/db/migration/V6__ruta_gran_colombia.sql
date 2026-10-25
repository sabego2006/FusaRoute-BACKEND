-- V6 — Ruta nueva: Gran Colombia.
--
-- Ruta real de Fusagasuga que no estaba en la semilla original de V2 (esa
-- solo traia 8 rutas documentadas por Santiago Bermudez el 2026-09-27).
-- Angelica Aranguren la agrega el 2026-10-22, verificada contra el letrero
-- fisico de la buseta, mismo metodo de verificacion que se uso para corregir
-- Maiz Amarillo en V5.
--
-- Letrero: 'Hospital, Gran Colombia, Escuela de Policia, Ebenezer'. Tarifa:
-- $2.600, urbana de precio unico fijo, igual que el resto de las rutas
-- urbanas de la ciudad (Decreto 16/2026, mismo valid_from que usa V2 para
-- esa tarifa citywide).
--
-- La ruta tiene DOS puntos de partida posibles segun quien la opere; el
-- equipo la documenta desde la Terminal de Transporte, el mismo punto de
-- arranque real que uso Maiz Amarillo en V5. 'Terminal de Transporte' NO
-- entra en route_neighborhoods porque no esta en el letrero -- es, igual que
-- en Maiz Amarillo, el punto donde arranca el trazado (path_geojson) sin ser
-- una parada nombrada. El trazado todavia no se ha dibujado: path_geojson
-- queda NULL hasta que exista el KML, mismo criterio que V2 uso para las
-- ocho rutas originales.
-- ===========================================================================

INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Gran Colombia', 'URBANA', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Hospital'),
               (1, 'Gran Colombia'),
               (2, 'Escuela de Policía'),
               (3, 'Ebenezer')) AS v(order_index, neighborhood_name)
 WHERE name = 'Gran Colombia';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE name = 'Gran Colombia';
