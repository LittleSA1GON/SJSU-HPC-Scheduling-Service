FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
# Install curl explicitly so the compose healthcheck does not depend on base-image tooling.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 hpc && useradd --uid 10001 --gid hpc --no-create-home hpc \
    && mkdir -p /app /data && chown hpc:hpc /data
COPY --from=build /build/target/*.jar /app/app.jar
ENV HPC_DB_PATH=/data/hpc.db
USER hpc
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
