# RDFArchitect

[![Backend CI](https://github.com/SOPTIM/RDFArchitect/actions/workflows/backend-ci.yml/badge.svg?branch=main)](https://github.com/SOPTIM/RDFArchitect/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/SOPTIM/RDFArchitect/actions/workflows/frontend-ci.yml/badge.svg?branch=main)](https://github.com/SOPTIM/RDFArchitect/actions/workflows/frontend-ci.yml)
[![Version](https://img.shields.io/badge/version-0.15.1-blue.svg)](https://github.com/SOPTIM/RDFArchitect/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

RDFArchitect is a web-based tool for visualizing and editing RDF graphs that model UML classes using the CIM standard.

## Overview

RDFArchitect combines a Java/Spring backend and a Svelte frontend to provide a practical modeling workflow for CIM-based RDF data. The application supports importing, editing, comparing, validating, and exporting graph data with an interactive UI.

## Key Features

- Import and export RDF graph content
- Visualize class structures via UML diagrams
- Edit classes, attributes, associations, and enum entries
- Manage datasets, graphs, packages, and namespaces
- Compare graphs and inspect change history
- Generate and inspect SHACL content

## Architecture

- Backend: Spring Boot service in `backend/` (default runtime port `8080`)
- Frontend: SvelteKit app in `frontend/` (dev server on `1407`)
- Gateway (local Docker): Nginx proxy in `docker-compose.yaml` exposed on `3000`
  - `/` routes to frontend
  - `/api` routes to backend

## Prerequisites

- Java 25 or higher
- Maven 3.9.9 or higher
- Node.js 24 or higher
- npm 11 or higher
- Docker and Docker Compose (optional, for containerized local setup)

## Quickstart

### Run Locally (Dev)

1. Start backend:

```bash
cd backend
mvn spring-boot:run
```

2. Start frontend in a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

3. Open the frontend at `http://localhost:1407`.

### Run with Docker Compose

```bash
docker compose up --build
```

Open `http://localhost:3000`.

## Configuration Highlights

Backend config (`backend/src/main/resources`):

- `frontend.url` (default: `http://localhost:1407`)
- `frontend.accessRoute` (default: `/api`)
- `database.http.endpoint` (default: `http://localhost:3030`)
- `database.defaultDataset` (default: `default`)

Frontend runtime config:

- `PUBLIC_BACKEND_URL` controls API base URL (Docker default: `/api`)
- In container deployments, this is injected via `frontend/docker-entrypoint.sh`

## API Documentation

When the backend is running, Swagger UI is available at:

- `http://localhost:8080/swagger-ui.html`

## Development Workflows

### Backend

```bash
cd backend
mvn -B test
mvn -B verify
```

### Frontend

```bash
cd frontend
npm run clean-install
npm run test
npm run lint
npm run build
```

## Contributing

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for development and pull request guidelines.

## Security

Please see [SECURITY.md](SECURITY.md) for responsible vulnerability reporting.

## Support

Please see [SUPPORT.md](SUPPORT.md) for usage help, issue routing, and support options.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history.

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE).
