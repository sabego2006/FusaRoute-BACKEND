-- V18 — Destino favorito por usuario (RF-10, SCRUM-21).
--
-- Flyway es el dueno del esquema. Las migraciones ya aplicadas NO se tocan: editar
-- una migracion ya aplicada cambia su checksum y rompe el arranque.
--
-- DEPENDE DE LA V17 (historial de busquedas, SCRUM-20), que a la fecha de este
-- archivo sigue sin mergear en su propio PR. Flyway no aplica migraciones fuera de
-- orden: si esta V18 llega a la base antes que la V17, la V17 ya no entra nunca y el
-- arranque falla. Por eso el PR del historial se mergea primero. Es la misma leccion
-- que dejo escrita la V17, vista desde el otro lado: alla el problema fue aplicar la
-- migracion de una rama sin mergear; aqui es adelantarse en el numero.


-- ---------------------------------------------------------------------------
-- favorite_destination — un solo destino favorito por usuario (RF-10)
-- ---------------------------------------------------------------------------
-- user_id es la LLAVE PRIMARIA, no una columna con indice. Esa es la traduccion
-- literal del criterio de aceptacion "solo uno activo a la vez": la base no deja
-- que un usuario tenga dos favoritos, en vez de confiar en que la aplicacion no
-- inserte el segundo. Tambien es lo que hace que guardar sea un reemplazo — Spring
-- Data hace merge sobre el id asignado a mano, asi que marcar un favorito nuevo
-- pisa el anterior sin borrarlo antes.
--
-- Por lo mismo no hay un indice aparte: la PK ya es el indice que sostiene la unica
-- consulta de la tabla (buscar el favorito de un usuario).
--
-- ON DELETE CASCADE: el favorito no sobrevive al usuario que lo marco. Es una
-- preferencia suya, no un registro contable que haya que conservar. Igual que
-- search_history en la V17.
CREATE TABLE favorite_destination (
    user_id     BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    -- 120 es el mismo ancho que users.name y que search_history.destination, y el
    -- length declarado en la entidad; Hibernate valida el tipo al arrancar, asi que
    -- ambos deben coincidir.
    --
    -- Se guarda el nombre del barrio y no una coordenada ni una FK a routes: lo que
    -- la pantalla de busqueda precarga es el texto del campo destino, y el barrio es
    -- justamente ese texto. Una FK ademas atarian el favorito a una ruta concreta,
    -- cuando el usuario marca un destino, no una ruta.
    destination VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE favorite_destination IS
    'Destino favorito del usuario (RF-10). Uno solo por cuenta: user_id es la llave primaria.';
COMMENT ON COLUMN favorite_destination.destination IS
    'Barrio de destino, tal como se precarga en el campo de busqueda.';
COMMENT ON COLUMN favorite_destination.created_at IS
    'Cuando se marco el favorito vigente. Se reescribe cada vez que se reemplaza.';
