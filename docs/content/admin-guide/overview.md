---
title: Overview
sidebar_position: 1
---

# Administrator's Guide

Topics for the person operating RDFArchitect for a team: configuration, triple store, security, and common operational tasks.

## Architecture at a glance

RDFArchitect consists of three runtime components plus a triple store:

- **Frontend** — a SvelteKit single-page application served as static files behind an nginx process.
- **Backend** — a Spring Boot REST service. Holds no long-term state of its own; every persistent change is written through to the triple store.
- **Gateway** (optional) — an nginx reverse proxy that routes `/api/*` to the backend and everything else to the frontend, so that both can be served from a single origin.
- **Triple store** — **Apache Jena Fuseki** (recommended). Stores all snapshots.

## Data peristence

RDFArchitect stores datasets and graphs in-memory. Only snapshots are stored in Apache Jena Fuseki and are persisted on restarts.

## Where to go next

- [Installation](./installation) — how to deploy.
- [Configuration](./configuration) — settings reference.
- [Apache Jena Fuseki](./fuseki) — triple-store guidance.
- [Security](./security) — trusted-network and reverse-proxy guidance.
- [Upgrading](./upgrading) — version-to-version notes.
- [Troubleshooting](./troubleshooting) — operational issues.
