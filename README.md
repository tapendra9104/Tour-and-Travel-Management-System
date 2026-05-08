# Wanderlust Travels

![CI](https://github.com/tapendra9104/Tour-and-Travel-Management-System/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?logo=springboot)
![License](https://img.shields.io/badge/license-MIT-green)

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
2. Set credentials if needed. `.env.example` lists every supported environment variable:

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

Local development seeds these accounts by default:

- Admin: `admin@wanderlust.com` / `Admin@123`
- Traveler: `traveler@wanderlust.com` / `Traveler@123`

In production, demo credential display is disabled and the traveler seed is disabled. Set `SEED_ADMIN_PASSWORD` to create an initial admin on a fresh production database.

## CSS Build

If you update templates or frontend scripts and want to regenerate the stylesheet:

```powershell
cd frontend
npm run build:css
```

## Device and Browser Compatibility

The UI is hardened for current evergreen browsers across desktop, tablet, and mobile:

- Desktop: Chrome, Edge, Brave, Firefox, and Safari on Windows and macOS.
- Mobile and tablet: iPhone, iPad, Android phones, and Android tablets using Safari, Chrome, Firefox, Edge, and Brave.
- Layout support includes responsive breakpoints, touch-friendly controls, horizontal table scrolling for admin data, iOS safe-area handling, dynamic mobile viewport height fixes, and reduced-motion fallbacks.

The frontend declares this support in `frontend/package.json` through `browserslist`. Browsers that are end-of-life or missing modern JavaScript/CSS features may receive a simplified experience.

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
- Render will prompt for secrets such as `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `APP_BASE_URL`, `SEED_ADMIN_PASSWORD`, and optional SMTP credentials during setup.

Open the Blueprint directly in Render:

`https://dashboard.render.com/blueprint/new?repo=https://github.com/tapendra9104/Tour-and-Travel-Management-System`

## Maven Build

From the repository root:

```powershell
mvn test
mvn -pl backend -am package
```

## SMTP Email Setup

Without SMTP, notifications are stored in the database but not delivered by email.
Password reset emails require SMTP to be configured.

**Gmail example** (use a [Google App Password](https://myaccount.google.com/apppasswords), not your account password):

```powershell
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="you@gmail.com"
$env:MAIL_PASSWORD="xxxx-xxxx-xxxx-xxxx"
$env:MAIL_FROM="no-reply@yourdomain.com"
$env:APP_BASE_URL="https://yourdomain.com"
```

**Brevo / Sendinblue example:**

```powershell
$env:MAIL_HOST="smtp-relay.brevo.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="your-login@example.com"
$env:MAIL_PASSWORD="your-brevo-smtp-key"
```

## Swagger UI / API Docs

When running locally, the interactive API documentation is available at:

```
http://localhost:8080/swagger-ui.html
```

To disable Swagger UI in production:

```powershell
$env:SPRINGDOC_ENABLED="false"
```

## Notes

- Bookings, users, inquiries, notifications, waitlists, and tour management are stored in MySQL through JPA, JDBC, and Flyway.
- Tour catalog content is served from MySQL; `tours.json` is the built-in source for default destinations and backfills any missing catalog entries.
- The dashboard is role-aware: guests use booking reference lookup, travelers see only their own bookings, and admins can manage bookings, inquiries, exports, and the tour catalog.
- SMTP email delivery is supported when `MAIL_*` environment variables are configured. Without SMTP, notifications are still stored in the app database.
- Password reset emails use `APP_BASE_URL`; set it to the public production URL before launch.
- The payment gateway defaults to `sandbox` mode (simulated, no real charges). Set `PAYMENT_GATEWAY=stripe` and configure `STRIPE_SECRET_KEY` to process real payments — see `StripePaymentGateway.java` for the full upgrade guide.
- All visible generator branding and platform-specific code was removed from the converted application and cleaned from the project files.
