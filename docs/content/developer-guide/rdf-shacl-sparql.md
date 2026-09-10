---
title: Working With RDF, SHACL, and SPARQL
sidebar_position: 9
---

# Working with RDF, SHACL, and SPARQL Inside the Codebase

## Apache Jena

The backend uses **Apache Jena 5.x** end-to-end. The relevant entry points are:

- `org.apache.jena.rdf.model.Model` — the high-level RDF API. Used in services for read-side work.
- `org.apache.jena.graph.Graph` — the low-level triple-set API. Used in `database/`, `rdf/`, and the in-memory adapter where performance matters.
- `org.apache.jena.query.QueryFactory` / `UpdateAction` — for SPARQL.
- `org.apache.jena.shacl.*` — for SHACL evaluation when needed.

The `rdf/` package contains the project's own helpers (graph wrappers with version history, RDF formatting that matches ENTSO-E conventions, model merging utilities). Use these instead of Jena's defaults where they exist — they encode CIM-specific output decisions (resource ordering, prefix handling, etc.) that downstream tooling expects.

## SHACL generation

SHACL shapes are generated procedurally from CIM model objects. The entry point is `services/shacl/SHACLGenerateService` (use case: `SHACLGenerateUseCase`), which delegates to the builders under `shacl/property/shapegenerator/` and `shacl/property/shapebuilder/`. Each property type (attribute, association, enum-typed) has its own builder.

When fixing a SHACL bug, the most useful starting point is `SHACLFromCIMGeneratorTest`. It loads a known-good CIM graph and asserts the shape of the generated SHACL — adding a failing case there is the quickest way to reproduce.

## Diagram layout

Layout positions are persisted as RDF using a small custom vocabulary under `dl/rdf/` (Diagram Layout). Layout DTOs live in `api/dto/rendering/` with renderer-specific subdirectories (`svelteflow/`, `mermaid/`). When changing the layout schema, update both the `dl/` model and any code that reads or writes layout data.

## Migration scripts

The migration generator stitches together templates from `src/main/resources/sparql-templates/migration/`. Each template is a parameterised SPARQL UPDATE block — `class-renamed.sparql`, `attribute-renamed.sparql`, etc. — filled in by `SparqlUpdateGenerator`; `SparqlMigrationBuilder` decides which block a given semantic change needs and in which order the blocks are emitted. Both live in `services/schemamigration/artifacts/`, behind the `MigrationScriptBuilder` interface. Adding a new migration capability means: (1) adding the template, (2) wiring it into the composer, and (3) extending the wizard's confirmation step DTOs and UI.

The comment a user enters for a change in the wizard's review step is emitted as `#` lines directly above the block it belongs to, so the script carries its own rationale.

## Migration reports

The migration report is the human-readable counterpart of the script, generated from the same list of `SemanticClassChange` objects. `MigrationReportBuilder` is the interface, `MarkdownMigrationReportBuilder` the implementation, both in `services/schemamigration/artifacts/`; `SchemaMigrationService` prepends the validation reports of both schemas (via `SchemaValidationReportToMarkdownService`) before handing the changes over.

Two variants are produced from the same input: the summary lists directly changed classes and names their affected concrete subclasses, the detailed one walks every affected concrete class and repeats inherited changes under each. Field changes are rendered as sentences by a `switch` over `SemanticFieldChangeType`, so a new change type needs a case there — otherwise it silently renders as an empty line. IRIs are shortened with the prefixes from `SchemaConfig`.

A new migration capability should therefore reach the report as well as the script: a change that migrates data but never appears in the protocol is invisible to whoever reviews the migration.
