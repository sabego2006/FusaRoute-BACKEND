-- PLANTILLA, no es una migracion: vive en docs/ a proposito para que Flyway no la aplique.
--
-- Como se usa (la regla del 30 % manual aplica: los valores los escribe el integrante a
-- mano, a partir de la observacion de campo del equipo):
--   1. Copiar este archivo a src/main/resources/db/migration/ con el nombre
--        V10__sembrar_sectores_congestion.sql
--      (si ya existe un V10 en main, usar el siguiente numero libre; MigrationVersionsTest
--      falla ante versiones duplicadas).
--   2. Reemplazar cada <...> por el dato medido. Un bloque por sector. Borrar los bloques
--      que sobren y estas instrucciones.
--   3. NO editar V9: una migracion ya aplicada cambia de checksum y Flyway se niega a arrancar.
--   4. Poner la FUENTE de cada sector en el comentario del bloque Y en el cuerpo del PR.
--
-- Condicion para que un sector entre: es observacion real con fuente citable (fecha,
-- metodo, quien la levanto). Un estimado NO entra: V9 declara que no hay dato de campo y
-- sembrar uno inventado seria presentarle dato falso al comite.
--
-- Limites del modelo (V9), no de esta plantilla:
--   * La ventana son 2 horas, [start_time, end_time); el aviso se enciende 30 min antes.
--     No hay CHECK que obligue las 2 horas ni que start_time < end_time: se respeta a mano.
--   * Una ventana que cruce la medianoche no esta soportada.
--   * Sin dia de la semana: la misma ventana rige todos los dias.
--   * traffic_sectors.name es UNIQUE.
--
-- Los neighborhood_name deben ser IDENTICOS (mayusculas, tildes, puntos, espacios) a los de
-- route_neighborhoods, porque el cruce con la ruta es por nombre exacto. Lista vigente tras
-- aplicar V2, V3, V5 y V6 (extraida de origin/main el 2026-10-28):
--
--   Llano Largo - La Clarita ("100"): Llano Verde, Llano Largo, Centro, Cra. 1 Santander,
--                                     Pantano de Vargas, La Clarita
--   Maíz Amarillo (V5):               Hospital San Rafael, Maíz Amarillo, Ciudad Ebenezer
--   Pablo Bello - Cedritos:           Pablo Bello, Centro, Cedritos
--   Camino Real - La Pampa:           Camino Real, Prados de Alta Gracia, Calle 22, Éxito,
--                                     Hospital, Batallón, La Pampa, Llano Grande
--   Villa Leny - SENA Quebrajacho:    Villa Leny, Éxito, Puente El Águila, Centro,
--                                     Villa Natalia, Vía Quebrajacho, SENA
--   Fusagasugá - Chinauta:            Fusagasugá, Jaibaná, Colegio Chinauta, Chinauta,
--                                     Pirámides, Alto Canecas, Inspección El Triunfo
--   Fusagasugá - Arbeláez:            Fusagasugá, El Cuja, Escuela Kennedy, El Placer,
--                                     Espinalito, Batallón Sumapaz, Los Ríos, Arbeláez
--   Fusagasugá - Pasca (V3):          Fusagasugá, Corregimiento, Hogar Francisco y Clara,
--                                     Escuela de los Sauces, Alaska, Buenas Tardes,
--                                     Crucero La Angostura, La Capilla, Pasca
--   Gran Colombia (V6):               Hospital, Gran Colombia, Escuela de Policía, Ebenezer
--
-- OJO, nombres casi iguales que NO cruzan entre si: "Hospital" (Camino Real, Gran Colombia)
-- vs "Hospital San Rafael" (Maiz Amarillo); "Ebenezer" (Gran Colombia) vs "Ciudad Ebenezer"
-- (Maiz Amarillo). Un sector que liste solo uno de los dos avisa en una ruta y en la otra no.
-- Decidir a proposito cual(es) incluir, o listar ambos.
--
-- Verificacion despues de aplicar (cada barrio sembrado debe existir en alguna ruta; esta
-- consulta debe devolver CERO filas):
--   SELECT n.neighborhood_name
--     FROM traffic_sector_neighborhoods n
--    WHERE NOT EXISTS (SELECT 1 FROM route_neighborhoods r
--                       WHERE r.neighborhood_name = n.neighborhood_name);

-- ---------------------------------------------------------------------------
-- Sector: <NOMBRE DEL SECTOR, p. ej. el corredor o la via que se tranca>
-- Fuente: <fecha(s) de observacion, metodo (conteo en sitio, cronometraje, ...), quien>
-- ---------------------------------------------------------------------------
INSERT INTO traffic_sectors (name, start_time, end_time)
VALUES ('<NOMBRE DEL SECTOR>', TIME '<HH:MM>', TIME '<HH:MM>');

INSERT INTO traffic_sector_neighborhoods (sector_id, neighborhood_name)
SELECT id, v.neighborhood_name
  FROM traffic_sectors,
       (VALUES ('<barrio 1>'),
               ('<barrio 2>')) AS v(neighborhood_name)
 WHERE name = '<NOMBRE DEL SECTOR>';
