# ===========================================
# Multi-stage Dockerfile for config-service
# ===========================================

# Stage 1: Build
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

# Copy root gradle files
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Copy service module
COPY config-service ./config-service

# Build only this service
RUN gradle :config-service:build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /app/config-service/build/libs/*.jar app.jar

# Change ownership to spring user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8888

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# JVM optimization for containers
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
