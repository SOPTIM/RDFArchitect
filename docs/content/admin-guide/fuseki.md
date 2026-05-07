---
title: Snapshot Store — Fuseki
sidebar_position: 4
---

# Snapshot Store: Apache Jena Fuseki

## Recommended deployment

- A single Fuseki instance, TDB2-backed.
- One Fuseki dataset per snapshot.
- Persistent volume under `/fuseki` with enough space for shared snapshots.

## Access control between RDFArchitect and Fuseki

RDFArchitect talks to Fuseki over HTTP using the SPARQL 1.1 Protocol (SELECT/UPDATE) plus the Graph Store Protocol (GSP) when creating or loading snapshots. If Fuseki is on the same private network as the backend, no authentication is usually configured.

If you put Fuseki behind basic auth or an HTTP-level auth proxy, RDFArchitect does not currently send credentials. Either keep Fuseki on a trusted network, or place both behind the same authenticating proxy so the request path from the browser is the only one that needs to authenticate.

## Read-only users and snapshots

Snapshot links load datasets as read-only by default. RDFArchitect enforces that in the backend; Fuseki itself does not make snapshot datasets immutable for users with direct Fuseki access.
