-- V27 — Estado INACTIVA para el "borrado" de rutas por el administrador (RF-11, SCRUM-24).
--
-- Flyway es el dueno del esquema. Las migraciones ya aplicadas NO se tocan.
--
-- Eliminar una ruta es un soft delete: la fila queda con status = 'INACTIVA' y nada se
-- borra, porque ride_history.route_id y search_events.route_id la referencian. El
-- catalogo publico solo muestra ACTIVA, asi que una INACTIVA desaparece de las
-- busquedas nuevas sin romper el historial que ya la apunta.
--
-- El CHECK de V1 solo admitia ACTIVA y SUSPENDIDA: se reemplaza por uno que admite tambien INACTIVA.
ALTER TABLE routes DROP CONSTRAINT ck_routes_status;
ALTER TABLE routes ADD CONSTRAINT ck_routes_status
    CHECK (status IN ('ACTIVA', 'SUSPENDIDA', 'INACTIVA'));
