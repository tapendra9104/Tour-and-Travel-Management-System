# Wanderlust Travels

This project is the uploaded travel UI rebuilt as a professional `Java + MySQL + HTML/CSS/JS` application with a cleaner monorepo-style repository structure.

## Stack

- Java 17
- Spring Boot 3
- Thymeleaf
- MySQL 8
- Vanilla JavaScript
- Tailwind-generated static CSS

## Project Structure

- `backend/` contains the Spring Boot backend, database configuration, Flyway migrations, domain services, APIs, and tests.
- `frontend/` contains the HTML templates, JavaScript, CSS source, compiled CSS, and frontend build tooling.
- `docs/` contains architecture and repository structure documentation.
- `scripts/` contains local developer workflow scripts for building, testing, and running the app.
- `.github/workflows/` contains CI automation for GitHub.
- `docker-compose.yml` starts the MySQL database used by the backend.

### Backend

- `backend/src/main/java` contains controllers, services, security, booking, payments, notifications, inquiries, and waitlist logic.
- `backend/src/main/resources/application.properties` contains environment-driven app configuration.
- `backend/src/main/resources/data/tours.json` contains the built-in catalog seed data.
- `backend/src/main/resources/db/migration` contains Flyway migrations.

### Frontend

- `frontend/src/templates` contains Thymeleaf pages and shared fragments.
- `frontend/src/static/js` contains the frontend behavior for tours, booking, dashboard, and contact flows.
- `frontend/src/static/css` contains Tailwind source and compiled CSS.
- `frontend/src/static` also contains icon and image assets.

The backend Maven build pulls templates and static assets directly from `frontend/` so the app still runs as a single Spring Boot application.

## Repository Guides

- `backend/README.md` explains the server-side module boundaries.
- `frontend/README.md` explains the frontend asset and template module.
- `docs/project-structure.md` documents the repository layout and organization principles.

## Run Locally

1. Start MySQL and create a database named `wanderlust`, or use the defaults below.
2. Set credentials if needed:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/wanderlust?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="wanderlust"
$env:DB_PASSWORD="wanderlust"
```

3. Build the frontend CSS:

```powershell
cd frontend
npm install
npm run build:css
cd ..
```

4. Run the backend:

```powershell
cd backend
mvn spring-boot:run
```

5. Open `http://localhost:8080`

## Demo Accounts

- Admin: `admin@wanderlust.com` / `Admin@123`
- Traveler: `traveler@wanderlust.com` / `Traveler@123`

## CSS Build

If you update templates or frontend scripts and want to regenerate the stylesheet:

```powershell
cd frontend
npm run build:css
```

## Developer Scripts

From the repository root:

```powershell
.\scripts\dev.ps1 -StartDatabase
.\scripts\test.ps1
.\scripts\build.ps1
```

## Render Deployment

This repository now includes a Render Blueprint in `render.yaml` and a Docker deploy path in `backend/Dockerfile`.

- `wanderlust-travels` deploys as a public web service.
- `wanderlust-mysql` deploys as a private MySQL service with a persistent disk.
- Render will prompt for secrets such as `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `APP_NOTIFICATIONS_BASE_URL`, and optional SMTP credentials during setup.

Open the Blueprint directly in Render:

`https://dashboard.render.com/blueprint/new?repo=https://github.com/tapendra9104/Tour-and-Travel-Management-System`

## Maven Build

From the repository root:

```powershell
mvn test
mvn -pl backend -am package
```

## Notes

- Bookings, users, inquiries, notifications, waitlists, and tour management are stored in MySQL through JPA, JDBC, and Flyway.
- Tour catalog content is served from MySQL; `tours.json` is the built-in source for default destinations and backfills any missing catalog entries.
- The dashboard is role-aware: guests use booking reference lookup, travelers see only their own bookings, and admins can manage bookings, inquiries, exports, and the tour catalog.
- SMTP email delivery is supported when `MAIL_*` environment variables are configured. Without SMTP, notifications are still stored in the app database.
- All visible generator branding and platform-specific code was removed from the converted application and cleaned from the project files.
