# FusaRoute — Backend

API REST del sistema de información de transporte público de Fusagasugá. Proyecto Integrador de Ingeniería de Software I, Universidad de Cundinamarca (docente: Ing. Luiferney Ortiz Parra).

**Recursos externos:** la carpeta del curso en OneDrive (`C:/Users/Santiago/OneDrive - UNIVERSIDAD DE CUNDINAMARCA/Universidad/5 SEMESTRE/INGENIERIA SOFTWARE I`) contiene la Actividad 3 v3 y el material de clase.

El contexto completo del curso, el alcance del proyecto y las métricas de calidad comprometidas están en el `CLAUDE.md` de la carpeta madre de la asignatura.

**Equipo:** Santiago Bermúdez · Angélica Aranguren. Ambos trabajan todo el stack por exigencia del docente. Es la primera vez del equipo con Spring Boot, así que las convenciones de este archivo se explican, no solo se enuncian.

## Stack

- Java 21 + Spring Boot 3.x, Maven
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
com.fusaroute.backend
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

- **`Route`** — ruta de buseta: nombre/número, secuencia ordenada de paradas, barrios y comunas por los que pasa, tarifa, estado (activa / suspendida temporalmente).
- **`Stop`** — parada con coordenadas.
- **`Neighborhood`** — barrio o comuna; una ruta atraviesa varios.
- **`Fare`** — costo del pasaje, con historial (las tarifas cambian por año).
- **`TrafficSector`** — sector propenso a trancón, con las franjas horarias en que se congestiona. Base del aviso de hora pico.
- **`User`** — usuario final o administrador (rol).
- **`SearchRecord`** — búsqueda guardada en el historial del usuario; incluye la ruta que el usuario deje fijada para una hora específica.
- **`Feedback`** — mensaje de la caja de comentarios y la respuesta del administrador.

**Regla de negocio central:** hay más de 20 rutas y varias sirven para llegar al mismo destino. El caso de uso de búsqueda **simula cada ruta en Google Maps** y devuelve la de menor tiempo estimado. El catálogo de rutas (con sus paradas y trazado GeoJSON) es nuestro dato propio: ahí viven las polilíneas que el backend envía a Google Maps para pedirle la estimación de tiempo por ruta. **No** se compara un banco estático de tiempos — los tiempos se calculan en cada consulta a Maps.

**Modo offline:** el backend expone además un endpoint que, sin llamar a Google Maps, **calcula la mejor ruta por distancia geométrica** sobre los GeoJSON del catálogo. La app usa ese endpoint cuando no tiene red. No calcula tiempo de llegada ni trancones offline — solo distancia total.

## Reglas técnicas

**Supabase es solo PostgreSQL.** No usar Supabase Auth, Storage ni Realtime — decisión de arquitectura vigente. La autenticación es nuestra: Spring Security + JWT, con contraseñas hasheadas con BCrypt. El backend es el único dueño de la lógica de negocio y de la seguridad.

**Autorización por rol.** Todo endpoint de escritura sobre rutas es exclusivo de administrador. La verificación va en la capa de seguridad, no dispersa en los controllers.

**Secretos, nunca en el repositorio.** Credenciales de Supabase y API key de Google Maps van en variables de entorno. Se versiona `.env.example` con las claves vacías; `.env` y `application-local.yml` van en `.gitignore`. Antes de cualquier commit, verificar que no se cuele una credencial.

**API key de Maps.** La del backend es distinta de la del frontend y no se expone al cliente jamás. **Las llamadas a Google Maps se cachean 5 minutos por par origen-destino** — es parte del diseño de RNF-01, no una optimización opcional. La razón: el cálculo de la ruta depende de Maps, así que el caché sostiene el p95 < 8 s comprometido y reduce el consumo de cuota.

**Errores explícitos.** Nada de `catch` vacíos ni de devolver `null` para disimular un fallo. Códigos HTTP correctos (`400` validación, `401` sin autenticar, `403` sin permiso, `404` no existe) y un cuerpo de error consistente. El docente evalúa fiabilidad con métrica.

## Calidad medible (ISO/IEC 25010)

*Nota: Estas métricas deben verificarse contra la Actividad 3 v3.*

| Atributo | Métrica | Cómo se verifica |
|---|---|---|
| Rendimiento | p95 < 8 s end-to-end (cliente → backend → Google Maps → render) en `/api/routes/search` sobre 4G. Caché de 5 min por par origen-destino. | Spring Boot Actuator + Micrometer (percentil 95) sobre el endpoint, midiendo latencia total desde que entra al controller hasta que sale la respuesta |
| Fiabilidad | ≥ 95 % uptime mensual en horario hábil (lun–vie 7:00–21:00), RTO < 1 h lectiva, RPO < 24 h | healthcheck externo + backup diario automatizado de PostgreSQL con restore probado al cierre de sprint |
| Mantenibilidad | 0 violaciones de la regla de dependencia hexagonal | revisión en PR; `domain/` sin imports de framework |
| Seguridad | 0 vulnerabilidades críticas conocidas | auditoría de dependencias |
| Usabilidad de la API | códigos HTTP correctos y errores descriptivos | revisión en PR |

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
