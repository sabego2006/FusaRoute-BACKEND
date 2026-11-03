-- Un letrero por ruta, como archivo SVG, y retiro del modelo de placas de V11.
--
-- Por que: el docente dijo que los letreros no se leen. Hasta hoy el letrero se
-- armaba en vivo desde tres tablas (route_signs / route_sign_plates /
-- route_sign_lines) con tres plantillas fijas (PLACA_ARCO, PLACA_RECT,
-- TABLA_FRANJAS) y colores puestos a ojo, asi que no se parecia a la foto. Lo que
-- entra en su lugar: un SVG por ruta dibujado sobre la foto, servido por el
-- frontend en public/letreros/<SIGN_FILE>.svg, y en la base una sola columna con
-- el nombre del archivo.
--
-- OJO, dicho explicito para que nadie lo descubra tarde:
--
-- 1. Esta migracion toma V14 porque V13 ya estaba reservada por SCRUM-20
--    (historial de busquedas, PR #60 todavia sin mergear) y V10 sigue reservada por
--    el borrador de SCRUM-166 (Centro Terminal, San Francisco - Terminal Directo).
--    Revisar antes de mergear que ninguna otra rama en vuelo haya tomado V14.
--
-- 2. sign_file es NULLABLE a proposito. Tres rutas del catalogo se quedan sin
--    letrero porque no hay foto que las respalde: 'Villa Leny - SENA Quebrajacho',
--    'Fusagasuga - Chinauta (Inspeccion El Triunfo)' y 'Fusagasuga - Pasca'. Una
--    columna NOT NULL obligaria a inventarles un letrero.
--
-- 3. El formato del valor es el nombre del archivo sin extension, en MAYUSCULAS con
--    guion bajo (CENTRO_GALERIA_TERMINAL), y el CHECK lo obliga. El nombre legible
--    del letrero vive en routes.name, que es lo que ve el usuario. El UNIQUE es lo
--    que garantiza que dos rutas no se peleen el mismo letrero: junto con
--    uq_routes_name, es la razon por la que "Buscar Ruta" no puede mostrar
--    duplicados.
--
-- 4. Se va el concepto de SENTIDO con las placas. La columna route_signs.sense
--    ('SUBIENDO' / 'BAJANDO') era el unico lugar de la base donde existia, y solo
--    describia el letrero, nunca la ruta. La regla del dominio pasa a ser
--    **una ruta = un letrero = un sentido**: el recorrido de ida y el de vuelta,
--    cuando se modelen, son dos filas de routes con su propio letrero, no una
--    columna "sentido". El comentario de V1 que hablaba de "ida y vuelta son dos
--    registros" se queda como historia (una migracion aplicada no se edita jamas);
--    la regla vigente queda escrita aqui y en el CLAUDE.md del backend.
--
-- 5. Los 10 letreros que V11 sembro sobre 6 rutas se pierden con el DROP. No es una
--    perdida de evidencia: la trazabilidad de campo (field_id LC-0NN, foto de
--    origen, recorte, colores muestreados) vive en la carpeta del curso, en
--    docs/investigacion/letreros-rutas.json y docs/investigacion/letreros-svg/,
--    que es de donde salen los 31 SVG. V11 queda en el historial para quien
--    necesite ver como estaba modelado antes.
--
-- Fuente de los datos que entran despues: docs/investigacion/letreros-rutas.json
-- (carpeta del curso), version 2026-11-02, issue SCRUM-184.
-- Pendiente al cerrar esta migracion: la siembra de las rutas (V15) y los trazados
-- (V16). Esta solo mueve el esquema.
-- ===========================================================================


-- 1. La columna del letrero
-- ---------------------------------------------------------------------------
ALTER TABLE routes ADD COLUMN sign_file VARCHAR(140);

ALTER TABLE routes ADD CONSTRAINT uq_routes_sign_file UNIQUE (sign_file);

-- Con sign_file NULL el CHECK evalua a UNKNOWN, que no lo viola: las rutas sin
-- letrero pasan sin necesidad de una condicion extra.
ALTER TABLE routes ADD CONSTRAINT ck_routes_sign_file CHECK (sign_file ~ '^[A-Z0-9_]+$');

COMMENT ON COLUMN routes.sign_file IS
    'Nombre del archivo del letrero en public/letreros/<sign_file>.svg, sin extension. NULL = la ruta no tiene letrero fotografiado. Una ruta = un letrero = un sentido.';


-- 2. Retiro del modelo de placas de V11
-- ---------------------------------------------------------------------------
-- En orden hijo -> padre. Las FK son ON DELETE CASCADE, pero el DROP de la tabla
-- padre fallaria igual si la hija todavia existe.
DROP TABLE route_sign_lines;
DROP TABLE route_sign_plates;
DROP TABLE route_signs;
