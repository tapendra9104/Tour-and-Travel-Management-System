# Backend Module

This module contains the Spring Boot application, business logic, persistence, security, and automated tests for the Tour and Travel Management System.

## Structure

- `src/main/java/com/toursim/management/auth` handles registration, login support, and user roles.
- `src/main/java/com/toursim/management/booking` contains booking entities, services, APIs, and booking workflows.
- `src/main/java/com/toursim/management/config` contains security and application bootstrap configuration.
- `src/main/java/com/toursim/management/dashboard` contains traveler and admin dashboard aggregation logic.
- `src/main/java/com/toursim/management/inquiry` contains contact and support inquiry flows.
- `src/main/java/com/toursim/management/notification` contains in-app and email notification services.
- `src/main/java/com/toursim/management/payment` contains payment workflows and gateway abstractions.
- `src/main/java/com/toursim/management/tour` contains tour catalog management and destination metadata.
- `src/main/java/com/toursim/management/waitlist` contains waitlist workflows for full departures.
- `src/main/java/com/toursim/management/web` contains page controllers and API controllers.

## Resources

- `src/main/resources/application.properties` contains environment-driven runtime configuration.
- `src/main/resources/data/tours.json` contains built-in seed data for the tour catalog.
- `src/main/resources/db/migration` contains Flyway database migrations.

## Local Development

1. Copy `.env.example` values into your environment.
2. Start MySQL from the repository root with `docker compose up -d mysql`, or point the app to an existing database.
3. Build frontend assets from the `frontend/` module.
4. Run the backend:

```powershell
mvn spring-boot:run
```

## Packaging

From the repository root:

```powershell
mvn -pl backend -am package
```
