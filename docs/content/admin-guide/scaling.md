---
title: Scaling
sidebar_position: 8
---

# Scaling

What scales, what doesn't, and what to do when you outgrow the defaults.

## What scales horizontally

- The **frontend** — pure static assets behind nginx. Replicate trivially.
- The **reverse proxy** — depends on which one you chose; all common ones (Traefik, nginx, HAProxy, Caddy) scale horizontally.

## What does *not* scale horizontally in 1.0

- The **backend.** It holds in-process state (session affinity, undo stacks, in-flight transactions) that has not been hardened for multi-instance use. Run a single backend instance per Fuseki dataset. Use vertical scaling — bigger CPU and heap — when needed.
- **Fuseki.** TDB2 is a single-writer store. There is no built-in clustering. For larger workloads consider [RDF Patch + log shipping](https://jena.apache.org/documentation/rdf-patch/) for asynchronous replication, but most installations don't need this.

## What needs vertical sizing

| Component | Suggested defaults | When to bump |
| --------- | ------------------ | ------------ |
| Backend JVM heap | 1 GiB | Bump when SHACL generation on large schemas runs slow. |
| Backend CPU | 2 vCPU | Generally adequate; SHACL generation is mildly CPU-bound. |
| Fuseki heap | 2 GiB | Bump for graphs above 1M triples. |
| Fuseki disk | Match graph data + backups + ~20% overhead. | Plan for changelog growth. |

## Diagram size

The browser is the limiting factor on diagram performance. Above ~200 visible classes per package, expect noticeable slowness.

Mitigations:

- Use the diagram filter view to hide attributes / external associations.
- Split large packages into sub-packages.
- Avoid showing every association; rely on the class editor to navigate.

## Concurrent users

A single backend instance handles small/medium teams (up to ~50 concurrent active editors) comfortably. Each session is lightweight. The bottleneck — when one is hit — is usually Fuseki under heavy concurrent writes, not the backend itself.

## Storage growth

Storage growth is dominated by the changelog. A graph with frequent edits accumulates many changelog entries. For long-lived deployments:

- Monitor Fuseki disk use.
- Periodically prune old changelog entries (Fuseki admin tools, with a backup taken first).
- Compact TDB2 occasionally.

## Where the limits really are

Most installations will never need to scale beyond the single-backend, single-Fuseki layout. If you do, the right path is:

1. Vertical scaling first.
2. Splitting the workload across datasets (and possibly across multiple RDFArchitect instances) before attempting clustering.

If you outgrow vertical scaling, open a GitHub issue describing the workload — concrete pressure is the most useful input.
