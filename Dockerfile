# syntax=docker/dockerfile:1.7
# Imagem de produção da API. Build completo dentro do Docker (não precisa de Maven/JDK na máquina):
#   docker build -t srv-luthieria .
# O cache do ~/.m2 fica num cache mount do BuildKit: rebuilds baixam só o que mudou.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY srv-luthier-parent srv-luthier-parent
COPY srv-luthier-shared srv-luthier-shared
COPY srv-luthier-security srv-luthier-security
COPY srv-luthier-customer srv-luthier-customer
COPY srv-luthier-product srv-luthier-product
COPY srv-luthier-service srv-luthier-service
COPY srv-luthier-notification srv-luthier-notification
COPY srv-luthier-app srv-luthier-app
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -ntp -q package -DskipTests -pl srv-luthier-app -am \
    && cp srv-luthier-app/target/srv-luthier-app-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

COPY --from=build /app.jar app.jar

# Heap proporcional ao limite de memória do container (mem_limit no compose)
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k"

EXPOSE 8080 8081
# A porta de management (8081) nunca é publicada: só a rede interna do Docker enxerga.
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s \
  CMD wget -qO- --header "X-Forwarded-Proto: https" http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
