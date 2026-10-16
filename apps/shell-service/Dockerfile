# HaramYar application service (occasions, onboarding, notifications). Build first: ./mvnw -pl apps/shell-service -am package
FROM eclipse-temurin:21.0.8_9-jre-alpine
RUN addgroup -S haramyar && adduser -S -G haramyar -H haramyar
WORKDIR /app
COPY target/shell-service.jar /app/app.jar
ENV PORT=8092
USER haramyar
EXPOSE 8092
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=12 CMD wget -qO- http://127.0.0.1:8092/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
