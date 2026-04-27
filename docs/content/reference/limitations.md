---
title: Limitations
sidebar_position: 4
---

# Known Limitations (1.0)

The list of things RDFArchitect 1.0 deliberately or accidentally does not do, recorded here so you don't have to discover them through trial.

## Modelling

- **Single inheritance only.** Multiple inheritance is not supported. This matches CIM and is not expected to change.
- **One package per class.** A class belongs to exactly one package. This matches CIM convention.
- **No bulk-edit across classes.** Editing happens one class at a time. Rename ripples are automatic, but mass attribute changes require external tooling.
- **No in-app SHACL authoring.** Custom shapes are imported from external Turtle files; editing them inside the UI is not currently supported.
- **No OCL evaluation.** OCL constraints in imported files round-trip as opaque triples but are not validated inside the application.

## Collaboration

- **No conflict detection on concurrent edits.** Two users saving the same class concurrently produce a last-write-wins outcome. Coordinate via process or split work into separate graphs.
- **No commenting / annotation surface on snapshots.** Reviewers comment via your usual channels (mail, ticket, chat).
- **No notifications or activity feed.** The changelog is per-schema, not per-user.

## Migration wizard

- **Output is SPARQL Update only.** The wizard does not natively transform CSV / Excel / JSON instance data. Convert to RDF first or post-process the SPARQL into your target language.
- **Heuristic rename detection.** Confidence scores are guidance. Review every suggestion.
- **Cardinality reductions are flagged, not auto-resolved.** When a `0..*` becomes `0..1`, you decide which value survives.
- **Cross-schema instance rewrites need manual stitching.** When instance data spans many named graphs in your triple store, the produced script must be adapted to enumerate them.

## Identity and access

- **No built-in user management.** Authentication is delegated entirely to a reverse proxy. See [Access control](/admin-guide/access-control).
- **No role-based access control.** Fine-grained per-schema or per-dataset ACLs are not supported. Approximate via separate datasets and proxy-level rules.
- **No API tokens.** All API access is session-cookie-based.

## Operations

- **Backend is not horizontally scalable in 1.0.** Run a single backend instance per Fuseki. Vertical scaling instead.
- **Fuseki has no built-in clustering.** TDB2 is single-writer. Replicate with RDF Patch / log shipping if you need high availability.
- **Snapshot deletion is one-at-a-time.** Bulk operations require direct SPARQL against Fuseki.
- **No native instance-data validation.** Export the SHACL and feed it to an external validator.

## UI

- **English only.** No i18n in 1.0.
- **Diagram performance degrades above ~200 classes per package.** Split into sub-packages or use the filter view.
- **Mobile / small-screen layouts are functional but not optimised.** RDFArchitect is a desktop-first application.

## Data formats

- **Default 50 MB upload limit.** Configurable, but very large files are better imported directly into Fuseki.
- **Blank node identifiers do not survive round-trips.** Identity is structural.
- **Comments inside source files are lost.** RDF semantics don't carry source-level comments. Keep originals in version control if you need them.

## If a limitation blocks you

Open an issue or discussion at [github.com/SOPTIM/RDFArchitect/issues](https://github.com/SOPTIM/RDFArchitect/issues) describing the use case. The maintainers triage feature requests there; concrete user pain is the most useful input.
