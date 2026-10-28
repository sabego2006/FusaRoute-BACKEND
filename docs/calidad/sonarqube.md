# SonarCloud — análisis estático de calidad

SCRUM-181 · RNF-05 (analizador estático en CI en cada PR).

## Qué es, y por qué así

**SonarCloud** (SonarQube Cloud), para backend y frontend. El 2026-10-27 se había decidido
autohospedar SonarQube Community en una VM de Oracle; el 2026-10-28 se revirtió y se
unificó todo en SonarCloud. Razones:

- **Cero infraestructura que mantener**: sin VM, sin certificado HTTPS, sin parches, sin
  uptime que vigilar. Para un equipo de dos ante sustentación, la VM era más riesgo que valor.
- **Decora PRs y analiza por rama.** SonarQube Community autohospedado no hace ninguna de
  las dos (son features de pago), y encajan con el flujo de `main` protegida con revisión
  obligatoria.
- **Un solo tablero** para los dos repos. Ambos `projectKey` ya existen:
  `sabego2006_FusaRoute-BACKEND` y `sabego2006_FusaRoute-FRONTEND`.

**Costo y condición:** el tier gratuito de SonarCloud exige que el repo sea **público**.

| Pieza | Dónde |
|---|---|
| Servidor y tablero | SonarCloud (organización `sabego2006`) |
| Analizador | Runners normales de GitHub Actions (`ubuntu-latest`) |
| Quality Gate | **Informativo** al inicio (`sonar.qualitygate.wait=false`): `domain/` y `application/` aún tienen poco código; un gate bloqueante hoy pondría todo PR rojo |

## Prerequisitos (una sola vez, los hace Santiago como admin)

El paso de análisis en CI queda **SKIPPED** hasta completar esto. Nada rompe mientras tanto.

### 1. Importar el repo

- El repo `FusaRoute-BACKEND` debe ser **público**.
- sonarcloud.io → **+** → *Analyze new project* → organización `sabego2006` → elegir
  `FusaRoute-BACKEND` → método **With GitHub Actions** (no *Automatic Analysis*).

### 2. Apagar el análisis automático

Proyecto → **Administration → Analysis Method** → desactivar *Automatic Analysis*. Si queda
activo choca con el análisis del CI y SonarCloud rechaza uno de los dos.

### 3. Crear y guardar el token

- SonarCloud → *My Account → Security* → generar un token → copiarlo.
- GitHub → repo BACKEND → Settings → Secrets and variables → Actions → New repository secret:
  `SONAR_TOKEN` = el token. **No** hay `SONAR_HOST_URL`: el host va fijo en el workflow.
  Si quedó el secret `SONAR_HOST_URL` de la VM, se puede borrar; el CI ya no lo lee.

### 4. Confirmar la organización

`sonar.organization` está en `pom.xml` como `sabego2006`. Debe ser idéntica a la
**organization key** real en SonarCloud; si difiere, el análisis falla con *"project not
found"*. Corregirlo es una línea en el `pom.xml`.

El primer push o PR a `main` hace que el paso **Análisis SonarCloud** corra. El badge del
README resuelve tras ese primer análisis.

## Cómo encaja con el CI actual

El job `build-and-test` de `.github/workflows/ci.yml` sigue haciendo `mvn -B verify` (que ya
genera `target/site/jacoco/jacoco.xml` vía el plugin de JaCoCo). Tras eso corre
`sonar-maven-plugin:5.8.0.7211:sonar` con `-Dsonar.host.url=https://sonarcloud.io` y
`-Dsonar.token=$SONAR_TOKEN`. La propiedad `sonar.coverage.jacoco.xmlReportPaths` está en el
`pom.xml` apuntando al XML que ya existe.

El host va **explícito** a propósito: `sonar-maven-plugin` sin `sonar.host.url` cae a
`http://localhost:9000`, no a SonarCloud, y fallaría sin avisar por qué.

El paso del scanner es **bloqueante** (no `continue-on-error`) **con guard**
`if: env.SONAR_TOKEN != ''`:

- Sin token → paso **SKIPPED**, CI verde.
- Con token → paso **corre**; cualquier fallo (red, Java 25 no soportado, token inválido)
  deja el PR en rojo. Es a propósito: el CI tiene que avisar.

## Plan B: Java 25 en el analyzer

SonarQube 2026.1 LTA (y las Community Build posteriores) soportan Java 25 oficialmente.
Verificado en los docs oficiales el 2026-10-27; **no verificado contra SonarCloud**. Si el
primer run falla por versión de Java:

- Añadir al pom o al goal: `-Dsonar.java.source=25 -Dsonar.java.binaries=target/classes`.

## Volver el gate bloqueante (cuando corresponda)

Cuando `domain/` y `application/` tengan código real y el gate verde sea alcanzable:

1. En SonarCloud, Quality Gates → ajustar umbrales si hace falta.
2. En `ci.yml`, cambiar `qualitygate.wait=false` a `qualitygate.wait=true`.
3. Ahora un gate rojo tumba el PR.

## VM de Oracle y `infra/sonarqube/`

El montaje autohospedado (`infra/sonarqube/compose.yaml` y `.env.example`) queda **obsoleto**.
Se conserva en este PR para no mezclar la migración con un borrado. Pasos pendientes, tras
confirmar que el análisis en SonarCloud queda verde:

1. Dar de baja la VM de Oracle (si llegó a crearse) y su IP pública.
2. Borrar `infra/sonarqube/` en un commit aparte.
3. Borrar el secret `SONAR_HOST_URL` del repo si sigue ahí.
