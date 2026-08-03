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
| Session cookie `SameSite` policy   | `server.servlet.session.cookie.same-site` | `lax`                      |

## Embedding RDFArchitect in another application

Datasets are scoped to the backend session, which the browser tracks with the
`RDFA_SESSION_ID` cookie. With the default `same-site: lax`, a browser will **not** send that
cookie when RDFArchitect runs inside a cross-site `<iframe>` — every API call then lands in a
fresh session and the app reports that no schemas have been imported. This affects host
applications that embed the UI in a third-party browsing context, such as the CIMNotebook VS
Code extension's RDFArchitect panel.

To support embedding, relax the policy on the instance being embedded:

```yaml
server:
    servlet:
        session:
            cookie:
                same-site: none
                secure: true
```

`same-site: none` requires `secure: true`, but that does not force you onto HTTPS for local
use — browsers treat `http://localhost` as a trustworthy origin and accept `Secure` cookies
from it. Only widen this where you need embedding: `none` removes the SameSite restriction
that limits cross-site request forgery.

## Frontend runtime config

The frontend is a static SPA with one runtime variable, rewritten at container start by `frontend/docker-entrypoint.sh`:

| Variable              | Default (Docker) | Description                                           |
| --------------------- | ---------------- | ----------------------------------------------------- |
| `PUBLIC_BACKEND_URL`  | `/api`           | Where the frontend expects to find the backend.       |
