# Martyrs module image: backend API and its micro-frontend artifact in one independently deployable unit.
# Build first: ./mvnw -pl modules/martyrs/backend -am package && npm run build -w modules/martyrs/frontend
FROM eclipse-temurin:21.0.8_9-jre-alpine
RUN addgroup -S haramyar && adduser -S -G haramyar -H haramyar
WORKDIR /app
COPY backend/target/martyrs.jar /app/app.jar
COPY frontend/dist/mfe/entry.js /app/static/mfe/1.0.0/entry.js
ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/ PORT=8091
USER haramyar
EXPOSE 8091
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=12 CMD wget -qO- http://127.0.0.1:8091/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
