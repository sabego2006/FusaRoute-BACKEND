# Evidencia de Docker (sección 16 del documento final)

Corrida real del `docker-compose.yml` de este repo: PostgreSQL + backend + frontend con un
solo comando, y la aplicación usada desde un navegador.

## Condiciones de la corrida

| | |
|---|---|
| Fecha | 2026-11-10 (hora de Colombia; los logs están en UTC, 2026-11-11) |
| Máquina | Windows 11 Pro, Docker Desktop (Engine 29.9.2, Compose v5.6.1), 16 CPU y 8 GB asignados a Docker |
| Código | backend y frontend en `origin/main` más los cambios de este PR y del PR hermano del frontend |
| Comando | `docker compose down -v` y luego `docker compose up --build -d` (base vacía; imágenes ya en caché) |
| Puertos | **no son los de por defecto**: `DB_PORT=15432`, `BACKEND_PORT=18080`, `FRONTEND_PORT=4200`, porque la máquina ya tenía ocupados 5432 y 8080. Con el `.env` sin esas variables quedan 5432, 8080 y 4200 |
| Google Maps | sin claves (ni la del backend ni la del frontend): la búsqueda responde con el cálculo propio y la pantalla muestra "El mapa no está disponible ahora" en lugar del mapa |

## Qué se comprobó

| Comprobación | Resultado | Archivo |
|---|---|---|
| Los tres servicios arrancan con un comando y quedan `healthy` | sí, 50 s con las imágenes en caché | [`compose-ps.txt`](compose-ps.txt), [`arranque.txt`](arranque.txt) |
| Orden de arranque por *healthcheck* (`db` → `backend` → `frontend`) | sí | [`arranque.txt`](arranque.txt) |
| Flyway crea el esquema y aplica las migraciones sobre la base vacía | 23 migraciones, hasta V25 | [`arranque.txt`](arranque.txt) |
| La URL del backend llega al frontend en ejecución, no compilada | `/config.json` devuelve `http://localhost:18080`, el valor del `.env` | [`verificacion.txt`](verificacion.txt) |
| *Fallback* de SPA | `GET /rutas/5` responde 200 con `index.html` | [`verificacion.txt`](verificacion.txt) |
| Ningún proceso corre como root | `nginx` (uid 101) y `fusaroute` (uid 999) | [`verificacion.txt`](verificacion.txt) |
| Registro, inicio de sesión (con activación del segundo factor) y búsqueda de ruta desde el navegador | todas las peticiones con respuesta correcta; la búsqueda Carrera 17 → Carrera 8 sugiere "Cuja - Horizonte - Mesitas" | [`verificacion.txt`](verificacion.txt), capturas |
| Los datos quedan en PostgreSQL | 1 usuario, 1 búsqueda en el historial, 34 rutas sembradas | [`verificacion.txt`](verificacion.txt) |
| Tamaño de las imágenes | backend 608 MB, frontend 83,7 MB | [`imagenes.txt`](imagenes.txt) |

El `401` de `POST /api/auth/login` que aparece en `verificacion.txt` es el comportamiento
esperado: una cuenta nueva debe activar el segundo factor antes de recibir la sesión
(`TOTP_ENROLLMENT_REQUIRED`); las dos peticiones siguientes lo activan.

## Capturas

| Archivo | Qué muestra |
|---|---|
| [`01-registro.png`](01-registro.png) | formulario de registro servido por el contenedor de Nginx |
| [`02-sesion-iniciada.png`](02-sesion-iniciada.png) | sesión iniciada tras activar el segundo factor |
| [`03-busqueda-resultado.png`](03-busqueda-resultado.png) | resultado de la búsqueda: ruta, letrero, tiempo, caminata y tarifa |
| [`04-catalogo.png`](04-catalogo.png) | catálogo de rutas leído de la base del compose |

El recorrido lo hizo un script de Playwright (Chromium) contra `http://localhost:4200`, con
una cuenta de prueba creada en el momento sobre la base local; no se usó ninguna credencial
real. El script fue de un solo uso y no se versiona: las pruebas de extremo a extremo
formales son otro entregable.

## Límites de esta evidencia

- Los 50 s son con las imágenes ya construidas. La primera construcción en esta máquina
  tardó varios minutos (descarga de dependencias de Maven y de npm) y no se cronometró.
- No se probó con claves de Google Maps: el mapa y los tiempos de Google no están cubiertos.
- Falta la captura manual de Docker Desktop con los tres contenedores en verde.

## Explicación de los Dockerfile

[`dockerfiles-explicados.md`](dockerfiles-explicados.md): línea por línea, lista para las
secciones 16.2 y 16.3.
