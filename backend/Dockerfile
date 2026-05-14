# syntax=docker/dockerfile:1.7

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 스크립트 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

# 의존성만 별도 레이어로 미리 해소.
# build.gradle / settings.gradle 이 변경될 때만 이 레이어가 무효화되며,
# GHA 레이어 캐시에 /root/.gradle(Gradle 배포본 + 의존성 jar)이 함께 보존됨.
# → 소스만 바뀌는 빌드에서는 이 레이어가 CACHED, bootJar 는 컴파일만 수행.
RUN ./gradlew --no-daemon dependencies

# 소스 복사 후 부트 jar 생성
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- extract stage ----
# Spring Boot layered jar 분해 (의존성/loader/snapshot/application 4-layer)
FROM eclipse-temurin:21-jdk AS extract
WORKDIR /workspace
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ---- runtime stage ----
FROM eclipse-temurin:21-jre AS runtime

# 비루트 사용자
RUN groupadd --system spring \
 && useradd --system --gid spring --home-dir /app --create-home spring

WORKDIR /app
USER spring:spring

# 변경 빈도 낮은 순으로 복사
COPY --from=extract --chown=spring:spring /workspace/dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /workspace/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/application/ ./

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD bash -c '</dev/tcp/localhost/8080' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
