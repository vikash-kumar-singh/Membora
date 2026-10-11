FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY server server
RUN chmod +x gradlew \
    && ./gradlew -p server bootJar --no-daemon \
    && cp server/build/libs/membora-server-*.jar /workspace/membora-server.jar

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system membora \
    && useradd --system --gid membora --home-dir /app --shell /usr/sbin/nologin membora

WORKDIR /app
COPY --from=build --chown=membora:membora /workspace/membora-server.jar app.jar

USER membora
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
