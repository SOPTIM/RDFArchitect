---
title: Triple Store — Fuseki
sidebar_position: 4
---

# Triple Store: Apache Jena Fuseki

## Recommended deployment

- A single Fuseki instance, TDB2-backed.
- One Fuseki dataset per snapshot.
- Persistent volume under `/fuseki` with enough space for schemas, SHACL, version history, and snapshots (a few hundred MB per active CGMES release is typical).

## Access control between RDFArchitect and Fuseki

RDFArchitect talks to Fuseki over HTTP using the SPARQL 1.1 Protocol (SELECT/UPDATE) plus the Graph Store Protocol (GSP). If Fuseki is on the same private network as the backend, no authentication is usually configured.

If you put Fuseki behind basic auth or an HTTP-level auth proxy, RDFArchitect does not currently send credentials. Either keep Fuseki on a trusted network, or place both behind the same authenticating proxy so the request path from the browser is the only one that needs to authenticate.

## Read-only users and snapshots

RDFArchitect's read-only model is per dataset, enforced by the backend. It is **not** a Fuseki-level protection — a user with direct Fuseki access can still modify any dataset.
