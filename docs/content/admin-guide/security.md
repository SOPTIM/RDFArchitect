---
title: Security
sidebar_position: 6
---

# Security

RDFArchitect is intended for small teams on intranet or otherwise trusted networks. It is not designed to be exposed directly to the public internet.

RDFArchitect 1.0.0 has no built-in user authentication or authorisation. Security vulnerabilities may exist in the application or in the surrounding deployment stack, so production instances should either run only on trusted internal networks or sit behind a reverse proxy that provides authentication.

Typical deployment patterns include:

- **OAuth2 Proxy** in front of the nginx gateway, authenticating against your organisation's identity provider.
- **Traefik with forward-auth**, if Traefik is already part of your platform.
- **Kubernetes Ingress** with OIDC authentication, commonly via an `oauth2-proxy` sidecar or middleware.

Keep Fuseki on a private network reachable only by the backend. Snapshot links load datasets as read-only by default, but that protection is enforced by the backend, not by Fuseki; a user with direct Fuseki access can still modify snapshot datasets.

## Session ids handed to an embedding host

The session id is a capability for a whole session: whoever holds it can read and change every
dataset in it. `GET /api/session` returns it to the caller's own session, which is harmless in
itself, but the embedded-session handshake hands it to whatever page embeds RDFArchitect — and a
webview's origin cannot be allow-listed, so "whatever page" is literal.

The handshake is therefore off unless `PUBLIC_EMBED_SESSION_HANDSHAKE=true` is set on the frontend
(see [Configuration](/admin-guide/configuration#letting-the-host-read-the-session-live-datasets)).
Enable it only where you would also be comfortable with those users embedding the instance at all,
and remember it composes with `same-site: none`: that already lets a foreign page make authenticated
requests, and the handshake additionally lets it read the answers.

## Snapshot links

Snapshot URLs are capability tokens: anyone with the URL can view the snapshot. Treat them as sensitive, especially for profiles that have not been publicly released. If you need to revoke a snapshot, delete its Fuseki dataset directly; the dataset name follows the `snapshot-<base64>` convention.
