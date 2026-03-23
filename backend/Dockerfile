FROM node:20-bookworm-slim AS frontend-builder
WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/src ./src
RUN npm run build:css

FROM maven:3.9.9-eclipse-temurin-17 AS backend-builder
WORKDIR /workspace

COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml

COPY backend/src backend/src
COPY frontend/src/templates frontend/src/templates
COPY frontend/src/static frontend/src/static
COPY --from=frontend-builder /workspace/frontend/src/static/css/app.css frontend/src/static/css/app.css

RUN mvn -q -pl backend -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=backend-builder /workspace/backend/target/wanderlust-travels-backend-1.0.0.jar /app/app.jar

EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
