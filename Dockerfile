# Imagen del backend para Render (SCRUM-128). Render no tiene runtime Java nativo,
# asi que el servicio corre en Docker. Dos etapas: Maven compila, y la imagen final
# solo lleva el JRE y el jar.

# --- Etapa 1: compilar el jar ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# El pom va solo primero para que la descarga de dependencias quede en una capa
# cacheable: si solo cambia el codigo, Render no vuelve a bajar todo Maven Central.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src src

# Sin tests, Checkstyle ni JaCoCo a proposito: la compuerta de calidad es el CI de
# GitHub Actions (mvn -B verify: tests, ArchUnit, JaCoCo, OWASP), que ya corrio antes
# del merge a main. Repetirlo aqui solo gasta minutos de build del tier gratis.
RUN mvn -B package -DskipTests -Dcheckstyle.skip -Djacoco.skip=true

# --- Etapa 2: imagen de ejecucion ---
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin fusaroute
COPY --from=build /build/target/fusaroute-backend-*.jar app.jar
USER fusaroute

# La instancia gratis de Render tiene 512 MB y 0,1 CPU. MaxRAMPercentage deja aire al
# resto del proceso; SerialGC es el recolector que menos memoria y CPU consume con un
# solo nucleo; ExitOnOutOfMemoryError hace que Render reinicie el contenedor en vez de
# dejar una JVM zombi respondiendo a medias.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Render inyecta PORT; application.properties lo lee con server.port=${PORT:8080}.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
