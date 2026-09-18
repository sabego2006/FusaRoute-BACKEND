---
name: fusaroute-context-sync
description: Propaga una decisión de FusaRoute (cambio de versión de stack, arquitectura, alcance, métrica ISO 25010, ambientes/despliegue, convención de código, o resultado de Sprint Planning) a los 3 repos del proyecto (ing-software-I, FusaRoute-BACKEND, FusaRoute-FRONTEND) para que sus CLAUDE.md no queden desincronizados. Úsala explícitamente cuando el usuario pida "actualiza el contexto de FusaRoute", "sincroniza los CLAUDE.md", "propaga esto a los tres repos" o "/fusaroute-sync". Úsala también proactivamente, sin que el usuario la pida, en cuanto una conversación sobre FusaRoute cierre o confirme una decisión que pertenece a más de un repo — aunque el usuario solo esté hablando de un repo a la vez, si lo que acaba de decidir contradice o desactualiza lo que dice otro CLAUDE.md, dispara esta skill antes de dar la conversación por cerrada. Ejemplos de disparo proactivo: "subamos a Spring Boot 4", "ya cerramos el Sprint Planning en Jira", "cambiamos el umbral de p95 a...", "decidimos desplegar el backend en Render", "pasamos de RxJS a signals". No la uses para ediciones de código que no cambian ninguna decisión documentada, ni para tocar docs/actividad3/ (el entregable ya presentado al docente).
---

# fusaroute-context-sync

## Por qué existe

FusaRoute vive en 3 repos git separados, cada uno con su propio `CLAUDE.md`. El 2026-09-17
se encontró que `FusaRoute-FRONTEND/CLAUDE.md` llevaba semanas describiendo convenciones de
código de React (hooks, `useState`) sobre un proyecto que ya era Angular real desde el
scaffolding, y declaraba "Angular 18" cuando el `package.json` real decía 21.
`FusaRoute-BACKEND/CLAUDE.md` seguía prometiendo un RTO de servidor 24/7 que una decisión de
despliegue posterior (Render tier gratis) ya había descartado en el repo de documentación.
Ningún repo se había tocado cuando la decisión cambió en otro.

Esta skill existe para que una decisión tomada en una conversación se refleje en los 3 repos
en el mismo momento, no que se descubra semanas después por accidente. No es una skill de
"generar documentación" — es una skill de consistencia: detecta dónde el texto ya no coincide
con la realidad (otro repo, o el código mismo) y lo corrige ahí.

## Las 3 fuentes de verdad

| Repo | Rol | Archivos que le conciernen |
|---|---|---|
| `ing-software-I` | Fuente de verdad transversal: alcance, stack completo, arquitectura hexagonal, las 5 métricas ISO/IEC 25010, decisiones de arquitectura, backlog, Jira, metodología/roles/sprints | `CLAUDE.md` (raíz), `docs/backlog/historias-rf01-rf15.md`, `docs/backlog/requisitos-no-funcionales.md`, `docs/guia-jira-fusaroute.md` |
| `FusaRoute-BACKEND` (`C:/Users/Santiago/dev/FusaRoute/FusaRoute-BACKEND`) | Refleja únicamente lo que le aplica del repo 1: stack backend (Java/Spring Boot), reglas hexagonales, sus métricas, sus ambientes/despliegue | `CLAUDE.md` |
| `FusaRoute-FRONTEND` (`C:/Users/Santiago/dev/FusaRoute/FusaRoute-FRONTEND`) | Refleja únicamente lo que le aplica del repo 1: stack frontend (Angular/TypeScript), sus métricas, sus convenciones de código | `CLAUDE.md` |

BACKEND y FRONTEND nunca inventan una decisión — solo heredan y detallan lo que ya está
decidido en `ing-software-I`. Si BACKEND o FRONTEND dicen algo que `ing-software-I` no dice o
contradice, gana `ing-software-I` (o se marca como pendiente de decidir ahí primero).

## Procedimiento

Sigue este orden. No te saltes el paso 1 aunque creas recordar el contenido de la conversación
— los archivos pueden haber cambiado desde la última vez que los leíste en esta sesión.

### 1. Leer el estado actual real de los 6 archivos

Lee, en este orden:

1. `ing-software-I/CLAUDE.md` (la raíz del repo de documentación — es la carpeta madre del curso)
2. `ing-software-I/docs/backlog/historias-rf01-rf15.md`
3. `ing-software-I/docs/backlog/requisitos-no-funcionales.md`
4. `ing-software-I/docs/guia-jira-fusaroute.md`
5. `FusaRoute-BACKEND/CLAUDE.md`
6. `FusaRoute-FRONTEND/CLAUDE.md`

No asumas contenido por memoria de la conversación. Si alguno de los 3 repos de código no
existe todavía en el filesystem (por ejemplo, si el scaffolding inicial no se ha hecho),
dilo y detente para ese repo en vez de inventar su CLAUDE.md.

### 2. Identificar qué cambió y a quién le pertenece

A partir de la decisión que disparó la skill (lo que se acaba de decidir o confirmar en la
conversación), identifica:

- Si el cambio es transversal (stack, arquitectura, alcance, métrica, ambientes, Jira/sprint):
  primero confirma que `ing-software-I/CLAUDE.md` (o el archivo de backlog/Jira que corresponda)
  ya lo refleja. Si no lo refleja, ese es el primer archivo a corregir — antes de tocar BACKEND
  o FRONTEND, porque ellos heredan de ahí.
- Si el cambio se originó en una conversación de código dentro de BACKEND o FRONTEND (ej. una
  convención de código nueva), igual debe quedar reflejado en `ing-software-I` si ese repo lo
  menciona (aunque sea de forma general), y confirmado — no necesariamente expandido — en el
  otro repo de código si aplica un principio análogo.

### 3. Detectar las 3 clases de desalineación

No busques solo "texto desactualizado" — compara activamente contra evidencia real:

**a. Números que ya no coinciden.** Versión de Java, Spring Boot, Angular, TypeScript, Node;
rangos de keys de Jira; fechas de sprint; umbrales de métricas (p95, % cobertura, etc.).
Compara el número que dice cada CLAUDE.md contra el que dicen los otros 5 archivos y, si el
repo de código existe, contra su `package.json` / `pom.xml` real.

**b. Convenciones de código que describen el paradigma equivocado.** Esto es lo que pasó con
React-en-Angular: el CLAUDE.md puede llevar mucho tiempo sin que nadie lo lea de nuevo. Si vas
a tocar `FusaRoute-BACKEND/CLAUDE.md` o `FusaRoute-FRONTEND/CLAUDE.md`, revisa con `ls` la
estructura real de carpetas y, si hace falta, abre 1-2 archivos de código representativos
(un componente, un controller) para confirmar que las convenciones descritas coinciden con lo
que el código realmente hace — no solo con lo que el `package.json` declara como dependencia.

**c. Afirmaciones que se contradicen entre repos.** Ej. un repo dice "no desplegado este
semestre" y otro ya registró la decisión de desplegar; un repo describe una métrica con un
umbral y otro la describe con otro. Estas son las más fáciles de pasar por alto porque cada
archivo, leído solo, parece coherente consigo mismo.

### 4. Aplicar los cambios con Edit

Reglas duras (vienen del `CLAUDE.md` raíz global de Santiago y del `CLAUDE.md` del repo de
documentación — no se negocian):

- Código y nombres de variables/clases/archivos de código en inglés; comentarios de código,
  mensajes de commit, PRs, issues y documentación en español.
- Un número de versión desactualizado es peor que no ponerlo: si algo cambió, corrígelo en
  todos los CLAUDE.md que lo mencionen y en el README del repo afectado si aplica, en el mismo
  pase — no dejes que un repo quede corregido y otro no.
- Nunca inventes una decisión ni rellenes un hueco con una suposición. Si te falta información
  para saber qué poner (ej. no sabes el umbral real que se decidió), señálalo como pendiente en
  el propio texto o en tu reporte final — el principio de realismo del usuario es no prometer
  ni afirmar lo que no se puede sostener con evidencia real.
- No toques contenido que no esté relacionado con el cambio que estás propagando. Esta skill
  corrige desalineación puntual, no reescribe el archivo.
- No toques `docs/actividad3/` salvo que el usuario lo pida explícitamente — ese entregable ya
  se presentó al docente y tiene su propio flujo de respaldo (ver `CLAUDE.md` del repo de
  documentación).

### 5. Commit/PR por cada repo tocado

Cada repo tiene su propia convención — respétala:

- Mensaje de commit en español, Conventional Commits (`docs:`, `chore:`, etc.). Si el cambio
  está ligado a un issue de Jira, incluye la key `SCRUM-N` en el commit.
- **Nunca commit directo a `main`** en `FusaRoute-BACKEND` ni `FusaRoute-FRONTEND` — son las
  ramas protegidas y requieren revisión de Angélica o Santiago (el que no está en la sesión).
  Crea una rama nueva (usa la convención `docs/SCRUM-N-nombre-corto` o `chore/...` si no hay
  key de Jira) y abre un PR.
- En `ing-software-I`, revisa primero si la sesión actual ya está en un worktree con un PR
  abierto para este mismo tema — si es así, añade el commit ahí en vez de abrir un PR nuevo.
  Si no hay uno abierto, sigue la misma disciplina de rama + PR que en los repos de código.
- **Nunca mergees el PR** — ni con `--admin` ni saltando la protección de rama de ninguna otra
  forma. Eso es decisión de Santiago y Angélica. Deja el PR abierto y dilo explícitamente en tu
  reporte final.

### 6. Reportar

Cierra con un resumen corto, repo por repo:

- Qué se detectó (o "sin cambios, ya estaba sincronizado" si un repo no necesitaba tocarse).
- Qué se cambió exactamente en cada uno (o el link del PR si abriste uno).
- Qué quedó pendiente por falta de información, y qué quedó pendiente porque es una decisión
  que le corresponde al usuario (nunca la tomes tú).

## Qué NO hacer

- No mergear PRs, bajo ninguna circunstancia ni argumento.
- No decidir por tu cuenta una decisión de arquitectura, alcance o métrica que no se te dio
  explícitamente en la conversación — tu trabajo es propagar y mantener consistencia entre lo
  que ya se decidió, no generar contenido nuevo.
- No tocar `docs/actividad3/` sin pedido explícito.
- No asumir que un repo de código existe o tiene cierto contenido sin `ls`/`Read` real — sobre
  todo si ha pasado tiempo desde la última sesión que tocó ese repo.
