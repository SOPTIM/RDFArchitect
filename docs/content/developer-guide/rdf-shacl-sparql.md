---
title: Working With RDF, SHACL, and SPARQL
sidebar_position: 9
---

# Working with RDF, SHACL, and SPARQL Inside the Codebase

## Apache Jena

The backend uses **Apache Jena 6.x** end-to-end. The relevant entry points are:

- `org.apache.jena.rdf.model.Model` — the high-level RDF API. Used in services for read-side work.
- `org.apache.jena.graph.Graph` — the low-level triple-set API. Used in `database/`, `rdf/`, and the in-memory adapter where performance matters.
- `org.apache.jena.query.QueryFactory` / `UpdateAction` — for SPARQL.
- `org.apache.jena.shacl.*` — for SHACL evaluation when needed.

The `rdf/` package contains the project's own helpers (graph wrappers with version history, RDF formatting that matches ENTSO-E conventions, model merging utilities). Use these instead of Jena's defaults where they exist — they encode CIM-specific output decisions (resource ordering, prefix handling, etc.) that downstream tooling expects.

## SHACL generation

SHACL shapes are generated procedurally from CIM model objects. The entry point is `services/shacl/SHACLGenerateService` (use case: `SHACLGenerateUseCase`), which delegates to the builders under `shacl/property/shapegenerator/` and `shacl/property/shapebuilder/`. Each property type (attribute, association, enum-typed) has its own builder.

When fixing a SHACL bug, the most useful starting point is `SHACLFromCIMGeneratorTest`. It loads a known-good CIM graph and asserts the shape of the generated SHACL — adding a failing case there is the quickest way to reproduce.

`XSDDatatypeMapper` maps a CIM primitive to an XSD datatype by composing `xsd:<primitive label>`, and falls back to a `BaseDatatype` with that URI when Jena does not recognise the result. It therefore invents datatypes for primitives whose CIM name differs from the XSD one — `MonthDay` becomes the non-existent `xsd:MonthDay` where ENTSO-E uses `xsd:gMonthDay`. The conformance check (below) surfaces this, and `ConformanceAgainstEntsoeProfilesTest` pins the current behaviour.

## Custom SHACL: many documents per graph

A graph holds a **list** of `ShapesDocument`s, not one shapes graph. Each carries its display name, its origin (imported or authored), its position, whether it is enabled, and — importantly — the **verbatim text** it was written from alongside the parsed graph.

The text is the source of truth. Official ENTSO-E files carry comments and a deliberate ordering that users expect back byte for byte, and line/column diagnostics need the original characters; round-tripping through Jena destroys both. Anything that edits a document therefore edits *text*, and the parsed graph is derived from it.

Storage lives in `database/` (`GraphContext.getShapesDocuments()`), and `services/shacl/SHACLStoringService` is the use-case layer. Reads that need "the constraints of this graph" merge every **enabled** document in order; the first document to bind a prefix keeps it, so a later document rebinding it cannot change what earlier shapes mean.

Two invariants worth not breaking:

- **Conjunction, not precedence.** Every enabled document applies. Contradictions between documents are reported as findings (`ShapesConflictAnalyzer`), never resolved by preferring one document.
- **A context commits all of its participants together** to keep their version counters in lockstep, so committing a shapes document also mints a new version id for the RDF graph. Anything keyed on `GraphContext.getRdfGraphVersion()` will be invalidated by a shapes save as well.

## Validating and understanding shapes: CIMVocabCheck

Everything that reasons *about* shapes rather than generating them is built on `de.soptim.opencgmes:cimvocabcheck-core`, and all of it hangs off one object: an `RdfsSchemaIndex` built from the workspace's graphs by `services/shacl/validation/SchemaIndexCache`.

| package | question it answers |
|---|---|
| `services/shacl/validation/` | Are these shapes valid against the schema? Shape structure and embedded SPARQL, with source positions. |
| `services/shacl/terms/` | What terms exist, and what does this one mean? Backs editor completion and hover. |
| `services/shacl/form/` | What shapes does this document declare, and how do I write one back without reformatting the file? |
| `services/shacl/conformance/` | Does this document still agree with what the schema implies? |

Three things about that cache are load-bearing:

- It is keyed on `{graphUri → committed version id}` **and the session id**, because the in-memory database is per HTTP session. A commit, an undo or a redo invalidates it without the commit path knowing the cache exists.
- Validation is scoped to **every profile in the workspace**, not to the graph a document belongs to. Official cross-profile constraints files reference neighbouring profiles on purpose; scoping to one profile reported 25 spurious unknown-class errors on a single official file where whole-workspace scoping reported none.
- Every graph contributes exactly one profile. Graphs the CIM loader does not recognise are indexed generically under their declared `owl:versionIRI`, or a synthetic one — otherwise a profile still being authored would have its own classes reported as unknown.

Note that positions come back **1-based** for shape findings and **0-based** for findings inside embedded SPARQL; `SourcePositions` normalises them.

### There is no language server

Editor completion, hover and go-to-definition are plain REST endpoints plus Monaco providers, not LSP. The reason is that the language intelligence is not in OpenCGMES's LSP module — it is in `cimvocabcheck-core`'s `SchemaIndex`, which this process already holds. An LSP would have added a WebSocket, a protocol and a schema-injection SPI to reach an index one method call away, and its go-to-definition jumps into RDFS *source files*, which is the wrong destination for an editor whose classes live in a database. `esm/external/monaco-lsp-client` inside monaco-editor is a good reference for the LSP↔Monaco conversions if that ever changes.

### Editing text without reformatting it

`services/shacl/form/ShapeBlockLocator` finds the span of one Turtle statement so a form edit can replace it and copy the rest of the document through unchanged. It **scans** rather than parses, because the source positions a surgical edit needs are exactly what a parse discards. The scan tracks comments, string literals (including the triple-quoted ones that embedded SPARQL lives in), bracket nesting, the dot inside a name like `cim:ACLineSegment.length`, and SPARQL-style `PREFIX` with no terminator. Its tests are the safety net for the whole form feature: getting a span wrong corrupts a file rather than merely reformatting it.

### Comparing schema with document

`services/shacl/conformance/EffectiveConstraints` collapses a shapes graph into one statement per `(sh:targetClass, sh:path)` before anything is compared, because generated and official shapes share no naming convention and both spread a single property's rules over separate cardinality, datatype and value-type shapes. Collapsing is conjunction — the effective lower bound is the largest `sh:minCount` anyone asks for. Datatypes are *collected* rather than merged: two different ones is not a stricter rule but a contradiction, and the comparison has to see it as one.

## Diagram layout

Layout positions are persisted as RDF using a small custom vocabulary under `dl/rdf/` (Diagram Layout). Layout DTOs live in `api/dto/rendering/` with renderer-specific subdirectories (`svelteflow/`, `mermaid/`). When changing the layout schema, update both the `dl/` model and any code that reads or writes layout data.

## Migration scripts

The migration generator stitches together templates from `src/main/resources/sparql-templates/migration/`. Each template is a parameterised SPARQL UPDATE block — `class-renamed.sparql`, `attribute-renamed.sparql`, etc. The composer is in `services/schemamigration/`. Adding a new migration capability means: (1) adding the template, (2) wiring it into the composer, and (3) extending the wizard's confirmation step DTOs and UI.
