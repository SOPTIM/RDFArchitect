---
title: Configuration
sidebar_position: 3
---

# Configuration

The authoritative configuration is `backend/src/main/resources/application.yml`. Every value can be overridden at runtime with environment variables using Spring Boot's standard mapping (dots → underscores, uppercase). The compose file sets `DATABASE_DATABASETYPE`, `DATABASE_DEFAULTDATASET`, and `DATABASE_HTTP_ENDPOINT` this way.

## Commonly adjusted settings

| Purpose                            | Property                                | Default                      |
| ---------------------------------- | --------------------------------------- | ---------------------------- |
| Snapshot store endpoint            | `database.http.endpoint`                | `http://localhost:3030`      |
| Default dataset name               | `database.defaultDataset`               | `default`                    |
| Frontend URL (CORS allow-list)     | `frontend.url`                          | `http://localhost:1407`      |
| API base path                      | `frontend.accessRoute`                  | `/api`                       |
| Max uploaded schema size           | `spring.servlet.multipart.max-file-size`| `50MB`                       |
| History depth per graph            | `graph.maxVersions`                     | `256`                        |
| Diagram renderer                   | `rendering.renderer`                    | `svelteflow` (or `mermaid`)  |
| Session cookie name                | `server.servlet.session.cookie.name`    | `RDFA_SESSION_ID`            |
| Session cookie `secure` flag       | `server.servlet.session.cookie.secure`  | `false` (set to `true` in production) |
