# syntax=docker/dockerfile:1

# ---- build stage: fat jar 생성 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시 최적화: 래퍼/빌드 스크립트 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 빌드 (테스트는 CI에서 수행, 이미지 빌드는 패키징만)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- run stage: 경량 JRE + 비루트 ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 비루트 유저로 실행
RUN groupadd -r aegis && useradd -r -g aegis aegis

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER aegis

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
