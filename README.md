# Rackwise

Approval-gated infrastructure capacity planning platform (simulated infrastructure).
Independent portfolio project. Not affiliated with or endorsed by Apple or any other company.

**Status: bootstrap only.** Nothing below the "Verified so far" list is implemented yet.

## Stack
Java 21, Spring Boot 4.1, Maven wrapper, PostgreSQL 16, Flyway, React + TypeScript + Vite.

## Getting started
Prereqs: JDK 21 (a full JDK, not a JRE), Node 20+, Docker.

```bash
cp .env.example .env            # set POSTGRES_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up -d postgres   # Postgres on localhost:5440
set -a; . ./.env; set +a
cd backend && ./mvnw spring-boot:run    # http://localhost:8080/actuator/health
cd frontend && npm install && npm run dev
```

IntelliJ: open `backend/pom.xml` as a project, set Project SDK to JDK 21, add the `.env` values as run-configuration environment variables.

## Auth (implemented)
Stateless JWT (HS256, 15 min, in-memory on the client; no refresh token yet). Roles: `REQUESTER`, `REVIEWER`, `ADMIN`.
Dev accounts (profile `dev` only, password `Rackwise-Dev-1!` or `DEV_USER_PASSWORD`): `alice` requester, `bob` and `carol` reviewers, `admin`.
Run with `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.

## Verified so far
- Login, `/auth/me`, 401/403/400 JSON error contract and admin-only `/api/v1/users` checked manually with curl (automated tests pending on `test/*` branch).
- Backend compiles and starts against Postgres; `/actuator/health` reports UP.
- Frontend production build succeeds.
