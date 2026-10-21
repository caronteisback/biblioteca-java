# syntax=docker/dockerfile:1

# ---- build: JDK 17 and the Maven wrapper, so no Maven is needed on the machine
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Dependencies first: this layer is reused while only the sources change
RUN sh mvnw -B -q dependency:go-offline
COPY src/ src/
RUN sh mvnw -B -q -DskipTests package

# ---- run: JRE only, as a user without privileges
FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER app

# The prod profile takes the database from DB_URL, APP_USER and APP_USER_PASSWORD (see application-prod.yml).
# No secret is baked into the image: pass them when the container starts.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
