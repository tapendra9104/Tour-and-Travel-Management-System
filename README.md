# Wanderlust Travels

This project is the uploaded travel UI rebuilt as a professional `Java + MySQL + HTML/CSS/JS` application.

## Stack

- Java 17
- Spring Boot 3
- Thymeleaf
- MySQL 8
- Vanilla JavaScript
- Tailwind-generated static CSS

## Project Structure

- `src/main/java` contains the Spring Boot backend, page controllers, booking API, services, and persistence layer.
- `src/main/resources/templates` contains the HTML pages and shared Thymeleaf fragments.
- `src/main/resources/static` contains the compiled CSS, JavaScript, and icon assets.
- `src/main/resources/data/tours.json` contains the built-in catalog and is used to backfill missing default tours into MySQL on startup.

## Run Locally

1. Start MySQL and create a database named `wanderlust`, or use the defaults below.
2. Set credentials if needed:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/wanderlust?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="wanderlust"
$env:DB_PASSWORD="wanderlust"
```

3. Run the app:

```powershell
mvn spring-boot:run
```

4. Open `http://localhost:8080`

## Demo Accounts

- Admin: `admin@wanderlust.com` / `Admin@123`
- Traveler: `traveler@wanderlust.com` / `Traveler@123`

## CSS Build

If you update templates or frontend scripts and want to regenerate the stylesheet:

```powershell
npm install
npm run build:css
```

## Notes

- Bookings, users, inquiries, notifications, waitlists, and tour management are stored in MySQL through JPA, JDBC, and Flyway.
- Tour catalog content is served from MySQL; `tours.json` is the built-in source for default destinations and backfills any missing catalog entries.
- The dashboard is role-aware: guests use booking reference lookup, travelers see only their own bookings, and admins can manage bookings, inquiries, exports, and the tour catalog.
- SMTP email delivery is supported when `MAIL_*` environment variables are configured. Without SMTP, notifications are still stored in the app database.
- All visible generator branding and platform-specific code was removed from the converted application and cleaned from the project files.
