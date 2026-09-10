# FusaRoute — Backend

API REST del sistema de información de transporte público de Fusagasugá. Proyecto Integrador de Ingeniería de Software I, Universidad de Cundinamarca (docente: Ing. Luiferney Ortiz Parra).

**Recursos externos:** la carpeta del curso en OneDrive (`C:/Users/Santiago/OneDrive - UNIVERSIDAD DE CUNDINAMARCA/Universidad/5 SEMESTRE/INGENIERIA SOFTWARE I`) contiene la Actividad 3 v3 y el material de clase. Las historias de usuario grilladas (RF-01 a RF-12, con criterios de aceptación) están en `docs/backlog/historias-rf01-rf12.md` de esa misma carpeta — es la fuente de las reglas de negocio de este archivo.

**Planes vigentes** (leer los dos al abrir sesión nueva): el plan maestro de arranque en `C:/Users/Santiago/.claude/plans/eager-coalescing-creek.md` y el **Plan de Metodología y Preparación** (vigente desde 2026-09-09) en `C:/Users/Santiago/.claude/plans/lee-el-estado-del-wondrous-hoare.md`.

El contexto completo del curso, el alcance del proyecto y las métricas de calidad comprometidas están en el `CLAUDE.md` de la carpeta madre de la asignatura.

**Equipo:** Santiago Bermúdez · Angélica Aranguren. Ambos trabajan todo el stack por exigencia del docente. Es la primera vez del equipo con Spring Boot, así que las convenciones de este archivo se explican, no solo se enuncian.

## Stack

- **Java 25** (LTS vigente) + **Spring Boot 3.5.16**, Maven 3.9+
- **Arquitectura hexagonal** (puertos y adaptadores) — exigida por el docente
- PostgreSQL alojado en Supabase, vía Spring Data JPA
- Spring Security + JWT
- JUnit 5 + Mockito

## Arquitectura hexagonal: la regla que no se rompe

**La dependencia siempre apunta hacia adentro.** El dominio no conoce a nadie; la infraestructura conoce al dominio.

```
domain  ←  application  ←  infrastructure
```

Concretamente: una clase en `domain/` **nunca** importa Spring, JPA, Jackson, HTTP ni Supabase. Si aparece un `@Entity`, un `@Autowired` o un `import org.springframework...` dentro de `domain/`, la capa está contaminada y hay que corregirlo.

```
com.fusaroute
├── domain
│   ├── model/            Route, Stop, Neighborhood, Fare, TrafficSector, User, SearchRecord, Feedback
│   └── port
│       ├── in/           interfaces de casos de uso (lo que el mundo puede pedirle al dominio)
│       └── out/          interfaces que el dominio necesita (RouteRepositoryPort, TravelTimePort...)
├── application
│   └── usecase/          implementación de los casos de uso; orquesta dominio + puertos out
└── infrastructure
    └── adapter
        ├── in
        │   └── web/      @RestController, DTOs de request/response, mappers
        └── out
            ├── persistence/  @Entity JPA, repositorios Spring Data, mappers a/desde dominio
            └── maps/         cliente HTTP de Google Maps
```

**Dos modelos distintos, a propósito:** `domain/model/Route` es un objeto de dominio puro; `infrastructure/adapter/out/persistence/RouteJpaEntity` es la tabla. Un mapper los traduce. Duplicar campos aquí es intencional — es lo que permite cambiar de base de datos sin tocar la lógica de negocio, y es exactamente lo que hay que poder explicar ante el Comité de Arquitectura.

**Cómo se agrega una funcionalidad**, en orden:

1. Modelo y reglas en `domain/model/`.
2. Interfaz del caso de uso en `domain/port/in/`; si necesita algo externo, interfaz en `domain/port/out/`.
3. Implementación en `application/usecase/` — solo depende de las interfaces.
4. Adaptador de entrada (controller) y de salida (JPA / cliente HTTP) en `infrastructure/`.
5. Test del caso de uso con mocks de los puertos, sin levantar Spring.

## Dominio inicial

- **`Route`** — ruta de buseta: nombre/número, trazado GeoJSON, barrios por los que pasa, tarifa, estado (activa / suspendida temporalmente). **Sin transbordos** — cada ruta es un recorrido directo de un solo tramo, fuera de alcance combinar varias. Ida y vuelta son dos registros de `Route` independientes, no un mismo registro con "sentido".
- **`Neighborhood`** — barrio o comuna; una ruta atraviesa varios. **Sin paradas formales**: en Fusagasugá uno para la buseta con la mano, como un taxi, así que el sistema no modela paradas fijas. Los barrios de cada ruta son dato cargado a mano y validado por inspección visual contra el GeoJSON — no hay validación geoespacial automática (eso exigiría PostGIS, fuera de alcance).
- **`Fare`** — costo del pasaje. **Dos formas según el tipo de ruta:**
  - Urbana (dentro de Fusagasugá): un único precio fijo por ruta, sin importar dónde se suba o baje el usuario.
  - Intermunicipal (Chinauta, Pasca, Arbeláez): tabla de precios por **punto de referencia de bajada** (ej. "hasta el primer retorno: $X / hasta el Hotel Chinauta Real: $Y"), ordenada de menor a mayor. No es tarifa por parada (no hay paradas) ni por tramo formal — es un punto de referencia geográfico dentro del trazado.
  - Cada tarifa tiene `vigenteDesde`; la pantalla muestra la fecha de última actualización.
  - Tarifas diferenciales (estudiante, adulto mayor) fuera de alcance.
- **`CongestionWindow`** — ventana de congestión histórica de un **tramo** de una ruta (no de la ruta completa), modelada con `horaInicio` y `horaFin` — **no un booleano**, porque el aviso se activa 30 minutos antes de `horaInicio` y se apaga al llegar a `horaFin`, cálculo que un bool no puede sostener. Sin distinción por día de la semana este semestre (misma ventana todos los días).
- **`User`** — usuario final o administrador (rol).
- **`SearchRecord`** — búsqueda guardada en el historial del usuario (origen + destino); se conservan las últimas 6 por usuario, asociadas a la cuenta (no al dispositivo), para que persistan al cambiar de equipo.
- **`Feedback`** — comentario de un usuario (10–100 caracteres, sin URLs, filtrado contra un banco de palabras prohibidas) vinculado a su perfil, y la respuesta del administrador. Editable/borrable por su autor solo mientras esté pendiente de respuesta; una vez respondido, queda bloqueado (protege la métrica de "comentarios pendientes" de RF-13).

**Regla de negocio central:** hay más de 20 rutas y varias sirven para llegar al mismo destino. El caso de uso de búsqueda filtra las rutas candidatas que conectan origen y destino y **el dominio decide cuál es la mejor** por menor tiempo estimado — el tiempo lo informa un puerto de salida `TravelTimePort`, implementado por un adaptador que consulta Google Directions (Google cronometra rutas ya filtradas por el dominio; nunca elige el trazado ni decide cuál es mejor — eso rompería RNF-03 y dejaría `domain/` sin nada que cubrir en JaCoCo). El catálogo de rutas (con su trazado GeoJSON) es nuestro dato propio. Sin ruta candidata que conecte origen-destino, se devuelve un mensaje explícito, no una lista vacía.

**Modo offline:** el backend expone además un endpoint que, sin llamar a Google Maps, **calcula la mejor ruta por distancia geométrica** sobre los GeoJSON del catálogo. La app usa ese endpoint cuando no tiene red. No calcula tiempo de llegada ni trancones offline — solo distancia total.

**Autenticación (RF-01/02/03):** registro con nombre, correo (único, formato validado) y contraseña (mínimo 8 caracteres, 1 mayúscula, 1 número), activación inmediata sin verificación por correo. Login con JWT de validez **una semana** — vencido, el usuario reloguea (RNF-04: máximo 1 login por semana). Hasta 4 intentos fallidos con mensaje genérico; al quinto, bloqueo temporal con aviso explícito de bloqueo (no el mensaje genérico de los anteriores). Recuperación de contraseña fuera de alcance este semestre. Perfil editable (nombre, correo, teléfono, contraseña); cambiar contraseña exige la contraseña actual.

## Reglas técnicas

**Supabase es solo PostgreSQL.** No usar Supabase Auth, Storage ni Realtime — decisión de arquitectura vigente. La autenticación es nuestra: Spring Security + JWT, con contraseñas hasheadas con BCrypt. El backend es el único dueño de la lógica de negocio y de la seguridad.

**Autorización por rol.** Todo endpoint de escritura sobre rutas es exclusivo de administrador. La verificación va en la capa de seguridad, no dispersa en los controllers.

**Secretos, nunca en el repositorio.** Credenciales de Supabase y API key de Google Maps van en variables de entorno. Se versiona `.env.example` con las claves vacías; `.env` y `application-local.yml` van en `.gitignore`. Antes de cualquier commit, verificar que no se cuele una credencial.

**API key de Maps.** La del backend es distinta de la del frontend y no se expone al cliente jamás. **Las llamadas a Google Maps se cachean 5 minutos por par origen-destino** — es parte del diseño de RNF-01, no una optimización opcional. La razón: el cálculo de la ruta depende de Maps, así que el caché sostiene el p95 < 8 s comprometido y reduce el consumo de cuota.

**Errores explícitos.** Nada de `catch` vacíos ni de devolver `null` para disimular un fallo. Códigos HTTP correctos (`400` validación, `401` sin autenticar, `403` sin permiso, `404` no existe) y un cuerpo de error consistente. El docente evalúa fiabilidad con métrica.

## Ambientes y configuración

**Concepto, en una línea:** el código nunca cambia entre ambientes; lo que cambia es cuál archivo de configuración se activa.

| Ambiente | Qué es | Estado hoy |
|---|---|---|
| **DEV** | PostgreSQL en el portátil de cada integrante. Cada quien rompe lo suyo. | **activo** |
| **PRE** | proyecto Supabase con datos de prueba. El ensayo general. | se monta en el Sprint 2 |
| **PROD** | proyecto Supabase con las rutas reales. Lo que ve el comité. | se monta en el Sprint 2 · **no está desplegado en ningún servidor este semestre**: el backend corre desde un portátil el día de la demostración |

```
src/main/resources/
├── application.properties        # común · spring.profiles.active=${SPRING_PROFILE:dev}
├── application-dev.properties    # PostgreSQL local
├── application-pre.properties    # claves declaradas y vacías hasta el Sprint 2
└── application-prod.properties   # claves declaradas y vacías hasta el Sprint 2
```

Los cuatro archivos existen desde ya, para cumplir en estructura con la §22 de la guía de buenas prácticas aunque PRE y PROD todavía no tengan valores.

**Ningún valor se escribe a mano en un `.properties`.** Todos entran por variable de entorno:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
server.port=${PORT:8080}
google.maps.api.key=${GOOGLE_MAPS_API_KEY}
```

`.env.example` está versionado con las claves vacías y **es la documentación ejecutable: si una variable no está ahí, no existe.** `.env` está en `.gitignore` junto con `application-local.*`.

Arranque en DEV, desde un clon limpio:

```bash
cp .env.example .env      # y llenarlo con la contraseña del PostgreSQL local
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# comprobar: GET http://localhost:8080/health  →  {"status":"UP"}
```

Requiere **JDK 25** y **Maven 3.9+** instalados, y un PostgreSQL local con la base `fusaroute_dev` creada.

**Por qué 3.5 y no 4.x:** la línea 4 de Spring Boot ya está publicada y es la que sirve `start.spring.io`, pero es un cambio de versión mayor. Siendo la primera vez del equipo con Spring, el material que van a encontrar buscando un error —tutoriales, respuestas de StackOverflow, ejemplos— es de la línea 3.x, y esa diferencia se paga en horas de depuración. Se arranca en la última 3.x, que no tiene cambios de ruptura, y el salto a 4 queda como decisión propia con su tarjeta, no como algo que se hace a mitad de un sprint.

## Calidad medible (ISO/IEC 25010)

*Nota: Estas métricas deben verificarse contra la Actividad 3 v3.*

| Atributo | Métrica | Cómo se verifica |
|---|---|---|
| Rendimiento | p95 < 8 s end-to-end (cliente → backend → Google Maps → render) en `/api/routes/search` sobre 4G. Caché de 5 min por par origen-destino. | Spring Boot Actuator + Micrometer (percentil 95) sobre el endpoint, midiendo latencia total desde que entra al controller hasta que sale la respuesta |
| Fiabilidad | ≥ 95 % uptime mensual en horario hábil (lun–vie 7:00–21:00), RTO < 1 h lectiva, RPO < 24 h | healthcheck externo + backup diario automatizado de PostgreSQL con restore probado al cierre de sprint |
| Mantenibilidad | 0 violaciones de la regla de dependencia hexagonal · cobertura de `domain/` y `application/` ≥ 70 % | **ArchUnit** en el build (falla el build, no el revisor) + **JaCoCo** con umbral bloqueante; además revisión en PR |
| Seguridad | 0 vulnerabilidades críticas conocidas | auditoría de dependencias |
| Usabilidad de la API | códigos HTTP correctos y errores descriptivos | revisión en PR |

## Instrumentos de la métrica de mantenibilidad

La regla hexagonal no se sostiene con buena voluntad: se sostiene con dos herramientas en el build. **Ninguna de las dos existe todavía — se instalan en el Sprint 1.**

- **ArchUnit** (test de arquitectura en `src/test/java`): comprueba que ninguna clase de `com.fusaroute.domain..` importe `org.springframework..`, `jakarta.persistence..` ni `com.fasterxml.jackson..`, y que la dependencia entre capas apunte siempre hacia adentro. Si alguien contamina el dominio, **falla el build**, no lo tiene que ver un humano en el PR.
- **JaCoCo**: umbral de cobertura **bloqueante** sobre `domain/` y `application/`. Se limita a esos dos paquetes a propósito: son los que contienen lógica de negocio propia y los únicos donde la cobertura significa algo. Cubrir controllers para subir un porcentaje es maquillaje.

Las reglas de ArchUnit se escriben **contra nombres de paquete**. Por eso los nombres de este archivo y los del repositorio tienen que coincidir exactamente: si los nombres mienten, la métrica no se puede implementar.

## Testing

- **Casos de uso:** JUnit 5 + Mockito, mockeando los puertos `out`. Sin `@SpringBootTest` — deben correr rápido y aislados. Es el beneficio concreto de hexagonal, y conviene aprovecharlo desde el primer caso de uso.
- **Adaptadores:** tests de integración solo donde aportan (mapeo JPA, deserialización de la respuesta de Maps).
- Test nuevo con cada caso de uso nuevo, en el mismo PR.

## Git y flujo de trabajo

- `main` protegida. Ramas `feature/SCRUM-N-nombre` o `fix/SCRUM-N-nombre`.
- **Pull Request obligatorio**, revisado por el otro integrante; nadie mergea su propio PR sin revisión. Es donde ambos aprenden el código del otro, que es justo lo que el docente busca.
- Conventional Commits en español con key de Jira: `feat(SCRUM-N): descripción`, `fix(SCRUM-N): descripción`, etc.
- CI en GitHub Actions: build + tests en cada PR. Si el CI falla, no se mergea.
- Backlog en Jira; cada sustentación quincenal ante el comité cierra un hito.

## Convenciones de código

- Código y nombres de variables en **inglés**; comentarios, commits, issues, documentación y README en **español**.
- Nombres explícitos: `FindFastestRouteUseCase`, no `RouteService`. En hexagonal el nombre dice el rol.
- Comentar solo el *por qué* no obvio (una regla del negocio del transporte local, un límite de cuota de la API). Lo que el código ya dice no se comenta.
- Sin capas de compatibilidad ni abstracciones "para después": el proyecto es pequeño y el docente evalúa claridad.
- Diagramas (hexágono, C4) en Mermaid dentro de `README.md` o `/docs`, versionados con el código.
