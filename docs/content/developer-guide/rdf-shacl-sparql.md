---
title: RDF, SHACL, and SPARQL
sidebar_position: 9
---

# Working With RDF, SHACL, and SPARQL

Where in the codebase to land RDF-, SHACL-, or SPARQL-shaped work.

## Apache Jena

The backend uses [Apache Jena 5](https://jena.apache.org/). The primary types you'll touch are `Dataset`, `Model`, `Resource`, `Property`, `Literal`, plus the SPARQL execution helpers.

Always go through the project's database/graph ports rather than hitting Jena directly. The ports handle transactions, snapshot routing, and read-only enforcement consistently.

## Reading and writing data

The repo wraps Jena's transaction lifecycle so the standard pattern is opening a read or write transaction through the port, performing the operation, committing, and ensuring `end()` runs. Write operations also go through the change-history hook — every mutation that touches user-visible model state must record a history entry.

Browse a representative service implementation for the canonical shape; copy it.

## SPARQL templates

Templates live under `backend/src/main/resources/sparql-templates/`. The repo's helper:

- Loads templates by name from the classpath.
- Supports parameter substitution.
- Caches parsed queries.

Templates are plain `.sparql` files. They are the right place for *every* non-trivial query in the application:

- They're auditable — operators can see what queries the application runs.
- They're testable — they can be exercised against an in-memory dataset directly.

Inline SPARQL strings inside Java code should be avoided.

## SHACL generator

The `shacl/` package contains the components that:

- Walk the schema and produce a flat enumeration of classes, attributes, associations, and enums.
- Emit one `sh:NodeShape` per class.
- Emit `sh:property` shapes for each attribute and association.
- Emit `sh:in` for enumerations.
- Merge generated shapes with custom shapes loaded from the schema.

Generated shapes are deterministic — given the same model, the output is byte-identical. This is why we don't persist them.

### Adding a SHACL constraint kind

If you need the generator to emit a new constraint:

1. Decide whether it's structural (per-property) or class-level.
2. Extend the corresponding builder so the new constraint flows through the same emission path.
3. Update the SHACL importer to recognise the same predicate when importing files, so generated and imported shapes stay symmetric.
4. Add a fixture-based test covering a model exhibiting the constraint.

Don't fork a new builder — extend the existing pipeline.

## Migration template composer

The migration wizard's last step lives in `backend/src/main/java/org/rdfarchitect/migration/`. It reads the comparison result and emits a SPARQL Update script. Templates per migration kind live in `resources/sparql-templates/migration/`:

```
migration/
├── class-renamed.sparql
├── class-deleted.sparql
├── attribute-added.sparql
├── attribute-renamed.sparql
├── attribute-datatype-changed.sparql
├── attribute-fixed-value-changed.sparql
├── association-added.sparql
├── association-renamed.sparql
├── domain-renamed.sparql
├── enum-entry-renamed.sparql
├── enum-entry-deleted.sparql
└── property-deleted.sparql
```

Each template handles one kind of detected difference. Adding a new pattern means adding the template, exposing the corresponding step in the wizard's UI, and wiring the composer to combine them.

## Things to be careful about

- **Blank nodes.** Jena assigns fresh blank-node IDs on import. Round-trip identity is by structure, not by identifier. The exporter sorts blank nodes deterministically so diffs stay small.
- **Datatype precision.** `xsd:integer` and `xsd:int` are not the same type to Jena. Don't normalise them silently — the schema author may have intended a specific one.
- **Order.** RDF is a set, not a sequence. Where the user perceives order, it's stored explicitly via an ordering predicate.
- **Whitespace in literals.** Comments contain newlines and indentation that should round-trip. The compare engine normalises whitespace for diffing but never normalises it on disk.
