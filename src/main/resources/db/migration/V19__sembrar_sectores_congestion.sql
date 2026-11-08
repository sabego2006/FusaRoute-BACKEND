-- SCRUM-18 / SCRUM-187: siembra de sectores de congestion historica (RF-09).
--
-- Fuente: observacion del equipo FusaRoute, dictada por Santiago Bermudez el 2026-11-07.
-- Fecha(s) y metodo del levantamiento en campo: no registrados; pendiente de documentar.
--
-- Una ventana por fila (V9 modela una sola franja por sector y name es UNIQUE), asi que
-- el sector "Indio", que se tranca dos veces al dia, entra como dos filas. Cada franja
-- dura 2 horas, [start_time, end_time); el aviso se enciende 30 min antes.
--
-- El barrio es 'Indio', identico al de route_neighborhoods (V15). 'Monumento El Indio'
-- (V12) NO se incluye: no hay observacion que diga que ese nombre cae en el sector.

-- ---------------------------------------------------------------------------
-- Sector: Indio, franja del mediodia (12:00 - 14:00)
-- ---------------------------------------------------------------------------
INSERT INTO traffic_sectors (name, start_time, end_time)
VALUES ('Indio (mediodia)', TIME '12:00', TIME '14:00');

INSERT INTO traffic_sector_neighborhoods (sector_id, neighborhood_name)
SELECT id, v.neighborhood_name
  FROM traffic_sectors,
       (VALUES ('Indio')) AS v(neighborhood_name)
 WHERE name = 'Indio (mediodia)';

-- ---------------------------------------------------------------------------
-- Sector: Indio, franja de la tarde (17:00 - 19:00)
-- ---------------------------------------------------------------------------
INSERT INTO traffic_sectors (name, start_time, end_time)
VALUES ('Indio (tarde)', TIME '17:00', TIME '19:00');

INSERT INTO traffic_sector_neighborhoods (sector_id, neighborhood_name)
SELECT id, v.neighborhood_name
  FROM traffic_sectors,
       (VALUES ('Indio')) AS v(neighborhood_name)
 WHERE name = 'Indio (tarde)';
