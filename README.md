# CampusSafe API

This is the Java backend for CampusSafe. It uses Java 17, Spring Boot 3, Spring JDBC, and MySQL 8. The API is organized into `controller`, `config`, `security`, and `repository` packages under `src/main/java/edu/campussafe`.

## Run locally

The backend reads `.env` when started from this directory. Copy `.env.example` to `.env`, then start MySQL from the repository root:

```bash
docker compose up -d db
```

Then run:

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:5000`. The Compose database uses `campussafe` / `campussafe-local`; use `DATABASE_URL`, `DB_USERNAME`, and `DB_PASSWORD` to connect to a different MySQL server.

## Deploy to Render

This repository includes a Render Blueprint at its root. In Render, select **New > Blueprint**, connect this repository, and apply `render.yaml`. Render builds the Docker image from `Dockerfile`, prompts for external MySQL connection settings, generates `JWT_SECRET`, and checks `/api/health`. The Blueprint allows browser requests from `https://smart-campus-frontend-iota.vercel.app`.

After deployment, copy the service URL shown by Render and set the Vercel project's `VITE_API_URL` environment variable to that URL ending in `/api`, then redeploy the frontend. The MySQL database starts empty; records from earlier MongoDB or PostgreSQL databases are not migrated automatically.

## Runtime configuration

- `DATABASE_URL`: MySQL JDBC URL, for example `jdbc:mysql://127.0.0.1:3306/campussafe?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`.
- `DB_USERNAME`: MySQL user name.
- `DB_PASSWORD`: MySQL password.
- `JWT_SECRET`: signing key for authentication tokens. Render generates this value.
- `CLIENT_URL`: allowed frontend origin for API requests.
- `PORT`: HTTP port; Render supplies this automatically.
- `UPLOAD_DIR`: directory for uploaded files.
