# SonarQube — análisis estático de calidad

SCRUM-181 · RNF-05 (analizador estático en CI en cada PR).

## Qué es, y por qué así

**SonarQube Community autohospedado en Docker**, no SonarCloud. El ticket original pedía
SonarCloud, pero la decisión del 2026-10-27 fue montarlo **únicamente desde Docker**:
tablero propio, control total, sin dependencia de un SaaS externo.

**Arquitectura elegida** (porque "afecte el CI + tablero persistente + no pesado + gratis"
no se sostienen los cuatro a la vez con cualquier combinación):

| Pieza | Dónde | Por qué |
|---|---|---|
| Servidor SonarQube + Postgres | VM **Always-Free de Oracle Cloud** (ARM Ampere A1, Ubuntu), con `docker compose` | Gratis real 24/7 con IP pública; 4 OCPU / 24 GB ARM alcanzan de sobra para SonarQube Community. Es lo más cercano a "Cloud" sin pagar |
| Analizador | Runners normales de GitHub Actions (`ubuntu-latest`) | Siempre disponibles; apuntan al servidor por URL pública |
| Quality Gate | **Informativo** al inicio (`sonar.qualitygate.wait=false`) | `domain/`/`application/` aún tienen poco código; un gate bloqueante hoy pondría todo PR rojo. Se vuelve `wait=true` cuando haya código real |

**Limitación conocida de Community Edition:** no analiza por rama ni decora PRs (son
features de pago, Developer Edition en adelante). El tablero refleja el último commit
analizado, no un diff por PR. Aceptable para la sustentación; no equivale a SonarCloud.

## Prerequisitos (una sola vez, los hace Santiago como admin)

El paso de análisis en CI queda **SKIPPED** hasta completar esto. Nada rompe mientras tanto.

### 1. Crear la VM

- Oracle Cloud → Compute → Instances → Create instance.
- Shape: **VM.Standard.A1.Flex** (ARM Ampere Always-Free). 2 OCPU / 12 GB es suficiente.
- Imagen: Ubuntu LTS reciente.
- Guardar la IP pública.
- **Si falla por "Out of host capacity":** probar otra región Always-Free. Es común.

### 2. Endurecer el host

```bash
# vm.max_map_count lo exige Elasticsearch (dentro de SonarQube).
sudo tee /etc/sysctl.d/99-sonarqube.conf <<'EOF'
vm.max_map_count=262144
fs.file-max=131072
EOF
sudo sysctl --system

# Docker + compose plugin.
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER
# Cerrar y reabrir sesión para que el grupo tome efecto.
```

### 3. Subir el compose y arrancar

```bash
# Copiar infra/sonarqube/ a la VM (scp o git clone de este repo).
cd infra/sonarqube
cp .env.example .env
# Rellenar SONAR_DB_PASSWORD con una contraseña fuerte:
#   openssl rand -base64 32
docker compose up -d
docker compose logs -f sonarqube   # esperar "SonarQube is operational" (~2 min)
```

### 4. Primer login + endurecimiento obligatorio

- Entrar a `http://<ip-publica>:9000` solo desde un túnel SSH (`ssh -L 9000:localhost:9000 ubuntu@<ip>`)
  o desde la red local. **El puerto 9000 no debe quedar expuesto al mundo.**
- Login: `admin` / `admin`. **Cambiar la contraseña inmediatamente.**
- Crear el proyecto en SonarQube con **Project Key** = `sabego2006_FusaRoute-BACKEND`.
- Generar un **token de análisis** (My Account → Security). Guardarlo.

### 5. HTTPS pública (obligatorio antes de abrir al mundo)

Opciones realistas:

- **Caddy** delante del contenedor (lo más corto): un `Caddyfile` con `sonar.<tu-dominio>`
  y `reverse_proxy localhost:9000`. Caddy saca el certificado de Let's Encrypt solo.
- **Cloudflare Tunnel**: cero puertos abiertos al mundo, HTTPS automático.

Firewall / Security List de Oracle: abrir **solo 443** (y 80 si usas Let's Encrypt HTTP-01)
hacia Internet; **9000 nunca**.

### 6. Guardar los secrets del CI

GitHub → repo → Settings → Secrets and variables → Actions → New repository secret:

- `SONAR_TOKEN` = el token del paso 4.
- `SONAR_HOST_URL` = la URL HTTPS pública (por ejemplo `https://sonar.fusaroute.dev`).

El primer push a cualquier rama hará que el paso **Análisis SonarQube** del CI corra
contra la VM. El badge del README resolverá tras ese primer análisis.

## Cómo encaja con el CI actual

El job `build-and-test` de `.github/workflows/ci.yml` sigue haciendo `mvn -B verify`
(que ya genera `target/site/jacoco/jacoco.xml` vía el plugin de JaCoCo). Tras eso corre
`sonar-maven-plugin:5.8.0.7211:sonar` con `-Dsonar.host.url=$SONAR_HOST_URL` y
`-Dsonar.token=$SONAR_TOKEN`. La propiedad `sonar.coverage.jacoco.xmlReportPaths` está en
el `pom.xml` apuntando al XML que ya existe; Sonar lo ingiere sin duplicar configuración.

El paso del scanner es **bloqueante** (no `continue-on-error`) **con guard** `if: env.SONAR_TOKEN != '' && env.SONAR_HOST_URL != ''`:

- Sin secrets → paso **SKIPPED**, CI verde.
- Con secrets → paso **corre**; cualquier fallo (red, Java 25 no soportado, token
  inválido, VM caída) deja el PR en rojo. Es a propósito: el CI tiene que avisar.

## Plan B: Java 25 en el analyzer

SonarQube 2026.1 LTA (y las Community Build posteriores) soportan Java 25 oficialmente.
Verificado en los docs oficiales el 2026-10-27. Si el primer run falla por versión de Java:

- Añadir al pom o al goal: `-Dsonar.java.source=25 -Dsonar.java.binaries=target/classes`.
- Como último recurso, subir la imagen a la **última** Community Build en Docker Hub (ya
  hay una del 2026-10 pineada en `compose.yaml`; actualizarla sabiendo que exige
  migración de BD).

## Volver el gate bloqueante (cuando corresponda)

Cuando `domain/` y `application/` tengan código real y el gate verde sea alcanzable:

1. En SonarQube, Quality Gates → ajustar umbrales si hace falta.
2. En `ci.yml`, cambiar `qualitygate.wait=false` a `qualitygate.wait=true`.
3. Ahora un gate rojo tumba el PR.

## Reset / mantenimiento

- **Backup del Postgres de SonarQube:** `docker compose exec db pg_dump -U sonar sonar > backup.sql`.
  Nada crítico (el análisis se reconstruye desde el repo), pero el histórico y las
  revisiones de hotspots se pierden si se tira el volumen sin backup.
- **Actualización mayor:** NO hacerla sin leer la guía de upgrade de SonarQube para la
  versión destino. Las versiones mayores exigen pasos de migración; por eso el tag está
  pineado en `compose.yaml`.
