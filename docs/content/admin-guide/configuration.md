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

### Letting the host read the session (live datasets)

A host that embeds RDFArchitect may want to work with *the datasets you are editing* — the
CIMNotebook IDE extensions validate SPARQL against them. Datasets belong to a session, so an
outside tool can only reach them by addressing that session, and for that it needs the session
id. The embedded app hands it over on request:

```
host → app:  { type: "rdfa:session-request" }
app  → host: { type: "rdfa:session", id: "<session id>" }
```

The reply goes to the exact origin that asked, and only when the app is embedded. It is off
until the deployment opts in:

```
PUBLIC_EMBED_SESSION_HANDSHAKE=true      # on the frontend container
```

:::warning What this exposes
The session id is the value of the session cookie, so whoever holds it can read and change
everything in that session. With the handshake enabled, **any page that embeds RDFArchitect in an
iframe can ask for it** — there is no way to tell a trusted embedder from an untrusted one, since a
webview's origin is not a stable, allow-listable value.

`same-site: none` already lets such a page *send* authenticated requests; the handshake
additionally lets it *read* the answers. Enable it only where the instance is reached by people
you trust to embed it, and prefer leaving it off on a public deployment.

The IntelliJ tool window does not need this: it loads the app as a top-level document and the
plugin scripts its own browser directly. It is the VS Code webview (an iframe) that depends on the
handshake.
:::

## Frontend runtime config

The frontend is a static SPA whose runtime variables are rewritten at container start by `frontend/docker-entrypoint.sh`:

| Variable              | Default (Docker) | Description                                           |
| --------------------- | ---------------- | ----------------------------------------------------- |
| `PUBLIC_BACKEND_URL`  | `/api`           | Where the frontend expects to find the backend.       |
| `PUBLIC_EMBED_SESSION_HANDSHAKE` | `false` | Answer an embedding host's request for the session id (see [above](#letting-the-host-read-the-session-live-datasets)). |
