# CampusSafe API

Spring Boot API backed by PostgreSQL JSONB document storage.

## Deploy to Render

This repository includes a Render Blueprint at its root. In Render, select **New > Blueprint**, connect this repository, and apply `render.yaml`. Render builds the Docker image from `Dockerfile`, creates the PostgreSQL database, generates `JWT_SECRET`, and checks `/api/health`. The Blueprint allows browser requests from `https://smart-campus-frontend-iota.vercel.app`.

After deployment, copy the service URL shown by Render and set the Vercel project's `VITE_API_URL` environment variable to that URL ending in `/api`, then redeploy the frontend. The new database starts empty. Existing MongoDB records are not migrated; user accounts must register again unless data is migrated separately.

## Runtime configuration

- `DATABASE_URL`: PostgreSQL connection URL. Render supplies this from the Blueprint database.
- `JWT_SECRET`: signing key for authentication tokens. Render generates this value.
- `CLIENT_URL`: allowed frontend origin for API requests.
- `PORT`: HTTP port; Render supplies this automatically.
- `UPLOAD_DIR`: directory for uploaded files.
