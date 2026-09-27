-- V2: semilla de rutas reales de Fusagasugá.
--
-- Fuentes: Inventum 2014 (inventario de rutas urbanas), Decreto 16/2026 de la
-- Alcaldía (tarifa urbana única de 2600, tarifa veredal Chinauta, tabla de
-- taxi vía Melgar/Arbeláez/Pasca), fotos y publicaciones de Tizumba, mapa
-- del Blog SENA (ene 2020). El detalle por ruta va en el "procedencia:" de
-- cada bloque del comentario original de SCRUM-161.
--
-- Todas las rutas se cargan solo en un sentido. La ida-y-vuelta son dos
-- registros independientes segun el dominio; el contrario entra en un
-- futuro insert, no en un cambio de esquema.
--
-- path_geojson queda NULL en toda la semilla. Los trazados salen a SCRUM-166.
--
-- valid_from = 2026-02-05 en todas las tarifas: fecha del Decreto 16/2026.

-- =============================================================================
-- URBANAS
-- =============================================================================

-- Ruta 100 (Llano Largo -> La Clarita). El tramo intermedio entre "Llano Largo"
-- y "Centro" no esta documentado, asi que los barrios se cargan seguidos sin
-- una posicion vacia intermedia.
INSERT INTO routes (name, code, fare_type, status)
VALUES ('Llano Largo – La Clarita', '100', 'URBAN', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Llano Largo – La Clarita') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Llano Verde',
    'Llano Largo',
    'Centro',
    'Cra. 1 Santander',
    'Pantano de Vargas',
    'La Clarita'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT id, 0, NULL, 2600, DATE '2026-02-05'
FROM routes WHERE name = 'Llano Largo – La Clarita';


-- Ruta Maiz Amarillo (Maiz Amarillo -> Centro). Orden de barrios sin confirmar.
INSERT INTO routes (name, fare_type, status)
VALUES ('Maíz Amarillo', 'URBAN', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Maíz Amarillo') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Ebenezer',
    'Maíz Amarillo',
    'Gran Colombia',
    'Centro'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT id, 0, NULL, 2600, DATE '2026-02-05'
FROM routes WHERE name = 'Maíz Amarillo';


-- Ruta Pablo Bello - Cedritos. Orden de barrios sin confirmar.
INSERT INTO routes (name, fare_type, status)
VALUES ('Pablo Bello – Cedritos', 'URBAN', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Pablo Bello – Cedritos') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Pablo Bello',
    'Centro',
    'Cedritos'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT id, 0, NULL, 2600, DATE '2026-02-05'
FROM routes WHERE name = 'Pablo Bello – Cedritos';


-- Ruta Camino Real - La Pampa (Camino Real -> Llano Grande).
INSERT INTO routes (name, fare_type, status)
VALUES ('Camino Real – La Pampa', 'URBAN', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Camino Real – La Pampa') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Camino Real',
    'Prados de Alta Gracia',
    'Calle 22',
    'Éxito',
    'Hospital',
    'Batallón',
    'La Pampa',
    'Llano Grande'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT id, 0, NULL, 2600, DATE '2026-02-05'
FROM routes WHERE name = 'Camino Real – La Pampa';


-- Ruta Villa Leny - SENA Quebrajacho. Sale del casco urbano, por eso 2700
-- (tarifa veredal Centro Agrotecnologico Quebrajacho, Decreto 16/2026).
INSERT INTO routes (name, fare_type, status)
VALUES ('Villa Leny – SENA Quebrajacho', 'URBAN', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Villa Leny – SENA Quebrajacho') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Villa Leny',
    'Éxito',
    'Puente El Águila',
    'Centro',
    'Villa Natalia',
    'Vía Quebrajacho',
    'SENA'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT id, 0, NULL, 2700, DATE '2026-02-05'
FROM routes WHERE name = 'Villa Leny – SENA Quebrajacho';


-- =============================================================================
-- INTERMUNICIPALES (tarifa por punto de referencia de bajada)
-- =============================================================================

-- Fusagasuga - Chinauta. Es veredal dentro del municipio, pero cobra por
-- punto de referencia, asi que el dominio lo trata como INTERMUNICIPAL.
INSERT INTO routes (name, fare_type, status)
VALUES ('Fusagasugá – Chinauta', 'INTERMUNICIPAL', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Fusagasugá – Chinauta') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Fusagasugá',
    'Jaibaná',
    'Colegio Chinauta',
    'Chinauta',
    'Pirámides',
    'Alto Canecas',
    'Inspección El Triunfo'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT r.id, ord - 1, ref, precio, DATE '2026-02-05'
FROM (SELECT id FROM routes WHERE name = 'Fusagasugá – Chinauta') r
CROSS JOIN LATERAL (VALUES
    ('Dentro de Chinauta',                 2600),
    ('Jaibaná',                            3150),
    ('Colegio Chinauta',                   5000),
    ('Chinauta (retorno antes del peaje)', 5000),
    ('Pirámides',                          6300),
    ('Alto Canecas',                       6950),
    ('Inspección El Triunfo',              9550)
) WITH ORDINALITY AS t(ref, precio, ord);


-- Fusagasuga - Arbelaez. El pasaje hasta Arbelaez cabecera NO esta publicado
-- en el Decreto 16/2026, asi que se cargan solo los tres tramos veredales
-- con precio. Cuando la Alcaldia publique el terminal, se agrega en Vx.
INSERT INTO routes (name, fare_type, status)
VALUES ('Fusagasugá – Arbeláez', 'INTERMUNICIPAL', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Fusagasugá – Arbeláez') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Fusagasugá',
    'El Cuja',
    'Escuela Kennedy',
    'El Placer / Espinalito',
    'Batallón Sumapaz',
    'Los Ríos',
    'Arbeláez'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT r.id, ord - 1, ref, precio, DATE '2026-02-05'
FROM (SELECT id FROM routes WHERE name = 'Fusagasugá – Arbeláez') r
CROSS JOIN LATERAL (VALUES
    ('Colegio Kennedy',   3450),
    ('Guayabal Batallón', 4550),
    ('Los Ríos',          5650)
) WITH ORDINALITY AS t(ref, precio, ord);


-- Fusagasuga - Pasca. Mismo caso: solo el tramo Alaska esta publicado.
INSERT INTO routes (name, fare_type, status)
VALUES ('Fusagasugá – Pasca', 'INTERMUNICIPAL', 'ACTIVE');

INSERT INTO route_neighborhoods (route_id, position, neighborhood_name)
SELECT r.id, ord - 1, n
FROM (SELECT id FROM routes WHERE name = 'Fusagasugá – Pasca') r
CROSS JOIN LATERAL unnest(ARRAY[
    'Fusagasugá',
    'Escuela de Sauces',
    'Alaska',
    'Buenas Tardes',
    'Pasca'
]) WITH ORDINALITY AS t(n, ord);

INSERT INTO fares (route_id, position, reference_point, price, valid_from)
SELECT id, 0, 'Alaska', 3550, DATE '2026-02-05'
FROM routes WHERE name = 'Fusagasugá – Pasca';
