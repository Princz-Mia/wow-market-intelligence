# Build context: repository root. The base image is multi-arch, so the same file builds on x86 and on arm64 hosts.
FROM container-registry.oracle.com/java/jdk-no-fee-term:25.0.5@sha256:283d01db5d749b005826ad311af50a78cc40969548abb63d4b57063a6d611960 AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# No unzip in this image, so mvnw downloads the tar.gz: verify that artifact instead of the zip.
RUN sed -i 's/^distributionSha256Sum=.*/distributionSha256Sum=80ffca22aed9e8b9713a232f3394fd81d7f20322df75efdb2b047dbd3e3a23bb/' .mvn/wrapper/maven-wrapper.properties
COPY collector collector
RUN --mount=type=cache,target=/root/.m2 sh -c '\
    sh ./mvnw -B -ntp -pl collector -DskipTests package && \
    java -Djarmode=tools -jar collector/target/collector-0.1.0.jar extract --layers --destination /out'

FROM container-registry.oracle.com/java/jdk-no-fee-term:25.0.5@sha256:283d01db5d749b005826ad311af50a78cc40969548abb63d4b57063a6d611960
RUN groupadd --system --gid 10001 collector && useradd --system --uid 10001 --gid collector --no-create-home collector
# Owned by the app user, so a fresh named volume mounted here starts out writable.
RUN mkdir -p /data/archive && chown collector:collector /data/archive
WORKDIR /app
COPY --from=build /out/dependencies/ ./
COPY --from=build /out/spring-boot-loader/ ./
COPY --from=build /out/snapshot-dependencies/ ./
COPY --from=build /out/application/ ./
USER collector
ENV COLLECTOR_ARCHIVE_ROOT=/data/archive \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "collector-0.1.0.jar"]
