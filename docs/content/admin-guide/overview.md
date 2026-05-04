---
title: Overview
sidebar_position: 1
---

# Administrator's Guide

Topics for the person operating RDFArchitect for a team: configuration, snapshot storage, security, and common operational tasks.

## Architecture at a glance

RDFArchitect consists of three runtime components plus snapshot storage:

- **Frontend** — a SvelteKit single-page application served as static files behind an nginx process.
- **Backend** — a Spring Boot REST service. Holds uploaded datasets and edits in memory.
- **Gateway** (optional) — an nginx reverse proxy that routes `/api/*` to the backend and everything else to the frontend, so that both can be served from a single origin.
- **Snapshot store** — **Apache Jena Fuseki**. Stores all snapshots.

## Data persistence

RDFArchitect stores datasets and graphs in-memory. Only snapshots are stored in Apache Jena Fuseki and are persisted on restarts.

## Where to go next

- [Installation](./installation) — how to deploy.
- [Configuration](./configuration) — settings reference.
- [Apache Jena Fuseki](./fuseki) — snapshot-store guidance.
- [Security](./security) — trusted-network and reverse-proxy guidance.
- [Upgrading](./upgrading) — version-to-version notes.
- [Troubleshooting](./troubleshooting) — operational issues.
