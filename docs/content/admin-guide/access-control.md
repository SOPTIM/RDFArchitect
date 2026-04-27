---
title: Access Control
sidebar_position: 5
---

# Access Control

RDFArchitect 1.0 has **no built-in user management**. Authentication and authorization are delegated entirely to your reverse proxy and identity provider. This is a deliberate choice — your IDP is the right place for password policy, MFA, lockouts, and audit logging.

## What the application provides

- A session cookie (`RDFA_SESSION_ID`) that ties HTTP requests together.
- Read-only mode at the dataset / graph / application level (see [Read-only mode](/user-guide/readonly-mode)).
- Reading of an authenticated principal from request headers, used for changelog author attribution.

## What the application does *not* provide

- A user/password database.
- Roles, groups, or per-graph ACLs.
- API tokens or OAuth client credentials grants.

If you need any of those, configure them at the reverse proxy.

## Reference patterns

### Pattern A — OIDC at the proxy (recommended)

Use a reverse proxy that supports OIDC (Traefik with `forwardAuth`, oauth2-proxy, nginx + lua-resty-openidc, Authelia, …) to:

1. Redirect unauthenticated requests to your IDP.
2. After login, pass an `Authorization` header (or set a cookie) on the proxied request.
3. Inject a header like `X-Authenticated-User: jane.doe@example.com` on every backend request.

RDFArchitect reads `X-Authenticated-User` (or any header you configure via the `frontend.accessRoute` chain) and tags changelog entries with the user's identity.

### Pattern B — Basic auth at the proxy

For demo / internal-only deployments, HTTP Basic auth at the proxy is acceptable. The proxy still injects the username on the upstream request.

### Pattern C — Mutual TLS (mTLS)

In tightly-controlled environments, validate a client certificate at the proxy and inject the certificate's CN / SAN into a header.

## Authorization

Granular authorization (per-dataset, per-graph) is **not** supported in 1.0. Three workable approximations:

- **Run two RDFArchitect instances** — one read-write for editors, one read-only for reviewers. They can share the same Fuseki, with the read-only instance configured globally read-only.
- **Use multiple datasets and per-path access rules at the proxy** — restrict `/api/datasets/restricted/**` to a specific group.
- **Use the read-only flag on graphs / datasets** — combined with operational discipline, this gives "approval gating" without code changes.

If you need native role-based access control, open a GitHub issue describing the use case.

## Header propagation

The default Compose nginx config propagates the standard headers. If you change the proxy stack, ensure the following make it through:

- `Host` (for absolute-URL generation).
- `X-Forwarded-For` (for audit trails).
- `X-Forwarded-Proto` (so cookies are flagged `Secure` correctly).
- Any custom auth header you configured.

## Anonymous access

If you do not configure authentication, RDFArchitect runs anonymously: every visitor can read and write everything. Changelog entries are recorded without an author. This is fine for local development and inappropriate for shared deployments.

## Logging out

Logout is the proxy's responsibility. RDFArchitect does not invalidate sessions itself. Configure your proxy to clear its session cookie and the IDP cookie on logout.

## Security headers

Have the proxy add at minimum:

- `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Content-Security-Policy: default-src 'self'; …` — tune to allow the frontend's needs (Tailwind doesn't need anything fancy; SvelteFlow uses inline SVG).

The static-site build does not bundle a CSP; you set it at the proxy.
