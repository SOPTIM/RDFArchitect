---
title: Installation
sidebar_position: 2
---

# Installation

Three ways to get RDFArchitect running, in order of effort.

1. [Docker Compose (recommended for evaluation and single-user use)](#1-docker-compose)
2. [Local development setup](#2-local-development-setup)
3. [Production deployment notes](#3-production-deployment-notes)

All three options assume that a **SPARQL 1.1-compliant triple store** is reachable from the backend. RDFArchitect is developed against **Apache Jena Fuseki** and that is the recommended choice.

## 1. Docker Compose

The repository ships a `docker-compose.yaml` that builds the backend and frontend images and puts an nginx gateway in front of them.

### Prerequisites

- **Docker** and **Docker Compose** (any recent version).
- A running **Apache Jena Fuseki** on the host at `http://localhost:3030` with a writable dataset called `default`. The compose file expects this Fuseki to be reachable from inside the containers at `host.docker.internal:3030`. See [Fuseki quickstart](#fuseki-quickstart) below if you do not have one yet.

### Starting

From the repository root:

```bash
cd docker
docker compose up --build
```

Open `http://localhost:3000` in your browser. The gateway routes:

- `/` → the frontend (SvelteKit application)
- `/api` → the backend (Spring Boot REST API)

The first start takes a few minutes because the Spring Boot and SvelteKit images are built locally. Subsequent starts reuse the built images and are fast.

### Stopping

`Ctrl+C` in the terminal where compose is running, then:

```bash
docker compose down
```

### Fuseki quickstart

If you do not yet have a triple store, the simplest way to get one is:

```bash
docker run --rm -p 3030:3030 \
  -e ADMIN_PASSWORD=admin \
  stain/jena-fuseki
```

Then create a dataset called `default` via the Fuseki UI at `http://localhost:3030`. (Any TDB2-backed dataset will do.)

For a more durable setup, mount a volume under `/fuseki` in the Fuseki container so that the data survives restarts.

## 2. Local development setup

Useful if you want to modify the source, build from a branch, or run without Docker.

### Prerequisites

- **Java 25** or higher
- **Maven 3.9.9** or higher
- **Node.js 24** or higher
- **npm 11** or higher
- A running **Apache Jena Fuseki** at `http://localhost:3030` with a dataset called `default`.

### Backend

From the repository root:

```bash
cd backend
mvn spring-boot:run
```

The backend starts on port `8080`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

### Frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on port `1407` at `http://localhost:1407` and talks to the backend directly (no nginx in front).

## 3. Production deployment notes

RDFArchitect has no built-in authentication or authorisation. For a production deployment, the following pattern is typical:

- Deploy backend, frontend, nginx, and Fuseki as four services (the compose file is a useful starting template).
- Put an SSO-capable reverse proxy in front of the nginx gateway. OAuth2 Proxy, Traefik with forward-auth, or Kubernetes Ingress with an OIDC sidecar all work.
- Configure Fuseki to persist to a mounted volume and include that volume in your backup policy.
- Set the cookie flags in `application.yml` (`same-site`, `secure`, `http-only`) for a production environment — the defaults in the repository are development values.

### File upload size

Default max upload size is **50 MB**, which is sufficient for most ENTSO-E profiles. If you need larger uploads, raise both:

- `spring.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-request-size`
- `server.tomcat.max-http-form-post-size`
- `server.tomcat.max-swallow-size`

## Verifying the installation

Once the application is up:

1. Open the homepage at `http://localhost:3000` (Docker) or `http://localhost:1407` (local dev).
2. Click **Open Editor**.
3. The left navigation should show a dataset called `default`. If it does, the backend is talking to the triple store.
4. Import a small test file (TTL or RDF/XML). If it shows up in the navigation tree, the end-to-end path is working.

If any of these steps fail, see [Troubleshooting](./troubleshooting) and the [FAQ](/reference/faq).
