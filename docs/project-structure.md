# Project Structure

This repository uses a monorepo-style layout so backend, frontend, infrastructure, automation, and documentation stay organized without splitting the project across multiple repositories.

## Top-Level Layout

- `backend/` contains the Spring Boot application and all server-side code.
- `frontend/` contains templates, static assets, and frontend build tooling.
- `docs/` contains architecture and repository guidance.
- `scripts/` contains local developer workflow scripts for build, test, and run commands.
- `.github/workflows/` contains CI automation for GitHub.
- `docker-compose.yml` provides the local MySQL dependency for development.
- `pom.xml` is the root Maven aggregator for the repository.

## Backend Boundaries

- Domain logic is grouped by feature: `auth`, `booking`, `dashboard`, `inquiry`, `notification`, `payment`, `tour`, and `waitlist`.
- Shared runtime concerns live in `config`.
- HTTP page and API entry points live in `web`.
- Migrations and seed data stay under `src/main/resources` so deployment artifacts are self-contained.

## Frontend Boundaries

- Pages and fragments are grouped under `src/templates`.
- Static JavaScript is grouped by user flow under `src/static/js`.
- Styling lives under `src/static/css`, with a single generated CSS output checked into the repository for consistent backend packaging.

## Working Principles

- Keep generated build output out of version control.
- Keep environment-specific values out of committed source and document them with example files.
- Keep developer workflows scriptable from the repository root.
- Keep CI aligned with the same commands used locally.
