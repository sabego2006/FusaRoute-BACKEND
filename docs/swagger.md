# Swagger / OpenAPI — cómo levantar el dashboard

Guía para levantar la documentación interactiva de la API de FusaRoute en local y
probar endpoints desde el navegador, sin Postman y sin escribir un solo `curl`.

## Qué es y de dónde sale

Swagger UI es un dashboard web que **se genera solo** a partir del código: no hay
un archivo de documentación que alguien tenga que mantener a mano. La librería
`springdoc-openapi` inspecciona los `@RestController` al arrancar, arma un
documento OpenAPI 3.1 (un JSON que describe cada endpoint, sus parámetros y sus
respuestas) y le pone encima una interfaz para ejecutar peticiones de verdad
contra el backend que está corriendo.

Consecuencia práctica: **si un endpoint cambia, el dashboard cambia solo** en el
siguiente arranque. Documentación que no se desactualiza.

Las tres piezas que lo hacen funcionar en este repo:

| Pieza | Archivo | Qué aporta |
|---|---|---|
| Dependencia | `pom.xml` → `springdoc-openapi-starter-webmvc-ui` 2.8.6 | Genera el documento OpenAPI y sirve la UI |
| Metadatos y auth | `src/main/java/com/fusaroute/infrastructure/config/OpenApiConfig.java` | Título, versión, y el esquema de seguridad `bearerAuth` (JWT) |
| Acceso público | `src/main/java/com/fusaroute/infrastructure/config/SecurityConfig.java` | Deja pasar `/swagger-ui/**` y `/v3/api-docs/**` sin token |

Ese tercer punto es el que más se olvida: con Spring Security activo, si no se
permiten esas rutas explícitamente el dashboard responde 401 y parece que Swagger
"no está instalado". Aquí ya está permitido (línea ~81 de `SecurityConfig`).

## Requisitos previos

```bash
java -version    # 25 o superior (el pom fija java.version=25)
mvn -v           # 3.9.x
```

Además, un archivo `.env` en la raíz del backend con las credenciales de DEV.
Se copia de la plantilla y se rellena:

```bash
cp .env.example .env
```

Variables que **no tienen valor por defecto** — si falta cualquiera, el arranque
falla a propósito: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GOOGLE_MAPS_API_KEY`,
`JWT_SECRET`, `TOTP_ENCRYPTION_KEY`.

> El `.env` **nunca se sube a Git** (está en `.gitignore`). Las credenciales de
> DEV se piden al equipo.

## Levantarlo

Desde la raíz de `FusaRoute-BACKEND`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Espera la línea `Started FusaRouteApplication in X seconds`. Si prefieres no
recompilar, con el jar ya construido:

```bash
mvn clean package -DskipTests      # solo la primera vez
java -jar target/fusaroute-backend-0.0.1-SNAPSHOT.jar
```

Comprobación rápida de que el backend respondió:

```bash
curl http://localhost:8080/health    # → {"status":"UP"}
```

## Las URLs

| URL | Qué es |
|---|---|
| <http://localhost:8080/swagger-ui/index.html> | **El dashboard.** Es la que se abre en el navegador |
| <http://localhost:8080/swagger-ui.html> | Atajo: redirige (302) a la anterior |
| <http://localhost:8080/v3/api-docs> | El contrato OpenAPI en JSON crudo |
| <http://localhost:8080/v3/api-docs/swagger-config> | Config que la UI lee al cargar |

El JSON de `/v3/api-docs` es lo que se le entrega a otro equipo (por ejemplo, el
del frontend) o se importa en Postman: es el contrato de la API en formato
estándar, no una captura de pantalla.

## Probar un endpoint protegido (JWT)

Casi todos los endpoints exigen un token. El flujo dentro del propio dashboard:

1. Abre `POST /api/auth/register` → **Try it out** → rellena el body → **Execute**.
   (O usa `POST /api/auth/login` si ya tienes una cuenta en DEV.)
2. En la respuesta 200, copia el valor del token JWT.
3. Arriba a la derecha, botón **Authorize** 🔓 → pega el token → **Authorize** → **Close**.
   Pega **solo** el token: Swagger añade el prefijo `Bearer ` por su cuenta,
   porque `OpenApiConfig` declara el esquema como `bearer`/`JWT`.
4. Ahora cualquier endpoint con candado (`GET /api/users/me`, etc.) se puede
   ejecutar: la UI manda el header `Authorization` automáticamente.

El candado cerrado 🔒 junto a un endpoint significa "requiere token"; esa
información también la genera `springdoc` a partir de la configuración, no se
escribe a mano.

## Endpoints que aparecen hoy

15 operaciones, agrupadas por controlador:

- **auth** — `POST /api/auth/register`, `POST /api/auth/login`
- **users** — `GET /api/users/me`, `PUT /api/users/me/password`,
  `GET /api/users/me/totp`, `POST /api/users/me/totp/enroll`,
  `POST /api/users/me/totp/confirm`, `GET /api/users/me/search-history`
- **routes** — `GET /api/routes`, `GET /api/routes/{id}`,
  `GET /api/routes/{id}/fare`, `GET /api/routes/search`,
  `GET /api/routes/search/offline`
- **operación** — `GET /health`, `GET /api/_internal/maps-probe`

## Detenerlo

`Ctrl + C` en la terminal donde corre. Si quedó un proceso huérfano ocupando el
8080:

```bash
# Windows (PowerShell)
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force }
```

## Si algo falla

| Síntoma | Causa y arreglo |
|---|---|
| `401` en `/swagger-ui/index.html` | Las rutas de Swagger no están en el `permitAll()` de `SecurityConfig`. Aquí ya lo están |
| `404` en `/swagger-ui/index.html` | Falta la dependencia `springdoc-openapi-starter-webmvc-ui` en el `pom.xml` |
| `Port 8080 was already in use` | Otro proceso tiene el puerto. Mátalo (arriba) o arranca con `--server.port=8081` |
| Arranque falla pidiendo `DB_URL`, `JWT_SECRET`… | Falta el `.env` o le falta una variable. Compara con `.env.example` |
| `Schema-validation: missing table [X]` | Hay una entidad JPA sin su migración de Flyway. Es un error real del código, no de Swagger — ver abajo |
| La UI carga pero sin endpoints | El backend arrancó con otro perfil, o los controladores no están en el paquete escaneado |

### Sobre `Schema-validation: missing table`

El perfil `dev` usa `spring.jpa.hibernate.ddl-auto=validate`: al arrancar,
Hibernate compara las entidades JPA contra el esquema real y **aborta** si alguna
tabla no existe. Es una red de seguridad deliberada — el esquema lo versiona
Flyway (`src/main/resources/db/migration/`), no Hibernate, así que una entidad sin
su `.sql` correspondiente es un bug que conviene descubrir al arrancar y no en
producción.

Este repo tuvo exactamente ese caso: `SearchHistoryJpaEntity` (RF-07, SCRUM-20)
entró sin su migración y el arranque fallaba con
`missing table [search_history]`. Se corrigió escribiendo
`V12__historial_de_busqueda.sql`. **El arreglo correcto es siempre escribir la
migración que falta**, no desactivar la validación.

Si necesitas ver el dashboard mientras una migración así se resuelve, se puede
saltar la comprobación sin tocar ningún archivo ni la base de datos:

```bash
java -jar target/fusaroute-backend-0.0.1-SNAPSHOT.jar --spring.jpa.hibernate.ddl-auto=none
```

Es un parche temporal de arranque local: cualquier endpoint que use la tabla
ausente seguirá fallando en tiempo de ejecución. No se commitea nunca como
configuración.

## Cómo añadir una migración (si te toca)

1. Crea `src/main/resources/db/migration/V<n>__descripcion_corta.sql`, donde
   `<n>` es **el siguiente número libre**, mayor que la versión ya aplicada en la
   base. Flyway no acepta migraciones fuera de orden: si la base está en v11, una
   V10 nueva no se aplica.
2. **Nunca edites una migración ya aplicada.** Flyway guarda un checksum de cada
   archivo; cambiar uno rompe el arranque con un error de validación. Las
   correcciones van en una migración nueva.
3. Los tipos deben coincidir con lo que declara la entidad (un `length = 120` en
   `@Column` ↔ `VARCHAR(120)`), porque `ddl-auto=validate` los compara al
   arrancar.
4. Arranca la app: Flyway aplica lo pendiente solo, y lo registra en la tabla
   `flyway_schema_history` del esquema `fusaroute`.
