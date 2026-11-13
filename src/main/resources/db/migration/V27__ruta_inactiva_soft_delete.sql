-- SCRUM-24 (RF-11): "eliminar" una ruta es un soft delete. El estado INACTIVA saca la ruta
-- de todo lo publico (catalogo, busqueda, tarifa) pero no borra ninguna fila: ride_history
-- y search_events siguen apuntando a ella y el historial la muestra como "ruta ya no disponible".
ALTER TABLE routes DROP CONSTRAINT ck_routes_status;
ALTER TABLE routes
    ADD CONSTRAINT ck_routes_status CHECK (status IN ('ACTIVA', 'SUSPENDIDA', 'INACTIVA'));
