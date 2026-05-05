---
title: Overview & Setup
sidebar_position: 1
---

# Developer Guide

A guide for developers who want to **contribute** to RDFArchitect, **extend it**, or **integrate it** into other systems. If you only want to use the application, the [User Guide](/user-guide/overview) is the right place.

This guide complements [`.github/CONTRIBUTING.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/CONTRIBUTING.md), which is the authoritative source on PR rules, commit format, and review process. Read that first; this document fills in the *how* behind those rules.

## Required toolchain

| Tool        | Minimum version | Notes                                                         |
| ----------- | --------------- | ------------------------------------------------------------- |
| Java        | 25              | Temurin is what CI uses; any compatible JDK works locally.    |
| Maven       | 3.9.9           | The project does not use the Maven wrapper.                   |
| Node.js     | 24              |                                                               |
| npm         | 11              |                                                               |
| Docker      | recent          | Optional, only needed for the Compose-based local stack.      |
| Apache Jena Fuseki | 5.x      | Required at runtime — see [Installation](/admin-guide/installation). |

A working Fuseki at `http://localhost:3030` with a writable dataset called `default` is the simplest way to develop locally. The [installation guide](/admin-guide/installation#fuseki-quickstart) has a one-line Docker invocation.

## Clone, build, run

```bash
git clone https://github.com/SOPTIM/RDFArchitect.git
cd RDFArchitect

# Backend (terminal 1)
cd backend
mvn spring-boot:run

# Frontend (terminal 2)
cd frontend
npm install
npm run dev
```

Open `http://localhost:1407`. Swagger UI for the backend is at `http://localhost:8080/swagger-ui.html`.

## IDE setup

- **IntelliJ IDEA** (Community works) for the backend. Import as a Maven project. Enable the Lombok plugin and "Annotation Processing" — Lombok and MapStruct both rely on it.
- **VS Code** with the Svelte and ESLint extensions for the frontend. The repository ships with the lint config; no per-machine setup required.
- **Pre-commit hook (optional)**: a quick `mvn -B spotless:apply && cd ../frontend && npm run format` before committing avoids most CI lint failures.

## Hot reload

- **Backend**: Spring Boot DevTools is *not* on the classpath. Restart Maven for changes. For tighter loops, run individual tests with `mvn -B test -Dtest=ClassName`.
- **Frontend**: Vite hot-reloads on save. Type changes in `.ts` files require a tab refresh occasionally; component changes do not.
