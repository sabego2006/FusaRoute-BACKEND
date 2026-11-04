-- Siembra de las 31 rutas con letrero (SCRUM-184).
--
-- Fuente de verdad: docs/investigacion/letreros-rutas.json (carpeta del curso),
-- version 2026-11-02, una entrada por ruta = un letrero = un sentido. Los barrios
-- que se conservan salen de docs/investigacion/letreros-svg/barrios-existentes.json.
-- Generada con docs/investigacion/scripts/gen-migraciones.js y revisada a mano.
--
-- Lo que hace, en numeros: 9 rutas heredan una fila que ya existe (se les cambia el
-- nombre al del letrero y se les pone sign_file, conservando el id) y 22 entran como
-- filas nuevas. El catalogo queda en 34 rutas: estas 31 mas las 3 que se quedan sin
-- letrero porque no hay foto que las respalde ('Villa Leny - SENA Quebrajacho',
-- 'Fusagasuga - Chinauta (Inspeccion El Triunfo)' y 'Fusagasuga - Pasca'), que esta
-- migracion no toca.
--
-- OJO, dicho explicito para que nadie lo descubra tarde:
--
-- 1. Las rutas que heredan van PRIMERO. Cada UPDATE libera el nombre viejo antes de que
--    cualquier INSERT pueda chocar con uq_routes_name. Hoy ningun nombre nuevo coincide
--    con uno viejo, pero el orden deja de depender de esa coincidencia.
--
-- 2. Heredar en vez de borrar e insertar es a proposito: conservando el id, las tarifas,
--    los sectores de congestion y el trazado que ya estaban ligados a esa fila siguen
--    ligados. Un DELETE + INSERT los perderia por el ON DELETE CASCADE.
--
-- 3. NO se toca ninguna tarifa existente. Las 8 rutas urbanas que heredan ya tienen su
--    fila de $2600 con fecha '2026-02-05' (V2, V6 y V12) y 'Fusagasuga - Arbelaez'
--    conserva su tabla por tramo. Solo se insertan las tarifas de las 22 rutas nuevas.
--
-- 4. Las dos rutas de Chinauta con letrero son INTERMUNICIPAL porque cobran POR PUNTO DE
--    BAJADA, que es lo que ese tipo significa en el dominio (no salen del municipio).
--    Copian la tabla por tramo que V2 sembro desde el Decreto 16/2026: BOQUERON la
--    completa, ESCUELA solo hasta Colegio Chinauta / Chinauta. La ruta de El Triunfo se
--    queda aparte, sin letrero y sin cambios.
--
-- 5. Cuatro rutas conservan los barrios que ya tienen en la base en vez de recibir los
--    del letrero ('KML_V12' / 'BD_EXISTENTE' en el JSON): Gaitan, San Antonio, Siboney y
--    Arbelaez. Para esas no hay DELETE de route_neighborhoods.
--
-- 6. Varios "barrios" son puntos de referencia y no barrios en sentido estricto
--    ('U. Hospital Exito', 'Antiguo Colsubsidio', 'Rotonda Calle 24B'). Se dejan tal cual
--    porque es como la gente describe el recorrido, que es lo que RF-05 muestra. Igual
--    criterio que V2 y V12.
--
-- PENDIENTE al cerrar esta migracion: 15 de las 31 rutas tienen el recorrido armado con
-- la ruta mas rapida de Google Maps, no dictado ni verificado en campo. Van marcadas
-- "VERIFICAR EN CAMPO" en el comentario de cada bloque y en
-- docs/investigacion/recorridos-para-kml.md. Dos lugares no se pudieron ubicar y por eso
-- el trazado no pasa por ellos aunque esten en la lista: 'Mesitas' y 'Barcelona'.
-- ===========================================================================


-- ===========================================================================
-- A. Rutas que heredan una fila existente (9)
-- ===========================================================================

-- #11b · Hospital - Batallón - La Pampa - Llano Grande  [HOSPITAL_BATALLON_LA_PAMPA_LLANO_GRANDE]
-- Hereda la fila 'Camino Real - La Pampa': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. 13 lugares dictados por Santiago.
UPDATE routes
   SET name = 'Hospital - Batallón - La Pampa - Llano Grande',
       sign_file = 'HOSPITAL_BATALLON_LA_PAMPA_LLANO_GRANDE',
       route_type = 'URBANA'
 WHERE name = 'Camino Real - La Pampa';

DELETE FROM route_neighborhoods
 WHERE route_id = (SELECT id FROM routes WHERE sign_file = 'HOSPITAL_BATALLON_LA_PAMPA_LLANO_GRANDE');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Terminal'),
               (1,  'Banco Caja Social'),
               (2,  'Panadería Filipo'),
               (3,  'Fiscalía'),
               (4,  'U. Hospital Éxito'),
               (5,  'Indio'),
               (6,  'Zona Rosa'),
               (7,  'Querencia'),
               (8,  'Batallón'),
               (9,  'Gran Surtidor'),
               (10, 'Villa Celeste'),
               (11, 'San Francisco'),
               (12, 'Llano Grande')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'HOSPITAL_BATALLON_LA_PAMPA_LLANO_GRANDE';


-- #15 · U. Hospital - Gran Colombia - Esc. Policía - C. Ebenezer  [U_HOSPITAL_GRAN_COLOMBIA_ESC_POLICIA_C_EBENEZER]
-- Hereda la fila 'Gran Colombia': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. 14 lugares dictados por Santiago.
UPDATE routes
   SET name = 'U. Hospital - Gran Colombia - Esc. Policía - C. Ebenezer',
       sign_file = 'U_HOSPITAL_GRAN_COLOMBIA_ESC_POLICIA_C_EBENEZER',
       route_type = 'URBANA'
 WHERE name = 'Gran Colombia';

DELETE FROM route_neighborhoods
 WHERE route_id = (SELECT id FROM routes WHERE sign_file = 'U_HOSPITAL_GRAN_COLOMBIA_ESC_POLICIA_C_EBENEZER');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Terminal'),
               (1,  'Banco Caja Social'),
               (2,  'Panadería Filipo'),
               (3,  'Fiscalía'),
               (4,  'U. Hospital Éxito'),
               (5,  'Indio'),
               (6,  'Zona Rosa'),
               (7,  'Querencia'),
               (8,  'Gran Colombia'),
               (9,  'Iglesia Gran Colombia'),
               (10, 'Escuela Policía'),
               (11, 'Ebenezer'),
               (12, 'Salón Comunal Ebenezer'),
               (13, 'Cambulos 3')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'U_HOSPITAL_GRAN_COLOMBIA_ESC_POLICIA_C_EBENEZER';


-- #16 · Centro - Colsubsidio - Cementerio - Gaitán - Galán  [CENTRO_COLSUBSIDIO_CEMENTERIO_GAITAN_GALAN]
-- Hereda la fila 'Gaitán': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. Conserva los barrios que ya tiene en la base.
UPDATE routes
   SET name = 'Centro - Colsubsidio - Cementerio - Gaitán - Galán',
       sign_file = 'CENTRO_COLSUBSIDIO_CEMENTERIO_GAITAN_GALAN',
       route_type = 'URBANA'
 WHERE name = 'Gaitán';


-- #18 · U. Hospital - Maíz Amarillo - Comfenalco - Ciudad Ebenezer  [U_HOSPITAL_MAIZ_AMARILLO_COMFENALCO_CIUDAD_EBENEZER]
-- Hereda la fila 'Maíz Amarillo': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. 14 lugares dictados por Santiago.
UPDATE routes
   SET name = 'U. Hospital - Maíz Amarillo - Comfenalco - Ciudad Ebenezer',
       sign_file = 'U_HOSPITAL_MAIZ_AMARILLO_COMFENALCO_CIUDAD_EBENEZER',
       route_type = 'URBANA'
 WHERE name = 'Maíz Amarillo';

DELETE FROM route_neighborhoods
 WHERE route_id = (SELECT id FROM routes WHERE sign_file = 'U_HOSPITAL_MAIZ_AMARILLO_COMFENALCO_CIUDAD_EBENEZER');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Terminal'),
               (1,  'Banco Caja Social'),
               (2,  'Panadería Filipo'),
               (3,  'Fiscalía'),
               (4,  'U. Hospital Éxito'),
               (5,  'Indio'),
               (6,  'Zona Rosa'),
               (7,  'Querencia'),
               (8,  'Batallón'),
               (9,  'Maíz Amarillo'),
               (10, 'Comfenalco'),
               (11, 'Ciudad Ebenezer'),
               (12, 'Salón Comunal Ebenezer'),
               (13, 'Cambulos 3')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'U_HOSPITAL_MAIZ_AMARILLO_COMFENALCO_CIUDAD_EBENEZER';


-- #21 · Centro - Cra. 1 Santander - Pantano de Vargas - La Clarita  [CENTRO_CRA_1_SANTANDER_PANTANO_DE_VARGAS_LA_CLARITA]
-- Hereda la fila 'Llano Largo - La Clarita ("100")': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
UPDATE routes
   SET name = 'Centro - Cra. 1 Santander - Pantano de Vargas - La Clarita',
       sign_file = 'CENTRO_CRA_1_SANTANDER_PANTANO_DE_VARGAS_LA_CLARITA',
       route_type = 'URBANA'
 WHERE name = 'Llano Largo - La Clarita ("100")';

DELETE FROM route_neighborhoods
 WHERE route_id = (SELECT id FROM routes WHERE sign_file = 'CENTRO_CRA_1_SANTANDER_PANTANO_DE_VARGAS_LA_CLARITA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Av. Las Palmas'),
               (1,  'Galería'),
               (2,  'Centro (Parque Principal)'),
               (3,  'Calle 7'),
               (4,  'Carrera 1 Santander'),
               (5,  'D1 Barrio Santander'),
               (6,  'Supermercado El Gran Surtidor'),
               (7,  'Paris Night Club'),
               (8,  'Pantano de Vargas'),
               (9,  'Academia Militar Pantano de Vargas'),
               (10, 'Jardín de Doña Bertha'),
               (11, 'La Clarita')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CENTRO_CRA_1_SANTANDER_PANTANO_DE_VARGAS_LA_CLARITA';


-- #24 · Centro - Colsubsidio - Progreso - San Antonio  [CENTRO_COLSUBSIDIO_PROGRESO_SAN_ANTONIO]
-- Hereda la fila 'San Antonio': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. Conserva los barrios que ya tiene en la base.
UPDATE routes
   SET name = 'Centro - Colsubsidio - Progreso - San Antonio',
       sign_file = 'CENTRO_COLSUBSIDIO_PROGRESO_SAN_ANTONIO',
       route_type = 'URBANA'
 WHERE name = 'San Antonio';


-- #25 · Arbeláez  [ARBELAEZ]
-- Hereda la fila 'Fusagasugá - Arbeláez': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. Conserva los barrios que ya tiene en la base.
UPDATE routes
   SET name = 'Arbeláez',
       sign_file = 'ARBELAEZ',
       route_type = 'INTERMUNICIPAL'
 WHERE name = 'Fusagasugá - Arbeláez';


-- #27 · Centro - U. Hospital - Colsubsidio - Cedritos  [CENTRO_U_HOSPITAL_COLSUBSIDIO_CEDRITOS]
-- Hereda la fila 'Pablo Bello - Cedritos': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. 10 lugares armados con Google Maps: VERIFICAR EN CAMPO.
UPDATE routes
   SET name = 'Centro - U. Hospital - Colsubsidio - Cedritos',
       sign_file = 'CENTRO_U_HOSPITAL_COLSUBSIDIO_CEDRITOS',
       route_type = 'URBANA'
 WHERE name = 'Pablo Bello - Cedritos';

DELETE FROM route_neighborhoods
 WHERE route_id = (SELECT id FROM routes WHERE sign_file = 'CENTRO_U_HOSPITAL_COLSUBSIDIO_CEDRITOS');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Av. Las Palmas'),
               (1, 'Centro'),
               (2, 'Carrera 6'),
               (3, 'Av. Manuel Humberto'),
               (4, 'U. Hospital Éxito'),
               (5, 'Calle 11'),
               (6, 'Antiguo Colsubsidio'),
               (7, 'Calle 4'),
               (8, 'Calle 2'),
               (9, 'Cedritos')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CENTRO_U_HOSPITAL_COLSUBSIDIO_CEDRITOS';


-- #28 · U. Hospital - Siboney - Macarena - Pablo Bello  [U_HOSPITAL_SIBONEY_MACARENA_PABLO_BELLO]
-- Hereda la fila 'Siboney': conserva el id y con el sus tarifas, sus
-- sectores de congestion y su trazado. Conserva los barrios que ya tiene en la base.
UPDATE routes
   SET name = 'U. Hospital - Siboney - Macarena - Pablo Bello',
       sign_file = 'U_HOSPITAL_SIBONEY_MACARENA_PABLO_BELLO',
       route_type = 'URBANA'
 WHERE name = 'Siboney';


-- ===========================================================================
-- B. Rutas nuevas (22)
-- ===========================================================================

-- #1 · Centro - Colsubsidio - Calle 3 - Olaya  [CENTRO_COLSUBSIDIO_CALLE_3_OLAYA]
-- Ruta nueva. URBANA. 12 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Centro - Colsubsidio - Calle 3 - Olaya', 'URBANA', 'ACTIVA', 'CENTRO_COLSUBSIDIO_CALLE_3_OLAYA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Ebenezer'),
               (1,  'Ara Ebenezer'),
               (2,  'Escuela Policía'),
               (3,  'Iglesia Gran Colombia'),
               (4,  'Indio'),
               (5,  'Manila'),
               (6,  'Puente Águila'),
               (7,  'Cra 7'),
               (8,  'Palmas'),
               (9,  'Antiguo Colsubsidio'),
               (10, 'Calle 3'),
               (11, 'Olaya')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CENTRO_COLSUBSIDIO_CALLE_3_OLAYA';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CENTRO_COLSUBSIDIO_CALLE_3_OLAYA';


-- #2 · Centro - Galería - Terminal  [CENTRO_GALERIA_TERMINAL]
-- Ruta nueva. URBANA. 12 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Centro - Galería - Terminal', 'URBANA', 'ACTIVA', 'CENTRO_GALERIA_TERMINAL');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Mirazul'),
               (1,  'Mirador Pampa'),
               (2,  'San Francisco'),
               (3,  'Avenida Calle 22'),
               (4,  'Indio'),
               (5,  'U. Hospital Éxito'),
               (6,  'Puente Águila'),
               (7,  'Cra 7'),
               (8,  'Avda. Palmas'),
               (9,  'Plaza de Mercado'),
               (10, 'CC Avenida'),
               (11, 'Terminal')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CENTRO_GALERIA_TERMINAL';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CENTRO_GALERIA_TERMINAL';


-- #3 · Nariño Pekín - Sta. María de los Ángeles - Altos de Pekín  [NARINO_PEKIN_STA_MARIA_DE_LOS_ANGELES_ALTOS_DE_PEKIN]
-- Ruta nueva. URBANA. 12 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Nariño Pekín - Sta. María de los Ángeles - Altos de Pekín', 'URBANA', 'ACTIVA', 'NARINO_PEKIN_STA_MARIA_DE_LOS_ANGELES_ALTOS_DE_PEKIN');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Mirazul'),
               (1,  'Mirador Pampa'),
               (2,  'San Francisco'),
               (3,  'Avenida Calle 22'),
               (4,  'Indio'),
               (5,  'U. Hospital Éxito'),
               (6,  'Puente Águila'),
               (7,  'Bomberos'),
               (8,  'Banco Agrario'),
               (9,  'Pekín'),
               (10, 'Sta. María de los Ángeles'),
               (11, 'Altos de Pekín')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'NARINO_PEKIN_STA_MARIA_DE_LOS_ANGELES_ALTOS_DE_PEKIN';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'NARINO_PEKIN_STA_MARIA_DE_LOS_ANGELES_ALTOS_DE_PEKIN';


-- #4 · Caney - Galería - Centro - Colsubsidio - El Edén  [CANEY_GALERIA_CENTRO_COLSUBSIDIO_EL_EDEN]
-- Ruta nueva. URBANA. 12 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Caney - Galería - Centro - Colsubsidio - El Edén', 'URBANA', 'ACTIVA', 'CANEY_GALERIA_CENTRO_COLSUBSIDIO_EL_EDEN');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Mirazul'),
               (1,  'San Francisco'),
               (2,  'Avenida Calle 22'),
               (3,  'Indio'),
               (4,  'U. Hospital Éxito'),
               (5,  'Clínica Belén'),
               (6,  'Caney'),
               (7,  'Santa Anita'),
               (8,  'Plaza de Mercado'),
               (9,  'Antiguo Colsubsidio'),
               (10, 'Barrio La Florida'),
               (11, 'Barrio El Edén')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CANEY_GALERIA_CENTRO_COLSUBSIDIO_EL_EDEN';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CANEY_GALERIA_CENTRO_COLSUBSIDIO_EL_EDEN';


-- #5 · La Palma - Colsubsidio - San Antonio - Santander  [LA_PALMA_COLSUBSIDIO_SAN_ANTONIO_SANTANDER]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('La Palma - Colsubsidio - San Antonio - Santander', 'URBANA', 'ACTIVA', 'LA_PALMA_COLSUBSIDIO_SAN_ANTONIO_SANTANDER');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Av. Las Palmas'),
               (1,  'Galería'),
               (2,  'Centro (Parque Principal)'),
               (3,  'Calle 4'),
               (4,  'Antiguo Colsubsidio'),
               (5,  'Diagonal 1'),
               (6,  'Carrera 1'),
               (7,  'San Antonio'),
               (8,  'Calle 3'),
               (9,  'Santander'),
               (10, 'Supermercado El Gran Surtidor'),
               (11, 'La Palma')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'LA_PALMA_COLSUBSIDIO_SAN_ANTONIO_SANTANDER';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'LA_PALMA_COLSUBSIDIO_SAN_ANTONIO_SANTANDER';


-- #6 · Chinauta - Fusa - Centro  [CHINAUTA_FUSA_CENTRO]
-- Ruta nueva. URBANA. 12 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Chinauta - Fusa - Centro', 'URBANA', 'ACTIVA', 'CHINAUTA_FUSA_CENTRO');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Chinauta'),
               (1,  'Hotel Madaura'),
               (2,  'Cutucumay'),
               (3,  'Entrada Pampa'),
               (4,  'Batallón'),
               (5,  'Querencia'),
               (6,  'Indio'),
               (7,  'U. Hospital Éxito'),
               (8,  'Potosí'),
               (9,  'Av. Palmas'),
               (10, 'Plaza de Mercado'),
               (11, 'Terminal')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CHINAUTA_FUSA_CENTRO';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CHINAUTA_FUSA_CENTRO';


-- #11 · Éxito - Calle 22 - Prados de Altagracia - Camino Real  [EXITO_CALLE_22_PRADOS_DE_ALTAGRACIA_CAMINO_REAL]
-- Ruta nueva. URBANA. 12 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Éxito - Calle 22 - Prados de Altagracia - Camino Real', 'URBANA', 'ACTIVA', 'EXITO_CALLE_22_PRADOS_DE_ALTAGRACIA_CAMINO_REAL');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Mirazul'),
               (1,  'Mirador Pampa'),
               (2,  'San Francisco'),
               (3,  'Avenida Calle 22'),
               (4,  'Indio'),
               (5,  'U. Hospital Éxito'),
               (6,  'Calle 22'),
               (7,  'D1 de la 22'),
               (8,  'Rotonda San Fernando'),
               (9,  'Prados de Altagracia'),
               (10, 'Comuneros'),
               (11, 'Camino Real')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'EXITO_CALLE_22_PRADOS_DE_ALTAGRACIA_CAMINO_REAL';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'EXITO_CALLE_22_PRADOS_DE_ALTAGRACIA_CAMINO_REAL';


-- #12 · Centro - Carrera 9 - Galería  [CENTRO_CARRERA_9_GALERIA]
-- Ruta nueva. URBANA. 12 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Centro - Carrera 9 - Galería', 'URBANA', 'ACTIVA', 'CENTRO_CARRERA_9_GALERIA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Mirazul'),
               (1,  'Mirador Pampa'),
               (2,  'San Francisco'),
               (3,  'Avenida Calle 22'),
               (4,  'Indio'),
               (5,  'U. Hospital Éxito'),
               (6,  'Puente Águila'),
               (7,  'Potosí'),
               (8,  'Carrera 9'),
               (9,  'Galería'),
               (10, 'Luxemburgo'),
               (11, 'Olaya')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CENTRO_CARRERA_9_GALERIA';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CENTRO_CARRERA_9_GALERIA';


-- #13 · Chinauta - Boquerón  [CHINAUTA_BOQUERON]
-- Ruta nueva. INTERMUNICIPAL. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
-- Tarifa: Copiar la tabla por tramo de "Fusagasugá - Chinauta (Inspección El Triunfo)" (Decreto 16/2026) completa. La ruta de El Triunfo se queda aparte sin letrero.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Chinauta - Boquerón', 'INTERMUNICIPAL', 'ACTIVA', 'CHINAUTA_BOQUERON');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Papa Picha'),
               (1,  'Puente Águila'),
               (2,  'U. Hospital Éxito'),
               (3,  'Indio'),
               (4,  'Querencia'),
               (5,  'Villa Celeste'),
               (6,  'Jaibaná'),
               (7,  'Chinauta (retorno antes del peaje)'),
               (8,  'Pirámides'),
               (9,  'Alto Canecas'),
               (10, 'Inspección El Triunfo'),
               (11, 'Boquerón')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CHINAUTA_BOQUERON';

-- order_index creciente de menor a mayor precio, como exige RF-06.
INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, v.amount, v.reference_point, v.order_index, DATE '2026-02-05'
  FROM routes,
       (VALUES (0, 'Jaibaná'                           , 3150),
               (1, 'Colegio Chinauta'                  , 5000),
               (2, 'Chinauta (retorno antes del peaje)', 5000),
               (3, 'Pirámides'                         , 6300),
               (4, 'Alto Canecas'                      , 6950),
               (5, 'Inspección El Triunfo'             , 9550))
           AS v(order_index, reference_point, amount)
 WHERE sign_file = 'CHINAUTA_BOQUERON';


-- #14 · Chinauta - Escuela  [CHINAUTA_ESCUELA]
-- Ruta nueva. INTERMUNICIPAL. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
-- Tarifa: Copiar la tabla por tramo de "Fusagasugá - Chinauta (Inspección El Triunfo)" (Decreto 16/2026) solo hasta Colegio Chinauta / Chinauta. La ruta de El Triunfo se queda aparte sin letrero.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Chinauta - Escuela', 'INTERMUNICIPAL', 'ACTIVA', 'CHINAUTA_ESCUELA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Papa Picha'),
               (1,  'Puente Águila'),
               (2,  'U. Hospital Éxito'),
               (3,  'Indio'),
               (4,  'Querencia'),
               (5,  'Batallón'),
               (6,  'Villa Celeste'),
               (7,  'Entrada Pampa'),
               (8,  'Jaibaná'),
               (9,  'Cutucumay'),
               (10, 'Colegio Chinauta'),
               (11, 'Chinauta Escuela')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CHINAUTA_ESCUELA';

-- order_index creciente de menor a mayor precio, como exige RF-06.
INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, v.amount, v.reference_point, v.order_index, DATE '2026-02-05'
  FROM routes,
       (VALUES (0, 'Jaibaná'                           , 3150),
               (1, 'Colegio Chinauta'                  , 5000),
               (2, 'Chinauta (retorno antes del peaje)', 5000))
           AS v(order_index, reference_point, amount)
 WHERE sign_file = 'CHINAUTA_ESCUELA';


-- #17 · Palacios - Trinidad - Guavio  [PALACIOS_TRINIDAD_GUAVIO]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Palacios - Trinidad - Guavio', 'URBANA', 'ACTIVA', 'PALACIOS_TRINIDAD_GUAVIO');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'U. Hospital Éxito'),
               (1,  'Indio'),
               (2,  'La Shell'),
               (3,  'Calle 22'),
               (4,  'La Querencia'),
               (5,  'Vía Arbeláez'),
               (6,  'Espinalito Alto'),
               (7,  'Palacios'),
               (8,  'La Trinidad'),
               (9,  'Guayabol'),
               (10, 'Bochica'),
               (11, 'Guavio')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'PALACIOS_TRINIDAD_GUAVIO';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'PALACIOS_TRINIDAD_GUAVIO';


-- #19 · Cra. 8 Hospital - Cooviprof - Villa Patricia  [CRA_8_HOSPITAL_COOVIPROF_VILLA_PATRICIA]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Cra. 8 Hospital - Cooviprof - Villa Patricia', 'URBANA', 'ACTIVA', 'CRA_8_HOSPITAL_COOVIPROF_VILLA_PATRICIA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Terminal'),
               (1,  'Av. Las Palmas'),
               (2,  'Centro'),
               (3,  'Carrera 8'),
               (4,  'Av. Manuel Humberto'),
               (5,  'U. Hospital Éxito'),
               (6,  'Indio'),
               (7,  'Rotonda Calle 24B'),
               (8,  'Avenida Calle 22'),
               (9,  'Carrera 41'),
               (10, 'Cooviprof'),
               (11, 'Villa Patricia')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CRA_8_HOSPITAL_COOVIPROF_VILLA_PATRICIA';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CRA_8_HOSPITAL_COOVIPROF_VILLA_PATRICIA';


-- #19c · Cra. 6 - Cll. 22 - Hospital - Cooviprof - Villa Patricia  [CRA_6_CLL_22_HOSPITAL_COOVIPROF_VILLA_PATRICIA]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Cra. 6 - Cll. 22 - Hospital - Cooviprof - Villa Patricia', 'URBANA', 'ACTIVA', 'CRA_6_CLL_22_HOSPITAL_COOVIPROF_VILLA_PATRICIA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Terminal'),
               (1,  'Av. Las Palmas'),
               (2,  'Centro'),
               (3,  'Carrera 6'),
               (4,  'Calle 22'),
               (5,  'U. Hospital Éxito'),
               (6,  'Indio'),
               (7,  'Rotonda Calle 24B'),
               (8,  'Avenida Calle 22'),
               (9,  'Carrera 41'),
               (10, 'Cooviprof'),
               (11, 'Villa Patricia')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CRA_6_CLL_22_HOSPITAL_COOVIPROF_VILLA_PATRICIA';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CRA_6_CLL_22_HOSPITAL_COOVIPROF_VILLA_PATRICIA';


-- #20 · U. Hospital - Batallón - Llano Largo  [U_HOSPITAL_BATALLON_LLANO_LARGO]
-- Ruta nueva. URBANA. 13 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('U. Hospital - Batallón - Llano Largo', 'URBANA', 'ACTIVA', 'U_HOSPITAL_BATALLON_LLANO_LARGO');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Terminal'),
               (1,  'Banco Caja Social'),
               (2,  'Panadería Filipo'),
               (3,  'Fiscalía'),
               (4,  'U. Hospital Éxito'),
               (5,  'Indio'),
               (6,  'Zona Rosa'),
               (7,  'Querencia'),
               (8,  'Batallón'),
               (9,  'Gran Surtidor'),
               (10, 'Villa Celeste'),
               (11, 'San Francisco'),
               (12, 'Llano Largo')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'U_HOSPITAL_BATALLON_LLANO_LARGO';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'U_HOSPITAL_BATALLON_LLANO_LARGO';


-- #22 · Cuja - Horizonte - Mesitas  [CUJA_HORIZONTE_MESITAS]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Cuja - Horizonte - Mesitas', 'URBANA', 'ACTIVA', 'CUJA_HORIZONTE_MESITAS');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Centro'),
               (1,  'Av. Las Palmas'),
               (2,  'U. Hospital Éxito'),
               (3,  'Indio'),
               (4,  'Calle 22'),
               (5,  'La Querencia'),
               (6,  'Vía Arbeláez'),
               (7,  'Río Cuja'),
               (8,  'Cuja'),
               (9,  'Espinalito Alto'),
               (10, 'Horizonte (Colegio Nuevo Horizonte)'),
               (11, 'Mesitas')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CUJA_HORIZONTE_MESITAS';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CUJA_HORIZONTE_MESITAS';


-- #23 · Hospital - La Shell - Cuja - Horizonte - Batallón  [HOSPITAL_LA_SHELL_CUJA_HORIZONTE_BATALLON]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Hospital - La Shell - Cuja - Horizonte - Batallón', 'URBANA', 'ACTIVA', 'HOSPITAL_LA_SHELL_CUJA_HORIZONTE_BATALLON');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Clínica Belén'),
               (1,  'U. Hospital Éxito'),
               (2,  'La Shell'),
               (3,  'Indio'),
               (4,  'Calle 22'),
               (5,  'La Querencia'),
               (6,  'Vía Arbeláez'),
               (7,  'Río Cuja'),
               (8,  'Cuja'),
               (9,  'Escuela Kennedy'),
               (10, 'Horizonte (Colegio Nuevo Horizonte)'),
               (11, 'Batallón Sumapaz')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'HOSPITAL_LA_SHELL_CUJA_HORIZONTE_BATALLON';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'HOSPITAL_LA_SHELL_CUJA_HORIZONTE_BATALLON';


-- #26 · Centro - Colsubsidio - Calle 4 - Cedritos - San Antonio  [CENTRO_COLSUBSIDIO_CALLE_4_CEDRITOS_SAN_ANTONIO]
-- Ruta nueva. URBANA. 11 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Centro - Colsubsidio - Calle 4 - Cedritos - San Antonio', 'URBANA', 'ACTIVA', 'CENTRO_COLSUBSIDIO_CALLE_4_CEDRITOS_SAN_ANTONIO');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Av. Las Palmas'),
               (1,  'Galería'),
               (2,  'Centro'),
               (3,  'Antiguo Colsubsidio'),
               (4,  'Calle 4'),
               (5,  'Carrera 4'),
               (6,  'Calle 2'),
               (7,  'Cedritos'),
               (8,  'Barrio Santander'),
               (9,  'Carrera 1'),
               (10, 'San Antonio')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CENTRO_COLSUBSIDIO_CALLE_4_CEDRITOS_SAN_ANTONIO';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CENTRO_COLSUBSIDIO_CALLE_4_CEDRITOS_SAN_ANTONIO';


-- #29 · U. Hospital - Cra. 9 Cll. 3 - Colsubsidio - Cementerio  [U_HOSPITAL_CRA_9_CLL_3_COLSUBSIDIO_CEMENTERIO]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('U. Hospital - Cra. 9 Cll. 3 - Colsubsidio - Cementerio', 'URBANA', 'ACTIVA', 'U_HOSPITAL_CRA_9_CLL_3_COLSUBSIDIO_CEMENTERIO');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'U. Hospital Éxito'),
               (1,  'Av. Manuel Humberto'),
               (2,  'Calle 11'),
               (3,  'Av. Las Palmas'),
               (4,  'Galería'),
               (5,  'Banco Popular'),
               (6,  'Centro'),
               (7,  'Carrera 9'),
               (8,  'Calle 3'),
               (9,  'Antiguo Colsubsidio'),
               (10, 'Carrera 11'),
               (11, 'Cementerio')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'U_HOSPITAL_CRA_9_CLL_3_COLSUBSIDIO_CEMENTERIO';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'U_HOSPITAL_CRA_9_CLL_3_COLSUBSIDIO_CEMENTERIO';


-- #30 · Santa Anita - Galería - Colsubsidio - Cementerio - Resguardos  [SANTA_ANITA_GALERIA_COLSUBSIDIO_CEMENTERIO_RESGUARDOS]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Santa Anita - Galería - Colsubsidio - Cementerio - Resguardos', 'URBANA', 'ACTIVA', 'SANTA_ANITA_GALERIA_COLSUBSIDIO_CEMENTERIO_RESGUARDOS');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Fiscalía'),
               (1,  'Clínica Belén'),
               (2,  'Av. Las Palmas'),
               (3,  'Centro'),
               (4,  'Galería'),
               (5,  'Santa Anita (Calle 17)'),
               (6,  'Carrera 17'),
               (7,  'Antiguo Colsubsidio'),
               (8,  'Carrera 11'),
               (9,  'Cementerio'),
               (10, 'Calle 1 Norte'),
               (11, 'Resguardos')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'SANTA_ANITA_GALERIA_COLSUBSIDIO_CEMENTERIO_RESGUARDOS';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'SANTA_ANITA_GALERIA_COLSUBSIDIO_CEMENTERIO_RESGUARDOS';


-- #31 · Carrera 6 - Fusacatán - San Fernando - Bosques de Oriente - Lusitania  [CARRERA_6_FUSACATAN_SAN_FERNANDO_BOSQUES_DE_ORIENTE_LUSITANIA]
-- Ruta nueva. URBANA. 11 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Carrera 6 - Fusacatán - San Fernando - Bosques de Oriente - Lusitania', 'URBANA', 'ACTIVA', 'CARRERA_6_FUSACATAN_SAN_FERNANDO_BOSQUES_DE_ORIENTE_LUSITANIA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Av. Las Palmas'),
               (1,  'Centro (Parque Principal)'),
               (2,  'Carrera 6'),
               (3,  'Calle 11'),
               (4,  'Los Robles'),
               (5,  'Fusacatán'),
               (6,  'San Fernando'),
               (7,  'San Fernando Centro Comercial'),
               (8,  'Bosques de Oriente'),
               (9,  'Calle 21'),
               (10, 'Lusitania')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CARRERA_6_FUSACATAN_SAN_FERNANDO_BOSQUES_DE_ORIENTE_LUSITANIA';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CARRERA_6_FUSACATAN_SAN_FERNANDO_BOSQUES_DE_ORIENTE_LUSITANIA';


-- #32 · Horizonte - Palacios - Trinidad - Barcelona  [HORIZONTE_PALACIOS_TRINIDAD_BARCELONA]
-- Ruta nueva. URBANA. 12 lugares armados con Google Maps: VERIFICAR EN CAMPO.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Horizonte - Palacios - Trinidad - Barcelona', 'URBANA', 'ACTIVA', 'HORIZONTE_PALACIOS_TRINIDAD_BARCELONA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Clínica Belén'),
               (1,  'U. Hospital Éxito'),
               (2,  'Nueva EPS'),
               (3,  'La Querencia'),
               (4,  'Calle 22'),
               (5,  'Vía Arbeláez'),
               (6,  'Río Cuja'),
               (7,  'Horizonte (Colegio Nuevo Horizonte)'),
               (8,  'Palacios'),
               (9,  'La Trinidad'),
               (10, 'Guayabol'),
               (11, 'Barcelona')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'HORIZONTE_PALACIOS_TRINIDAD_BARCELONA';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'HORIZONTE_PALACIOS_TRINIDAD_BARCELONA';


-- #34 · Centro Cra. 6 - Galería - Mandalay - Villa Natalia  [CENTRO_CRA_6_GALERIA_MANDALAY_VILLA_NATALIA]
-- Ruta nueva. URBANA. 13 lugares dictados por Santiago.
INSERT INTO routes (name, route_type, status, sign_file) VALUES
    ('Centro Cra. 6 - Galería - Mandalay - Villa Natalia', 'URBANA', 'ACTIVA', 'CENTRO_CRA_6_GALERIA_MANDALAY_VILLA_NATALIA');

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0,  'Mirazul'),
               (1,  'Mirador Pampa'),
               (2,  'San Francisco'),
               (3,  'Avenida Calle 22'),
               (4,  'Indio'),
               (5,  'U. Hospital Éxito'),
               (6,  'Puente Águila'),
               (7,  'Cra 7'),
               (8,  'Avda. Palmas'),
               (9,  'Plaza de Mercado'),
               (10, 'CC Avenida'),
               (11, 'Mandalay'),
               (12, 'Villa Natalia')) AS v(order_index, neighborhood_name)
 WHERE sign_file = 'CENTRO_CRA_6_GALERIA_MANDALAY_VILLA_NATALIA';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE sign_file = 'CENTRO_CRA_6_GALERIA_MANDALAY_VILLA_NATALIA';


-- ===========================================================================
-- Comprobacion final: si la siembra no cuadra, la migracion falla y no se aplica
-- a medias. Flyway corre cada migracion en una transaccion, asi que un EXCEPTION
-- aqui deja la base como estaba.
-- ===========================================================================
DO $$
DECLARE
    con_letrero INTEGER;
    activas     INTEGER;
BEGIN
    SELECT count(*) INTO con_letrero FROM routes WHERE sign_file IS NOT NULL;
    IF con_letrero <> 31 THEN
        RAISE EXCEPTION 'Se esperaban 31 rutas con letrero y quedaron %', con_letrero;
    END IF;

    SELECT count(*) INTO activas FROM routes WHERE status = 'ACTIVA';
    IF activas <> 34 THEN
        RAISE EXCEPTION 'Se esperaban 34 rutas activas en el catalogo y quedaron %', activas;
    END IF;
END $$;
