# ---- Stage 1: build the jar ----------------------------------------------------
# Full JDK plus the Maven wrapper. This stage is thrown away; only the jar is kept.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the files that define the dependencies first. Docker caches every step
# (a "layer"), so the slow dependency download is repeated only when pom.xml changes,
# not every time we edit a Java file.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: run it -----------------------------------------------------------
# Only a JRE: no compiler, no Maven, no source code. The final image is much smaller
# and has less in it for an attacker to use.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Do not run as root inside the container
RUN useradd --system --no-create-home borderline
USER borderline

# Let the JVM use up to 75% of the container's memory for its heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
