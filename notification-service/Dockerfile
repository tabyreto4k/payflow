FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle gradle
RUN ./gradlew --version --no-daemon

COPY settings.gradle.kts build.gradle.kts ./
COPY build-logic build-logic
COPY config config
# Модуль перечислен в settings.gradle.kts: без него сборка не сконфигурируется.
COPY events-contract events-contract
COPY notification-service notification-service
RUN ./gradlew :notification-service:bootJar --no-daemon -x test \
    && java -Djarmode=tools -jar notification-service/build/libs/*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

# Heap от лимита контейнера, а не от памяти хоста.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
