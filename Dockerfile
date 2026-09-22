# ==========================================
# Stage 1: Build TeleStream Fat JAR
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy gradle files
COPY gradlew* settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build fatJar
COPY src ./src
RUN ./gradlew fatJar --no-daemon

# ==========================================
# Stage 2: Ultra-Lightweight Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (Hugging Face Spaces requirement & best practice)
RUN adduser -D -u 1000 appuser && \
    mkdir -p /app/data && \
    chown -R appuser:appuser /app

# Copy compiled fatJar from builder
COPY --from=builder --chown=appuser:appuser /app/build/libs/telestream*.jar /app/telestream-all.jar

USER appuser

# Hugging Face default port is 7860
EXPOSE 7860

ENV PORT=7860
ENV BOT_TOKEN=""

ENTRYPOINT ["java", "-Xmx512m", "-jar", "/app/telestream-all.jar"]
