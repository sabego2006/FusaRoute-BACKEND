-- V2 — Semilla de rutas REALES de Fusagasuga.
--
-- Ocho rutas recopiladas por Santiago Bermudez el 2026-09-27 a partir de fuentes
-- documentales, no de datos inventados. La procedencia de cada ruta va anotada
-- sobre su INSERT: es lo que permite corregir el dato cuando cambie y lo que
-- hace defendible la semilla ante el Comite de Arquitectura.
--
-- Los nombres de barrios y puntos van CON TILDE: son dato que ve el usuario. El
-- archivo es UTF-8 y Flyway lo lee como UTF-8. Los comentarios van sin tilde,
-- igual que el resto de los comentarios del repositorio.
--
-- Fuentes citadas, en corto:
--   * Inventum 2014 — inventario de rutas publicado en la revista Inventum.
--   * Tizumba — publicaciones de Facebook con foto de la placa y mapa oficial.
--   * Blog SENA ene-2020 — mapa de acceso al Centro Agrotecnologico Quebrajacho.
--   * Decreto 16/2026 — decreto de tarifas de la Alcaldia de Fusagasuga,
--     vigente desde el 2026-02-05. Es la fuente de TODAS las tarifas de aqui.
--
--
-- LO QUE ESTA SEMILLA NO TIENE, dicho explicito para que nadie lo descubra tarde:
--
-- 1. path_geojson va NULL en las ocho. Los trazados no existen todavia y se
--    levantan en SCRUM-166. Consecuencia concreta: el mapa del catalogo
--    (SCRUM-154) y el modo offline (SCRUM-153) no tienen con que trabajar. La
--    busqueda por barrio si funciona, porque filtra por nombre y no por
--    geometria.
--
-- 2. status = 'ACTIVA' en las ocho, SIN verificacion de campo. Nadie confirmo
--    que las ocho esten operando hoy. Se asume activa porque es el estado
--    normal y porque marcarlas suspendidas seria una afirmacion mas fuerte y
--    menos sostenible. Si una resulta suspendida, es un UPDATE de una linea.
--
-- 3. El ORDEN de los barrios esta confirmado solo en cuatro rutas. En
--    'Maiz Amarillo' y 'Pablo Bello - Cedritos' el orden viene del inventario
--    sin confirmar, y en 'Camino Real - La Pampa' esta inferido del mapa. Cada
--    una lo dice sobre su INSERT. Importa porque RF-05 muestra justamente los
--    barrios EN ORDEN DE RECORRIDO: un orden equivocado es una respuesta
--    equivocada, no un detalle cosmetico.
--
-- 4. Dos rutas no tienen la tarifa de su destino final: 'Fusagasuga - Arbelaez'
--    y 'Fusagasuga - Pasca'. El Decreto 16/2026 publica los tramos veredales
--    pero no el pasaje intermunicipal completo, que lo fija la empresa
--    transportadora. Consecuencia: RF-06 no puede responder "cuanto cuesta ir a
--    Pasca", que es justo la pregunta que haria un usuario. Se siembran igual
--    porque la ruta y sus barrios si sirven para buscar, pero es un hueco real.
--
-- 5. La lista de barrios de 'Llano Largo - La Clarita' tiene un tramo sin datos
--    entre Llano Largo y Centro. Se dejo un HUECO EN order_index (no hay index
--    2) para que la propia tabla registre que ahi falta informacion, en vez de
--    presentar una lista contigua que parezca completa.
--
--
-- Nota sobre route_type: 'INTERMUNICIPAL' se usa aqui con el significado que
-- tiene en el dominio — "cobra por punto de referencia de bajada" — y no en
-- sentido administrativo. La ruta de Chinauta es veredal (no sale del
-- municipio) pero cobra por punto, asi que le corresponde ese tipo. Las
-- urbanas son las de precio unico fijo.
--
-- Nota sobre neighborhood_name: varias entradas son puntos de referencia
-- (Exito, Hospital, Batallon, Puente El Aguila, Cra. 1 Santander) y no barrios
-- en sentido estricto. Se dejan tal cual porque es como la gente describe el
-- recorrido, que es exactamente lo que RF-05 quiere mostrar. El nombre de la
-- columna se queda corto frente al dato; el dato no esta mal.


-- ===========================================================================
-- 1. Llano Largo - La Clarita  ("100" en la placa)
-- ---------------------------------------------------------------------------
-- Procedencia: nombre y extremos del inventario Inventum 2014; el tramo
-- Centro->La Clarita y el numero "100" de una foto de la placa publicada por
-- Tizumba el 30-dic-2022. Tarifa: Decreto 16/2026.
-- OJO: falta el tramo entre Llano Largo y Centro — de ahi el hueco en
-- order_index. La lista NO es el recorrido completo.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Llano Largo - La Clarita ("100")', 'URBANA', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Llano Verde'),
               (1, 'Llano Largo'),
               -- index 2 ausente a proposito: tramo sin datos.
               (3, 'Centro'),
               (4, 'Cra. 1 Santander'),
               (5, 'Pantano de Vargas'),
               (6, 'La Clarita')) AS v(order_index, neighborhood_name)
 WHERE name = 'Llano Largo - La Clarita ("100")';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE name = 'Llano Largo - La Clarita ("100")';


-- ===========================================================================
-- 2. Maiz Amarillo
-- ---------------------------------------------------------------------------
-- Procedencia: barrios del inventario Inventum 2014. Tarifa: Decreto 16/2026.
-- ORDEN SIN CONFIRMAR. Ademas el sentido (Maiz Amarillo -> Centro) esta sin
-- confirmar y arranca en Ebenezer, no en el barrio que da nombre a la ruta:
-- puede ser que el inventario liste los barrios sin respetar el recorrido.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Maíz Amarillo', 'URBANA', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Ebenezer'),
               (1, 'Maíz Amarillo'),
               (2, 'Gran Colombia'),
               (3, 'Centro')) AS v(order_index, neighborhood_name)
 WHERE name = 'Maíz Amarillo';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE name = 'Maíz Amarillo';


-- ===========================================================================
-- 3. Pablo Bello - Cedritos
-- ---------------------------------------------------------------------------
-- Procedencia: barrios del inventario Inventum 2014. Tarifa: Decreto 16/2026.
-- ORDEN SIN CONFIRMAR, y son solo tres entradas: casi con seguridad el
-- recorrido real pasa por mas barrios que estos tres.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Pablo Bello - Cedritos', 'URBANA', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Pablo Bello'),
               (1, 'Centro'),
               (2, 'Cedritos')) AS v(order_index, neighborhood_name)
 WHERE name = 'Pablo Bello - Cedritos';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE name = 'Pablo Bello - Cedritos';


-- ===========================================================================
-- 4. Camino Real - La Pampa
-- ---------------------------------------------------------------------------
-- Procedencia: recorrido de las tablas y el mapa oficial publicados por
-- Tizumba en Facebook el 27-oct-2020. Tarifa: Decreto 16/2026.
-- ORDEN INFERIDO del mapa, no leido de una tabla de paradas.
-- El nombre dice "La Pampa" pero el recorrido termina en Llano Grande: el
-- nombre viene de la fuente y el extremo del mapa. Sin resolver cual manda.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Camino Real - La Pampa', 'URBANA', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Camino Real'),
               (1, 'Prados de Alta Gracia'),
               (2, 'Calle 22'),
               (3, 'Éxito'),
               (4, 'Hospital'),
               (5, 'Batallón'),
               (6, 'La Pampa'),
               (7, 'Llano Grande')) AS v(order_index, neighborhood_name)
 WHERE name = 'Camino Real - La Pampa';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2600, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE name = 'Camino Real - La Pampa';


-- ===========================================================================
-- 5. Villa Leny - SENA Quebrajacho
-- ---------------------------------------------------------------------------
-- Procedencia: recorrido leido del mapa del Blog SENA, ene-2020.
-- Tarifa: Decreto 16/2026, tabla VEREDAL, entrada "Centro Agrotecnologico
-- Quebrajacho / SENA" — $2.700 y no $2.600 porque la ruta sale del casco
-- urbano. Sigue siendo precio unico fijo, asi que el tipo es URBANA: lo que
-- define el tipo en el dominio es cobrar un solo precio, no la geografia.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Villa Leny - SENA Quebrajacho', 'URBANA', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Villa Leny'),
               (1, 'Éxito'),
               (2, 'Puente El Águila'),
               (3, 'Centro'),
               (4, 'Villa Natalia'),
               (5, 'Vía Quebrajacho'),
               (6, 'SENA')) AS v(order_index, neighborhood_name)
 WHERE name = 'Villa Leny - SENA Quebrajacho';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 2700, NULL, 0, DATE '2026-02-05'
  FROM routes WHERE name = 'Villa Leny - SENA Quebrajacho';


-- ===========================================================================
-- 6. Fusagasuga - Chinauta (hasta Inspeccion El Triunfo)
-- ---------------------------------------------------------------------------
-- Procedencia: puntos, orden y tarifas del Decreto 16/2026 (tabla veredal de
-- Chinauta cruzada con la tabla de taxi via Melgar, que es la que da el orden
-- geografico de los puntos).
--
-- Veredal, no intermunicipal en sentido administrativo: no sale del municipio.
-- Va como INTERMUNICIPAL porque cobra POR PUNTO DE BAJADA, que es lo que ese
-- tipo significa en el dominio.
--
-- NO se siembra la entrada "Dentro de Chinauta = $2.600" que aparece en el
-- decreto. Esa es la tarifa de moverse DENTRO de Chinauta, no de llegar desde
-- Fusagasuga. Sembrada como punto de bajada de esta ruta, RF-06 responderia
-- que ir de Fusagasuga a Chinauta cuesta $2.600 cuando cuesta $5.000. Si algun
-- dia se modela el servicio interno de Chinauta, es una ruta aparte.
--
-- 'Colegio Chinauta' y 'Chinauta (retorno antes del peaje)' valen lo mismo
-- ($5.000) y son dos puntos distintos del decreto: no es un duplicado.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Fusagasugá - Chinauta (Inspección El Triunfo)', 'INTERMUNICIPAL', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Fusagasugá'),
               (1, 'Jaibaná'),
               (2, 'Colegio Chinauta'),
               (3, 'Chinauta'),
               (4, 'Pirámides'),
               (5, 'Alto Canecas'),
               (6, 'Inspección El Triunfo')) AS v(order_index, neighborhood_name)
 WHERE name = 'Fusagasugá - Chinauta (Inspección El Triunfo)';

-- order_index creciente de menor a mayor precio, como exige RF-06.
INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, v.amount, v.reference_point, v.order_index, DATE '2026-02-05'
  FROM routes,
       (VALUES (0, 'Jaibaná',                            3150),
               (1, 'Colegio Chinauta',                   5000),
               (2, 'Chinauta (retorno antes del peaje)', 5000),
               (3, 'Pirámides',                          6300),
               (4, 'Alto Canecas',                       6950),
               (5, 'Inspección El Triunfo',              9550))
           AS v(order_index, reference_point, amount)
 WHERE name = 'Fusagasugá - Chinauta (Inspección El Triunfo)';


-- ===========================================================================
-- 7. Fusagasuga - Arbelaez
-- ---------------------------------------------------------------------------
-- Procedencia: orden de los puntos de la tabla de taxi via Arbelaez del
-- Decreto 16/2026; tarifas de tramo de la tabla veredal del mismo decreto.
--
-- FALTA la tarifa hasta Arbelaez: no esta publicada en el decreto — el pasaje
-- intermunicipal completo lo fija la empresa transportadora. RF-06 no puede
-- responder "cuanto cuesta ir a Arbelaez" con esta semilla.
--
-- Dos nombres sin unificar entre el recorrido y las tarifas, dejados tal como
-- vienen de la fuente: 'Escuela Kennedy' (barrio) vs 'Colegio Kennedy'
-- (tarifa), y 'Batallon Sumapaz' (barrio) vs 'Guayabal Batallon' (tarifa).
-- Probablemente el mismo sitio; hay que confirmarlo y unificar la grafia,
-- porque la busqueda cruza por nombre exacto.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Fusagasugá - Arbeláez', 'INTERMUNICIPAL', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Fusagasugá'),
               (1, 'El Cuja'),
               (2, 'Escuela Kennedy'),
               (3, 'El Placer / Espinalito'),
               (4, 'Batallón Sumapaz'),
               (5, 'Los Ríos'),
               (6, 'Arbeláez')) AS v(order_index, neighborhood_name)
 WHERE name = 'Fusagasugá - Arbeláez';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, v.amount, v.reference_point, v.order_index, DATE '2026-02-05'
  FROM routes,
       (VALUES (0, 'Colegio Kennedy',   3450),
               (1, 'Guayabal Batallón', 4550),
               (2, 'Los Ríos',          5650))
           AS v(order_index, reference_point, amount)
 WHERE name = 'Fusagasugá - Arbeláez';


-- ===========================================================================
-- 8. Fusagasuga - Pasca
-- ---------------------------------------------------------------------------
-- Procedencia: orden de los puntos de la tabla de taxi via Pasca del Decreto
-- 16/2026; tarifa de Alaska de la tabla veredal del mismo decreto.
--
-- FALTA la tarifa hasta Pasca, y tambien las de 'Escuela de Sauces' y
-- 'Buenas Tardes': el decreto solo publica Alaska. Queda con UNA sola tarifa
-- de cinco puntos de recorrido, asi que RF-06 responde muy poco sobre esta
-- ruta. Es el hueco de dato mas grande de la semilla.
-- ===========================================================================
INSERT INTO routes (name, route_type, status, path_geojson) VALUES
    ('Fusagasugá - Pasca', 'INTERMUNICIPAL', 'ACTIVA', NULL);

INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name)
SELECT id, v.order_index, v.neighborhood_name
  FROM routes,
       (VALUES (0, 'Fusagasugá'),
               (1, 'Escuela de Sauces'),
               (2, 'Alaska'),
               (3, 'Buenas Tardes'),
               (4, 'Pasca')) AS v(order_index, neighborhood_name)
 WHERE name = 'Fusagasugá - Pasca';

INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
SELECT id, 3550, 'Alaska', 0, DATE '2026-02-05'
  FROM routes WHERE name = 'Fusagasugá - Pasca';
