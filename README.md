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

## Verified so far
- Backend compiles and starts against Postgres; `/actuator/health` reports UP.
- Frontend production build succeeds.
