# Guía de Docker para FusaRoute-BACKEND

Esta guía es para Santiago y Angélica, pensada desde cero: ninguno de los dos tenía claro el uso de Docker en este proyecto todavía. El objetivo es que ambos puedan verificar, cada uno en su propia máquina, que Docker funciona igual.

## 1. Qué es cada cosa

| Término | Qué es |
|---|---|
| **Docker Desktop** | El programa que instalas en Windows. Sin esto no hay nada más que funcione. |
| **Imagen** | La "receta" empaquetada de un programa (por ejemplo, `postgres:16-alpine` o lo que construye nuestro `Dockerfile`). No corre nada por sí sola. |
| **Contenedor** | Una imagen *en ejecución*. Es lo que realmente ocupa CPU/memoria y expone puertos. |
| **`docker-compose.yml`** | Un archivo que describe varios contenedores relacionados (aquí: una base Postgres + el backend) y cómo se conectan entre sí, para no tener que arrancar cada uno a mano. |

En este repo, **solo el backend usa Docker**. El frontend (Angular) se despliega a Vercel como archivos estáticos — no tiene Dockerfile ni lo necesita.

## 2. Instalar y verificar Docker Desktop (una sola vez, por máquina)

> Nota de esta sesión: al revisar la máquina de Angélica, Docker Desktop **no estaba instalado** (ni `docker` en el PATH, ni el ejecutable en `Program Files`). Es muy probable que Santiago esté en la misma situación — por eso este paso va primero, no se asume que ya está listo.

1. Descargar e instalar **Docker Desktop** desde [docker.com](https://www.docker.com/products/docker-desktop/) (requiere Windows 11 + WSL2, que el instalador configura solo si falta).
2. Reiniciar si el instalador lo pide, y abrir Docker Desktop al menos una vez (debe quedar la ballena en la bandeja del sistema, en verde).
3. Verificar desde una terminal **nueva** (PowerShell o Git Bash, para que tome el PATH actualizado):
   ```powershell
   docker --version
   docker info
   ```
   Si `docker info` responde con datos del servidor (no un error de conexión), Docker está listo.
4. Prueba mínima, sin tocar nada de FusaRoute:
   ```powershell
   docker run hello-world
   ```
   Debe imprimir un mensaje de "Hello from Docker!". Si esto falla, el problema es de la instalación de Docker, no del proyecto — resolver esto antes de seguir.

## 3. Dos caminos para correr el backend — y cuándo usar cada uno

El equipo tiene documentados **dos flujos distintos**. No son intercambiables: usan bases de datos diferentes.

### Camino A — El del día a día (sin Docker, con la Supabase compartida)

Este es el que usa el equipo normalmente para programar features. Apunta a la base de datos **compartida en la nube** (proyecto Supabase `fusaroute-dev`), así que todos ven los mismos datos.

```bash
cp .env.example .env
# llenar .env con las credenciales de fusaroute_dev (se piden por canal privado, nunca por repo/Jira)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# verificar: GET http://localhost:8080/health  ->  {"status":"UP"}
```

Requiere JDK 25 y Maven 3.9+ instalados — **no requiere Docker en absoluto**.

### Camino B — Con `docker-compose` (Postgres local, aislado)

Este camino levanta un **Postgres propio y vacío**, solo en tu máquina, separado de la base compartida. Es para:
- Probar una migración Flyway nueva (como la `V7` de esta misma tarea) sin arriesgar la base de datos que usa todo el equipo.
- Confirmar que el backend compila y arranca dentro de un contenedor igual al que usa Render en producción.

Desde `FusaRoute-BACKEND/`:

```bash
docker compose up --build
```

Esto levanta dos contenedores:
- `fusaroute-db` — Postgres 16, puerto `5432`, usuario `fusaroute_dev` / contraseña `dev_password`. **Estas credenciales son de juguete, solo para este Postgres aislado — no son las credenciales reales de Supabase.**
- `fusaroute-backend` — compila el `Dockerfile` (dos etapas: Maven compila, el JRE corre el jar) y arranca contra ese Postgres local, puerto `8080`.

Para confirmar que levantó bien:
```bash
curl http://localhost:8080/health
# {"status":"UP"}
```

Para ver los logs del backend (útil para confirmar que Flyway aplicó las migraciones sin error):
```bash
docker compose logs -f backend
```
Buscar en el log una línea tipo `Successfully applied N migrations` — ahí se confirma que `V1`...`V7` corrieron bien.

Para parar:
```bash
docker compose down        # para los contenedores, conserva los datos en el volumen
docker compose down -v     # además borra el volumen -- Postgres queda vacío la próxima vez
```
`down -v` es útil cuando quieres probar que las migraciones corren bien **desde cero**, como si fuera la primera vez.

### Resumen: ¿cuál uso?

| Situación | Camino |
|---|---|
| Programar una historia de usuario normal | A (Maven + Supabase compartida) |
| Probar una migración Flyway nueva antes de subirla | B (`docker compose`) |
| Verificar que el contenedor arranca igual que en Render (producción) | B (`docker compose`) |
| Nunca | Usar el Postgres de Docker como si fuera la base compartida del equipo — son bases distintas, con datos distintos |

## 4. Nota de seguridad

El `.env` real (con las credenciales de Supabase) **nunca** se copia dentro del build de Docker — `.dockerignore` ya lo excluye explícitamente. Nunca pegar el contenido de `.env` en el chat del equipo, en un commit, ni en la descripción de una imagen en Docker Hub.

## 5. Checklist para confirmar que Docker funciona igual en ambas máquinas

Cada uno (Santiago y Angélica) corre esto por separado y comparan resultados:

- [ ] `docker --version` imprime una versión (sin error de "comando no encontrado").
- [ ] `docker info` responde con datos del servidor, no un error de conexión.
- [ ] Desde `FusaRoute-BACKEND/`, `docker compose up --build` levanta `fusaroute-db` y `fusaroute-backend` sin errores en la consola.
- [ ] `curl http://localhost:8080/health` responde `{"status":"UP"}`.
- [ ] Los logs (`docker compose logs backend`) muestran que Flyway aplicó todas las migraciones, sin fallos.
- [ ] `docker compose down -v` limpia todo sin dejar contenedores ni volúmenes huérfanos (`docker ps -a` y `docker volume ls` quedan sin nada de `fusaroute-*`).

Si el resultado de este checklist difiere entre las dos máquinas, ese es el punto concreto a investigar (versión de Docker Desktop, configuración de WSL2, puertos ocupados por otro proceso, etc.) antes de asumir que "ya funciona".
