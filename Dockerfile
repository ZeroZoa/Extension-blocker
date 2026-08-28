# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# non-root 사용자로 실행 — 컨테이너가 뚫려도 프로세스가 root 권한을 갖지 않도록 한다.
RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /app/uploads-data && chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
