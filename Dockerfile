# ==========================================
# Stage 1: Build TeleStream Fat JAR
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy gradle wrapper and configurations
COPY gradlew* settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Make gradlew executable
RUN chmod +x ./gradlew

# Copy all project modules
COPY android-compat ./android-compat
COPY cloudstream3 ./cloudstream3
COPY src ./src

# Build fatJar executable
RUN ./gradlew fatJar --no-daemon

# ==========================================
# Stage 2: Production Runtime (Hugging Face Docker)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user (Hugging Face requirement: UID 1000)
RUN adduser -D -u 1000 appuser && \
    mkdir -p /app/data/plugins_cache && \
    chown -R appuser:appuser /app

# Copy compiled fatJar from builder
COPY --from=builder --chown=appuser:appuser /app/build/libs/telestream*.jar /app/telestream-all.jar

# Copy pre-cached plugins and assets
COPY --chown=appuser:appuser data ./data

USER appuser

# Hugging Face Spaces default port
EXPOSE 7860

ENV PORT=7860
ENV BOT_TOKEN=""

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Xms256m", "-jar", "/app/telestream-all.jar"]
