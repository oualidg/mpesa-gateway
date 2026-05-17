# ==============================================================================
# Runtime image for M-Pesa Gateway
#
# Strategy: JAR is pre-built by Maven on the runner (mvn clean package).
# This Dockerfile only packages the already-built JAR into a lightweight
# JRE image. This keeps Docker builds fast because Maven dependencies are
# cached on the runner between builds — not re-downloaded inside Docker.
# ==============================================================================

# Use Eclipse Temurin JRE 21 on Alpine — smallest viable JRE base image.
# Alpine-based images are ~50MB vs ~200MB for Debian-based equivalents.
# Temurin is the community successor to AdoptOpenJDK, production-grade.
FROM eclipse-temurin:21-jre-alpine

# Create a dedicated non-root user to run the application.
# Running as root inside a container is a security risk — if the process
# is compromised, the attacker has root inside the container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set the working directory inside the container.
WORKDIR /app

# Copy the pre-built JAR from the Maven target directory.
# The * wildcard handles the SNAPSHOT version suffix so this Dockerfile
# does not need updating when the version number changes.
COPY target/mpesa-gateway-*.jar app.jar

# Transfer ownership of the working directory to the non-root user.
RUN mkdir -p logs && chown -R appuser:appgroup /app

# Switch to non-root user for all subsequent instructions.
USER appuser

# Expose the port the M-Pesa Gateway listens on.
# This is documentation only — actual port binding is in docker-compose.yml.
EXPOSE 8081

# JVM tuning flags for a container environment:
# -XX:+UseContainerSupport        — lets JVM respect Docker CPU/memory limits
# -XX:MaxRAMPercentage=75.0       — use up to 75% of container memory for heap
# -XX:+ExitOnOutOfMemoryError     — fail fast on OOM instead of thrashing
# -Djava.security.egd=...urandom  — faster startup, avoids blocking on entropy
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
