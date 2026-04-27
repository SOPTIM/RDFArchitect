---
title: Backend Architecture
sidebar_position: 5
---

# Backend Architecture

The backend is a Spring Boot 4 application running on Java 25, organised as a hexagonal (ports-and-adapters) architecture.

## Layered overview

```
HTTP Request
   │
   ▼
┌─────────────────────────────────────────┐
│ api/controller/                         │  Thin REST controllers
│   └─ delegate to use cases              │
├─────────────────────────────────────────┤
│ services/<feature>/                     │  Use case interfaces + impls
│   ├─ business logic                     │
│   └─ orchestrates side effects          │
├─────────────────────────────────────────┤
│ database/ + graph/                      │  Ports & adapters
│   ├─ database & graph ports             │
│   ├─ Fuseki, file, in-memory adapters   │
│   └─ Jena transaction wrappers          │
└─────────────────────────────────────────┘
   │
   ▼
Apache Jena (RDF dataset, SPARQL, SHACL)
```

A request lands on a controller, the controller calls a use-case interface, the implementation orchestrates one or more port operations, and the ports talk to the actual datastore. Tests can exercise the same use-case implementations against an in-memory adapter without spinning up Fuseki.

## Controllers (`api/controller/`)

REST controllers are deliberately thin. Their responsibilities are limited to:

- Mapping HTTP verbs and paths to use-case calls.
- Decoding/encoding DTOs via MapStruct mappers.
- Surfacing exceptions as appropriate HTTP status codes via the global exception handler.
- Audit logging for write operations.

Browse the controller package to see the canonical patterns. New endpoints should mirror the closest existing controller — same package layout, same delegation style, same DTO and mapper conventions.

## Use cases (`services/<feature>/`)

For each feature there is a `*UseCase` interface and at least one implementation. The interface is the contract used by controllers and tests; the implementation contains the orchestration.

There are roughly 80 of these interfaces across the backend. They look near-identical, but the indirection lets us:

- Unit-test controllers by mocking the interface.
- Swap in alternative implementations without touching callers.
- Keep dependency direction one-way: controller → interface → implementation.

When you add a new feature, follow the same pattern even if the implementation is trivial.

## DTOs and mappers (`api/dto/`)

Every HTTP request and response shape is modelled as a DTO record (or POJO). MapStruct-generated mappers convert between DTOs and the internal domain types. Hand-written conversion is acceptable only when the structural distance is too large for MapStruct.

DTOs live next to their controllers in the package hierarchy.

## Database layer (`database/`, `graph/`)

The database package defines the **ports** — abstractions over the dataset and over a single graph — and provides three **adapters**:

| Adapter | Use |
| ------- | --- |
| Fuseki HTTP | Production. Talks to a remote Fuseki via HTTP. |
| File-based | Single-process file-backed mode. |
| In-memory | Unit and integration tests. |

The `database.databaseType` property in `application-database.yml` selects which adapter is wired in.

### Transactions

All graph mutations follow the standard Jena pattern: open a transaction through the port, perform reads/writes, commit, and ensure the transaction is ended in `finally`. The repo's helpers wrap this so a missed commit can't leak. Every Jena `Dataset` operation lives inside a transaction — *do not* call low-level Jena APIs directly from a service; route them through the port.

### Graph identifiers

Operations targeting a single graph take a graph-identifier value type that wraps the dataset name and the graph IRI. This type is the canonical representation throughout the backend; never pass two raw strings around.

## SHACL pipeline (`shacl/`)

The SHACL package contains:

- A **generator** that walks the model and emits `sh:NodeShape` / `sh:PropertyShape` triples.
- An **importer** that classifies incoming SHACL into custom shapes for storage.
- An **exporter** that combines generated and stored shapes for output.

The generator is deterministic — given the same model, it produces byte-for-byte identical output. This is the property that makes it safe to *not* persist generated shapes.

## Migration template composer (`migration/`)

The migration wizard's last step is implemented here. The composer:

1. Reads the comparison result.
2. Loads SPARQL templates from `resources/sparql-templates/migration/`.
3. Substitutes class/property IRIs into the templates.
4. Concatenates the blocks into a single SPARQL Update file.

Each template handles one kind of change (class rename, property delete, attribute datatype change, …). Adding a new pattern means adding a new template and a corresponding entry in the composer.

## Exception handling

A single `@ControllerAdvice` translates domain exceptions to HTTP status codes — not-found to 404, read-only to 409, validation to 400, everything else to 500 (logged with stack trace). Adding a new exception means adding a new handler method or extending an existing one.

## Audit logging

Every write controller logs a structured audit line with timestamp, principal (if any), graph identifier, operation, and target. The change-history feature reads from the same channel — the audit log is the source of truth for history, not a redundant copy.
