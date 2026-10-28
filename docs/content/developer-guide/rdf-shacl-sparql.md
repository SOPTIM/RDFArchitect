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

Layout positions are persisted as RDF following the Diagram Layout profile of CGMES (IEC 61970-453), with the vocabulary in `dl/rdf/`. The DTOs the renderers read live in `api/dto/rendering/` with renderer-specific subdirectories (`svelteflow/`, `mermaid/`), the DTOs for saving a layout in `api/dto/dl/`. When changing the layout schema, update both the `dl/` model and any code that reads or writes layout data.

- **Classes.** A class has one `DiagramObject` per diagram with a single `DiagramObjectPoint`, which references a `DiagramObjectGluePoint` of the class.
- **Edges.** Every inheritance and association edge between two classes of a diagram has a `DiagramObject` of its own. It references one end of the edge as its identified object and the other through `rdfa:DiagramObject.otherClass`, which is not part of the profile (`dl.data.dto.relations.EdgeKey`). Its edge points carry a `sequenceNumber` and are ordered numerically, counted from the side of the identified object. The first and the last point may be docking points, which pin an end of the edge to the class border: they reference the glue point of their class, and a moved class moves them along. The points in between are bend points.
- **Lazy creation.** Layout data of a class is created when the class is moved or laid out in the diagram for the first time. The edge diagram objects follow: `EdgeLayoutSync` brings them in line with the schema whenever classes get layout data. After a schema change, `SyncDiagramLayoutService` deletes the layout data of classes a diagram no longer shows and syncs the edges as well.
- **Saving.** The frontend saves the full, ordered list of edge points of an edge (`PUT …/layout/{diagram}/edges`), or classes, edges and labels changed by one action together (`PUT …/layout/{diagram}`). New points are answered with the mRIDs they are stored under.

Most `DLObjectFetcher` queries match IRIs with `FILTER(STR(?x) = "...")` and scan the whole layout model, which holds every diagram of the graph. A lookup that runs per edge or per class should follow the model index instead (`listResourcesWithProperty`), like `fetchDOPsForDO` does, or saving a layout gets slow.

## Migration scripts

The migration generator stitches together templates from `src/main/resources/sparql-templates/migration/`. Each template is a parameterised SPARQL UPDATE block — `class-renamed.sparql`, `attribute-renamed.sparql`, etc. — filled in by `SparqlUpdateGenerator`; `SparqlMigrationBuilder` decides which block a given semantic change needs and in which order the blocks are emitted. Both live in `services/schemamigration/artifacts/`. Adding a new migration capability means: (1) adding the template, (2) wiring it into the composer, and (3) extending the wizard's confirmation step DTOs and UI.

The comment a user enters for a change in the wizard's review step is emitted as `#` lines directly above the block it belongs to, so the script carries its own rationale. The wizard's other artefact, the Markdown migration report, is not SPARQL at all — see [Backend Architecture](./backend-architecture#generated-artefacts) for where it is built.
