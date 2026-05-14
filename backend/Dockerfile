# syntax=docker/dockerfile:1.7

# ---- build stage ----
# JDK 21 로 부트 jar 생성. BuildKit 캐시 마운트로 Gradle 홈 재사용.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 스크립트 먼저 → 소스만 바뀐 빌드에서 의존성 해석 캐시 유지
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

# ---- extract stage ----
# Spring Boot layered jar 분해 (의존성/loader/snapshot/application 4-layer)
# → 코드만 바뀌면 application 레이어만 재배포되므로 push/pull 비용 절감.
FROM eclipse-temurin:21-jdk AS extract
WORKDIR /workspace
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ---- runtime stage ----
FROM eclipse-temurin:21-jre AS runtime

# 비루트 사용자 (Docker 공식 베스트 프랙티스: 권한 없이 동작 가능하면 USER 사용)
RUN groupadd --system spring \
 && useradd --system --gid spring --home-dir /app --create-home spring

WORKDIR /app
USER spring:spring

# 변경 빈도 낮은 순으로 복사 → 레이어 캐시 효율
COPY --from=extract --chown=spring:spring /workspace/dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /workspace/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/application/ ./

EXPOSE 8080

# 컨테이너 메모리 제한에 맞춰 힙 자동 사이징.
# OOM 발생 시 즉시 종료 → orchestrator 가 재기동하도록 위임.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

# 외부 바이너리 없이 TCP 포트만 확인 (liveness 수준).
# 본격적인 readiness 는 /actuator/health 로 별도 게이트 가능.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD bash -c '</dev/tcp/localhost/8080' || exit 1

# exec form → java 가 PID 1 로 시작. SIGTERM 직접 수신해 graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
