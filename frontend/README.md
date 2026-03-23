# Frontend Module

This module contains the Thymeleaf templates, static JavaScript, icons, and Tailwind-powered stylesheet source used by the Spring Boot backend.

## Structure

- `src/templates` contains page templates and reusable Thymeleaf fragments.
- `src/static/js` contains client-side behavior for tours, booking, dashboard, and contact flows.
- `src/static/css/source.css` is the Tailwind source file.
- `src/static/css/app.css` is the generated stylesheet used by the application.
- `src/static` also contains icons and static assets served by the backend.

## Local Development

Install dependencies and rebuild the stylesheet when frontend files change:

```powershell
npm install
npm run build:css
```

The backend Maven build reads templates and static files from this module directly, so no separate frontend server is required.
