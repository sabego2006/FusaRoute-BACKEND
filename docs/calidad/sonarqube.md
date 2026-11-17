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

## Hallazgos aceptados con justificación

Regla del equipo: un hallazgo de seguridad se corrige en código. Solo se acepta ("Accept" en
SonarCloud) cuando, tras intentar al menos dos correcciones en código y medirlas en el análisis
del PR, resulta ser un falso positivo. La justificación vive aquí, no solo en el comentario de
SonarCloud, y el informe de la tanda lo declara como desviación.

## Cambios de seguridad revisados sin hallazgo nuevo

Cuando un PR toca código de autenticación/autorización, esta sección registra que se revisó
contra SonarCloud, aunque no haya hallazgo que aceptar — para que este documento siga
describiendo el estado de seguridad real del código, no solo las excepciones.

### SCRUM-196 — dispositivo de confianza del segundo factor (17-nov-2026)

`LoginService`, `JwtTrustTokenAdapter` y `User#trustFingerprintSource()` (PR #99) agregan la
regla "un USER no repite el TOTP por 15 días en el mismo dispositivo; un ADMIN siempre lo
repite". El punto sensible es `JwtTrustTokenAdapter.fingerprint()`, que usa
`MessageDigest.getInstance("SHA-256")` sobre el hash de la contraseña y el secreto TOTP
vigentes — nunca los valores en claro — para construir la huella de invalidación del claim
`fp`. Verificado contra la API de SonarCloud sobre el análisis real del PR #99 (no solo el
badge del check, que es informativo por diseño mientras `sonar.qualitygate.wait=false`):

```
GET sonarcloud.io/api/hotspots/search?projectKey=sabego2006_FusaRoute-BACKEND&pullRequest=99 → 0 hotspots
GET sonarcloud.io/api/issues/search?componentKeys=sabego2006_FusaRoute-BACKEND&pullRequest=99&types=VULNERABILITY → 0 issues
```

Cero hallazgos nuevos: ni el uso de SHA-256 (no es `java:S4790`, que marca MD5/SHA-1 para
contraseñas, no un hash de un solo sentido sobre datos ya de alta entropía) ni el resto del
cambio dispararon nada. No aplica el hallazgo `java:S4502` de abajo: la premisa de ese
hallazgo (sin cookies de sesión, JWT por header) sigue intacta porque el `trustToken` también
viaja en el cuerpo JSON, nunca en una cookie.

### java:S4502 — `SecurityConfig.java`, `csrf.disable()` (SCRUM-193, 2026-11-15)

**Qué dice la regla.** "Make sure disabling Spring Security's CSRF protection is safe here."

**Por qué no aplica.** CSRF (cross-site request forgery) explota que el navegador adjunta
solo las credenciales a cada petición: una cookie de sesión. Este backend no emite ninguna:
`SessionCreationPolicy.STATELESS`, sin `formLogin`, sin `httpBasic`, y la autenticación es un
JWT que el frontend manda en el header `Authorization: Bearer`. Un formulario o un `fetch` desde
otro sitio no puede poner ese header, así que no hay petición falsificable que proteger. La
referencia de Spring Security 6 ("Disable CSRF Protection", sección *Mobile Applications*) lo
dice así: *"A backend application that does not serve browser traffic may choose to disable
CSRF."* `SecurityConfigCsrfTest` fija la premisa: el login no entrega `Set-Cookie` y una
petición con JWT tampoco abre sesión. Si algún día se introduce una cookie de sesión, ese test
falla y esta decisión se revisa.

**Qué se intentó en código, y qué midió SonarCloud (PR #97).**

| Intento | Código | Resultado del análisis |
|---|---|---|
| 1 | `csrf.ignoringRequestMatchers("/api/**")` | sigue marcado (S4502, misma línea) |
| 2 | `csrf.requireCsrfProtectionMatcher(request -> false)` | sigue marcado (S4502, misma línea) |

La implementación de la regla marca **cualquier** llamada a `disable`, `ignoringAntMatchers`,
`ignoringRequestMatchers` o `requireCsrfProtectionMatcher` sobre `CsrfConfigurer`. No existe
forma en código de decir "CSRF no aplica" que la versión desplegada no marque; la única
"corrección" que la calla es activar CSRF con tokens en cookie, que en una API Bearer
cross-origin no protege nada y obligaría a cambiar el flujo de login en los dos repos.

**Por qué es un falso positivo reconocido.** SonarSource lo aceptó: el commit
`b1141386` de `sonar-java` (2026-11-12, ticket SONARJAVA-6615, *"S4502 should not raise when
the app is stateless with token-based auth"*) deja de marcar `csrf.disable()` cuando el bean
`SecurityFilterChain` usa `oauth2ResourceServer`, `SessionCreationPolicy.STATELESS` y no tiene
autenticación de navegador, que es exactamente la forma de `SecurityConfig.filterChain`. La
última release publicada al cerrar esto (8.46.0, 2026-10-29) aún no lo incluye; cuando
SonarCloud despliegue la siguiente, el hallazgo desaparece solo, sin tocar código.

**Cómo queda marcado.** El issue de la rama `main` se marca **Accept** con este texto resumido y
el enlace a esta sección. Al mergear el PR, el issue de la rama hereda la resolución.

## Retiro del SonarQube autohospedado

El montaje autohospedado (`infra/sonarqube/`) se borró del repo. Si llegó a crearse la VM de
Oracle, darla de baja junto con su IP pública, y borrar el secret `SONAR_HOST_URL` del repo si
sigue ahí (el CI ya no lo lee).
