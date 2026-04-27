---
title: Feature Checklist
sidebar_position: 1
---

# Feature Checklist

A quick map of what's in the box, organised by who tends to use what.

## Power-system / electrical engineers

| Feature |
| ------- |
| Browse a CIM/CGMES schema as a UML-style diagram. |
| Drill into a class's attributes, associations, and inheritance. |
| Inspect generated and custom SHACL constraints on a class. |
| Compare a working schema against a snapshot. |
| Receive a snapshot link and browse without an account. |
| View change history of a schema. |

## Information modelers / data architects

| Feature |
| ------- |
| Create classes, attributes, associations, enumerations, packages. |
| Single-inheritance modelling. |
| Multiplicity, datatypes, fixed values, stereotypes. |
| Custom SHACL upload. |
| Auto-generated SHACL exposed for review and export. |
| Per-dataset namespace and prefix management. |
| Schema import (Turtle, RDF/XML, N-Triples, N-Quads, TriG, JSON-LD). |
| Schema export with optional layout and SHACL. |
| Schema comparison with package, class, and property granularity. |
| Migration wizard producing a SPARQL Update script. |

## Project managers / product owners

| Feature |
| ------- |
| Read-only browse of any schema or snapshot. |
| Stable, shareable snapshot links. |
| Per-schema history with restore. |
| Comparison of two snapshots, schemas, or files. |

## Developers

| Feature | Where |
| ------- | ----- |
| REST API (Swagger UI) | `/swagger-ui.html` on the backend |
| Open source under Apache 2.0 | [github.com/SOPTIM/RDFArchitect](https://github.com/SOPTIM/RDFArchitect) |
| Hexagonal backend architecture (Java 25, Spring Boot 4, Jena 5) | [Backend architecture](/developer-guide/backend-architecture) |
| SvelteKit frontend (Svelte 5 runes, Vite 7, Tailwind 4) | [Frontend architecture](/developer-guide/frontend-architecture) |
| Containerised deploy (Docker, GHCR images) | `docker/docker-compose.yaml` |
| Tag-driven releases | [CI and releases](/developer-guide/ci-and-releases) |

## Administrators

| Feature | Where |
| ------- | ----- |
| Pluggable storage backends (Fuseki HTTP, file, in-memory) | `application-database.yml` |
| Read-only mode at schema, dataset, or application level | UI toggles + backend config |
| Audit-grade history | Per-schema, persisted in the triple store |
| Reverse-proxy-friendly auth integration | [Access control](/admin-guide/access-control) |

## What's not (yet) in the box

See [Limitations](./limitations).
