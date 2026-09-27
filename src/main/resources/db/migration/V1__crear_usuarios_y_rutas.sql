-- V1: esquema inicial de FusaRoute.
--
-- Cuatro tablas segun el dominio del CLAUDE.md:
--   users                 usuarios finales y administradores (RF-01/02/03).
--   routes                catalogo de rutas. path_geojson NULLable a proposito:
--                         la siembra de V2 no trae trazados (SCRUM-166 los aporta
--                         despues) y el buscador de RF-04/05/06 filtra por barrio,
--                         no por geometria.
--   route_neighborhoods   barrios por los que pasa cada ruta, en orden.
--   fares                 tarifa. Urbana: una sola fila con reference_point=NULL.
--                         Intermunicipal: una fila por punto de referencia de bajada,
--                         ordenadas de menor a mayor precio segun distancia recorrida.
--
-- La regla de nombres es la del CLAUDE.md: identificadores en ingles, en snake_case.
-- No se crean indices geoespaciales (fuera de alcance: exigiria PostGIS).

CREATE TABLE users (
    id                        BIGSERIAL       PRIMARY KEY,
    name                      VARCHAR(120)    NOT NULL,
    email                     VARCHAR(255)    NOT NULL,
    phone                     VARCHAR(30),
    password_hash             VARCHAR(72)     NOT NULL,
    role                      VARCHAR(20)     NOT NULL DEFAULT 'USER',
    failed_login_attempts     SMALLINT        NOT NULL DEFAULT 0,
    locked_until              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_role_check   CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE routes (
    id                        BIGSERIAL       PRIMARY KEY,
    name                      VARCHAR(160)    NOT NULL,
    code                      VARCHAR(30),
    fare_type                 VARCHAR(20)     NOT NULL,
    status                    VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    path_geojson              JSONB,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT routes_fare_type_check CHECK (fare_type IN ('URBAN', 'INTERMUNICIPAL')),
    CONSTRAINT routes_status_check    CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- El buscador filtra rutas activas por nombre de barrio. Los dos indices
-- responden a esas dos consultas de RF-04/05/06.
CREATE INDEX routes_status_idx ON routes (status);

CREATE TABLE route_neighborhoods (
    route_id                  BIGINT          NOT NULL,
    position                  SMALLINT        NOT NULL,
    neighborhood_name         VARCHAR(120)    NOT NULL,
    CONSTRAINT route_neighborhoods_pk PRIMARY KEY (route_id, position),
    CONSTRAINT route_neighborhoods_route_fk
        FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE,
    CONSTRAINT route_neighborhoods_position_check CHECK (position >= 0)
);

CREATE INDEX route_neighborhoods_name_idx ON route_neighborhoods (neighborhood_name);

CREATE TABLE fares (
    id                        BIGSERIAL       PRIMARY KEY,
    route_id                  BIGINT          NOT NULL,
    position                  SMALLINT        NOT NULL DEFAULT 0,
    reference_point           VARCHAR(200),
    price                     NUMERIC(10, 2)  NOT NULL,
    valid_from                DATE            NOT NULL DEFAULT CURRENT_DATE,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT fares_route_fk
        FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE,
    CONSTRAINT fares_route_position_unique UNIQUE (route_id, position),
    CONSTRAINT fares_price_positive CHECK (price > 0),
    CONSTRAINT fares_position_check CHECK (position >= 0)
);

CREATE INDEX fares_route_idx ON fares (route_id);
