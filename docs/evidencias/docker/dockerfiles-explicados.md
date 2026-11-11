# Dockerfile del backend y del frontend, explicados línea por línea

Texto listo para las secciones 16.2 (Dockerfile del backend) y 16.3 (Dockerfile del frontend)
del documento final. Los fragmentos son los archivos reales de cada repositorio; si un
`Dockerfile` cambia, esta explicación se actualiza en el mismo PR.

Los dos siguen el mismo patrón, **construcción en dos etapas** (*multi-stage build*): una
primera imagen con todas las herramientas compila, y la imagen final copia solo el
resultado. La imagen que se ejecuta no lleva compilador, gestor de paquetes ni código
fuente: pesa menos, arranca más rápido y expone menos superficie de ataque.

## 16.2 Dockerfile del backend (`FusaRoute-BACKEND/Dockerfile`)

### Etapa 1: compilar el jar

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build
```

Imagen oficial de Maven 3.9 sobre el JDK 25 de Eclipse Temurin, la misma versión de Java que
declara el `pom.xml`. `AS build` le da nombre a la etapa para poder copiar desde ella después.
El repositorio no tiene *wrapper* de Maven (`mvnw`), así que esta línea es también la que fija
la versión de Maven con la que se empaqueta.

```dockerfile
WORKDIR /build
```

Directorio de trabajo dentro de la imagen; las rutas relativas de las instrucciones siguientes
parten de aquí.

```dockerfile
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
```

Se copia **solo** el `pom.xml` y se descargan las dependencias. Docker guarda cada instrucción
como una capa y la reutiliza mientras sus entradas no cambien: como el `pom.xml` cambia mucho
menos que el código, la descarga de Maven Central queda en caché y un cambio de código no la
repite. `-B` (*batch*) quita la interacción y los colores; `-q` reduce el ruido del log.

```dockerfile
COPY src src
RUN mvn -B package -DskipTests -Dcheckstyle.skip -Djacoco.skip=true
```

Ahora sí entra el código y se empaqueta el jar ejecutable de Spring Boot. Se omiten a propósito
las pruebas, Checkstyle y JaCoCo: la compuerta de calidad es el CI de GitHub Actions
(`mvn -B verify`), que ya corrió antes del *merge* a `main`. Repetirla en cada construcción de
la imagen solo gastaría minutos de *build* del plan gratuito de Render.

### Etapa 2: imagen de ejecución

```dockerfile
FROM eclipse-temurin:25-jre
WORKDIR /app
```

La imagen final parte del **JRE** (solo el entorno de ejecución de Java), no del JDK ni de
Maven. Todo lo de la etapa 1 que no se copie explícitamente se descarta.

```dockerfile
RUN useradd --system --no-create-home --shell /usr/sbin/nologin fusaroute
COPY --from=build /build/target/fusaroute-backend-*.jar app.jar
USER fusaroute
```

Se crea un usuario de sistema sin directorio personal ni *shell*, se copia el jar desde la
etapa `build` y se cambia a ese usuario. El proceso **no corre como root**: si alguien lograra
ejecutar código dentro del contenedor, no tendría privilegios de administrador.

```dockerfile
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
```

Ajustes de la JVM pensados para la instancia gratuita de Render (512 MB de RAM y 0,1 CPU):

- `MaxRAMPercentage=75`: el *heap* usa como máximo el 75 % de la memoria del contenedor y deja
  el resto para la memoria no administrada de la JVM.
- `UseSerialGC`: el recolector de basura que menos memoria y CPU consume con un solo núcleo.
- `ExitOnOutOfMemoryError`: si la memoria se agota, el proceso termina y la plataforma reinicia
  el contenedor, en vez de dejar una JVM respondiendo a medias (factor 9, desechabilidad).

```dockerfile
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`EXPOSE` documenta el puerto en el que escucha la aplicación; el puerto real lo decide la
variable `PORT` (`server.port=${PORT:8080}` en `application.properties`), que Render inyecta
(factor 7, vinculación de puertos). `ENTRYPOINT` en forma de arreglo ejecuta `java` como
proceso principal del contenedor, sin un *shell* intermedio, de modo que recibe directamente
la señal de apagado.

**Configuración:** la imagen no contiene ninguna credencial. El `.dockerignore` deja fuera el
`.env`, y la base de datos, el secreto del JWT y las claves llegan como variables de entorno
al ejecutar el contenedor (factor 3).

## 16.3 Dockerfile del frontend (`FusaRoute-FRONTEND/Dockerfile`)

### Etapa 1: compilar el bundle

```dockerfile
FROM node:22-alpine AS build
WORKDIR /build
```

Node 22 sobre Alpine Linux. Es la misma versión mayor que fijan `.nvmrc`, el campo `engines`
del `package.json` y el CI, para que la imagen no compile con un Node distinto del que se
prueba.

```dockerfile
COPY package.json package-lock.json .npmrc ./
RUN npm ci
```

El mismo criterio de caché del backend: primero solo los manifiestos de dependencias.
`npm ci` instala **exactamente** las versiones del `package-lock.json` (y falla si no
coinciden con el `package.json`), lo que hace la construcción reproducible. El `.npmrc` viaja
con ellos porque trae `legacy-peer-deps=true`, sin el cual `npm ci` rechaza la instalación por
una diferencia de parche entre paquetes de Angular.

```dockerfile
COPY . .
RUN npx ng build --configuration=production,docker
```

Entra el código (el `.dockerignore` excluye `node_modules`, `dist`, pruebas y documentación) y
se compila. `production` aplica optimización, *hashing* de archivos y el *service worker*;
`docker` reemplaza `environment.ts` por `environment.docker.ts`, que **no** trae compilada la
URL del backend: activa la lectura de `/config.json` al arrancar (ver "Configuración en
ejecución", más abajo).

### Etapa 2: imagen de ejecución

```dockerfile
FROM nginxinc/nginx-unprivileged:1.28-alpine
```

Imagen oficial de Nginx Inc. que corre como el usuario `nginx` (uid 101) y no como root. Por
eso escucha en el puerto **8080** y no en el 80: un proceso sin privilegios no puede abrir
puertos por debajo de 1024. Se fija la rama estable 1.28 en vez de `latest` para que la
construcción no cambie sola de versión.

```dockerfile
ENV API_URL=http://localhost:8080 \
    GOOGLE_MAPS_API_KEY=""
```

Valores por defecto de la configuración de ejecución. Se reemplazan con `-e` en `docker run`
o con `environment:` en `docker-compose.yml`. Ninguno es un secreto: todo lo que llega al
navegador es inspeccionable.

```dockerfile
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
```

La imagen de Nginx procesa con `envsubst` todo lo que encuentre en `/etc/nginx/templates`
cuando el contenedor arranca y deja el resultado en `/etc/nginx/conf.d`. Ahí es donde
`API_URL` y `GOOGLE_MAPS_API_KEY` entran a la configuración.

```dockerfile
COPY --from=build /build/dist/fusaroute-frontend/browser /usr/share/nginx/html
```

Lo único que pasa de la etapa 1 a la imagen final: los archivos estáticos compilados. Node y
`node_modules` se quedan atrás.

```dockerfile
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/ || exit 1
```

`EXPOSE` documenta el puerto. `HEALTHCHECK` le permite a Docker saber si Nginx realmente
responde, no solo si el proceso existe. Se usa `127.0.0.1` y no `localhost` porque en Alpine
`localhost` puede resolver a la dirección IPv6 y Nginx aquí solo escucha en IPv4. No hay
`CMD`: se hereda el de la imagen base (`nginx -g 'daemon off;'`), que deja a Nginx como
proceso principal en primer plano.

### La plantilla de Nginx (`nginx/default.conf.template`)

| Bloque | Qué hace | Por qué |
|---|---|---|
| `location = /config.json` | responde `{"apiUrl":"${API_URL}","googleMapsApiKey":"${GOOGLE_MAPS_API_KEY}"}` con `Cache-Control: no-store` | configuración de ejecución; nunca se guarda en caché para que cambiar la variable y reiniciar baste |
| `location ~ ^/(index\.html\|ngsw\.json\|ngsw-worker\.js…)$` | `Cache-Control: no-cache` | estos archivos no llevan *hash* en el nombre; si se guardaran en caché, el navegador se quedaría con una versión vieja de la aplicación |
| `location ~* \.(?:js\|css)$` | caché de un año, `immutable` | Angular les pone *hash* en el nombre (`outputHashing: all`): si el contenido cambia, cambia el nombre |
| `location /` | `try_files $uri $uri/ /index.html` | *fallback* de SPA: una dirección como `/rutas/5` no existe como archivo; se entrega `index.html` y el enrutador de Angular resuelve la pantalla |
| cabeceras | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `server_tokens off` | endurecimiento básico; no se anuncia la versión de Nginx |

No se configuró `Content-Security-Policy`: el SDK de Google Maps carga recursos desde varios
dominios de Google y una política mal afinada rompe el mapa. Queda como mejora proyectada.

### Configuración en ejecución: por qué la URL del backend no va en el bundle

El despliegue en Vercel compila la URL del backend dentro del JavaScript
(`environment.prod.ts`): cada ambiente necesita su propia compilación, lo que incumple el
factor 3 de los 12 factores (la configuración debe vivir en el entorno, no en el código). La
imagen Docker lo resuelve así:

1. Se compila con `runtimeConfig: true` (`environment.docker.ts`).
2. `main.ts` llama a `loadRuntimeConfig()` **antes** de iniciar Angular; esta función pide
   `/config.json` y coloca `apiUrl` y `googleMapsApiKey` en el objeto `environment`, del que
   leen todos los servicios.
3. Nginx responde `/config.json` con las variables de entorno del contenedor.

Resultado: **una sola imagen para todos los ambientes**. Cambiar de backend es cambiar una
variable y reiniciar el contenedor.

Alternativas descartadas:

- **Reescribir el bundle al arrancar** (con `sed` o `envsubst` sobre los `.js`): el *service
  worker* de Angular guarda el *hash* de cada archivo compilado; modificarlos después de la
  compilación lo deja en estado inválido.
- **Proxy inverso en Nginx** (que el navegador llame a `/api` en el mismo origen): oculta el
  CORS, pero cambia la topología respecto a producción, donde el frontend (Vercel) y el
  backend (Render) están en orígenes distintos. Se prefirió conservar la misma topología
  para que el compose ejercite el CORS real.

Límite declarado: el despliegue de Vercel sigue con la URL compilada y no pide
`/config.json`, para no agregar un viaje de red al primer pintado que mide RNF-01.
