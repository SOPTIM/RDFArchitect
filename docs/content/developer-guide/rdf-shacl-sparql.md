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

Which documents *exist* is the one thing not versioned. Their content is — a document's graph is a transaction participant like any other, so emptying one is undoable — but `removeShapesDocument` takes the graph out of the participant list before the commit, so a deletion has nothing to rewind from. Deleting is therefore destructive and the UI confirms it; making it undoable means versioning the document list, not patching that method.

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

It is bounded by *size* rather than by entry count — the triples its indexes were built from, which is the thing an index is proportional to and is free to count. Counting entries said nothing about memory: eight indexes is a few megabytes of one-profile workspaces and something else entirely of eight full CGMES releases. An index nothing has asked for in half an hour is dropped as well, because sessions end without telling anyone and nothing else would ever release one. The most recently used entry is never dropped for size, so a workspace larger than the whole budget is still cached rather than rebuilt on every call.

Three things about that cache are load-bearing:

- It is keyed on `{graphUri → committed version id}` **and the session id**, because the in-memory database is per HTTP session. A commit, an undo or a redo invalidates it without the commit path knowing the cache exists.
- Validation is scoped to **every profile in the workspace**, not to the graph a document belongs to. Official cross-profile constraints files reference neighbouring profiles on purpose; scoping to one profile reported 25 spurious unknown-class errors on a single official file where whole-workspace scoping reported none.
- Every graph contributes exactly one profile. Graphs the CIM loader does not recognise are indexed generically under their declared `owl:versionIRI`, or a synthetic one — otherwise a profile still being authored would have its own classes reported as unknown.

Note that positions come back **1-based** for shape findings and **0-based** for findings inside embedded SPARQL; `SourcePositions` normalises them.

### There is no language server

Editor completion, hover and go-to-definition are plain REST endpoints plus Monaco providers, not LSP. The reason is that the language intelligence is not in OpenCGMES's LSP module — it is in `cimvocabcheck-core`'s `SchemaIndex`, which this process already holds. An LSP would have added a WebSocket, a protocol and a schema-injection SPI to reach an index one method call away, and its go-to-definition jumps into RDFS *source files*, which is the wrong destination for an editor whose classes live in a database. `esm/external/monaco-lsp-client` inside monaco-editor is a good reference for the LSP↔Monaco conversions if that ever changes.

### Editing text without reformatting it

Two things decide whether the form may write a shape at all, and both live in `ShapeModelReader`. A predicate it does not model makes the shape read-only, which is the obvious half. The other half is that naming a predicate is not enough: the form holds one value per predicate and `ShapeModelWriter` writes plain literals, so a second `sh:targetClass`, or `sh:message "…"@en`, would come back with a value gone or a language tag stripped — silently, on a shape the form had called editable. Every modelled predicate therefore declares the shape of value the writer can reproduce (`ValueKind`), and one it cannot makes the shape read-only exactly as an unknown predicate does.

`ShapeModelWriter.term` shortens IRIs with `qnameFor`, never `shortForm`. `shortForm` matches on the namespace alone, so with `ex: <http://example.org/>` bound it shortens `http://example.org/a/b` to `ex:a/b` — which is not Turtle, and turns a form edit into a document that no longer parses.

`services/shacl/form/ShapeBlockLocator` finds the span of one Turtle statement so a form edit can replace it and copy the rest of the document through unchanged. It **scans** rather than parses, because the source positions a surgical edit needs are exactly what a parse discards. The scan tracks comments, string literals (including the triple-quoted ones that embedded SPARQL lives in), bracket nesting, the dot inside a name like `cim:ACLineSegment.length`, and SPARQL-style `PREFIX` with no terminator. Its tests are the safety net for the whole form feature: getting a span wrong corrupts a file rather than merely reformatting it. `linesBySubject` is the same scan run once for every subject at a time, for callers — the class-constraints dialog — that need many lines rather than one; asking `locate` per subject is quadratic in the file's length.

A snapshot stores each document as its own named graph, and a document holding no triples has none, because an empty graph is not something the store will take. Documents are therefore recovered from the **metadata** graph as well as from the shapes graphs, or an empty-but-named one would be lost with its name, position and text.

### Comparing schema with document

`services/shacl/conformance/EffectiveConstraints` collapses a shapes graph into one statement per `(sh:targetClass, sh:path)` before anything is compared, because generated and official shapes share no naming convention and both spread a single property's rules over separate cardinality, datatype and value-type shapes. Collapsing is conjunction — the effective lower bound is the largest `sh:minCount` anyone asks for. Datatypes are *collected* rather than merged: two different ones is not a stricter rule but a contradiction, and the comparison has to see it as one.

Two things about the comparison are load-bearing, and both were got wrong first:

- **The right-hand side is every enabled document, not the open one.** A graph's constraints are their conjunction, and official releases split their rules across files: the CGMES 3.0 DiagramLayout file states 11 of its property shapes itself and defers 41 to the shared IdentifiedObject file. Reading one document alone reported its neighbours' coverage as missing. The open document joins in even when it is disabled, because it is the one the question is about, and `ConformanceFinding.statedIn` names the documents behind each finding so a merged answer still points at a file.
- **Coverage is counted apart from agreement.** `compared` is the *overlap* — the keys both sides state — and `agreeing` is that minus the contradictions and differences. Counting the schema's whole surface instead scored silence as disagreement, so a 55-line cross-profile constraints file with one rule in it announced "0 of 49 property constraints agree" about a profile it had no quarrel with. `ConformanceAcrossDocumentsTest` pins both, against the real DiagramLayout files.

### What one class is constrained by

`GET /classes/{classUUID}/shacl` answers in two halves — the shapes the schema generates and the shapes the documents state — and the class dialog folds them into one row per property. Two things make that possible and both live in `SHACLStoringService`:

- **Provenance.** Shapes are read from the union of the enabled documents, and merging is what throws away the file a reader needs in order to change a rule. `ShapeOrigin` puts it back: the document, and the line the shape starts on in that document's own text (via `ShapeBlockLocator`). Shapes are matched to documents by the id the fetchers already produce — `RDFNode.toString()` — which is the IRI for a named shape and the label for a blank node. Blank node identity survives the merge because adding a model copies triples rather than rewriting them, so an inlined property shape is attributed as reliably as a named one.
- **A summary in words.** `PropertyShapesWrapper.summary` is the conjunction of the wrapper's shapes, rendered by `services/shacl/effective/EffectiveConstraints`. That class is shared with the conformance check on purpose, so `0..1, xsd:float` means the same thing in both places.

Neither dialog writes. See the deprecation note on `updateClassSHACL` for why.

## Diagram layout

Layout positions are persisted as RDF using a small custom vocabulary under `dl/rdf/` (Diagram Layout). Layout DTOs live in `api/dto/rendering/` with renderer-specific subdirectories (`svelteflow/`, `mermaid/`). When changing the layout schema, update both the `dl/` model and any code that reads or writes layout data.

## Migration scripts

The migration generator stitches together templates from `src/main/resources/sparql-templates/migration/`. Each template is a parameterised SPARQL UPDATE block — `class-renamed.sparql`, `attribute-renamed.sparql`, etc. The composer is in `services/schemamigration/`. Adding a new migration capability means: (1) adding the template, (2) wiring it into the composer, and (3) extending the wizard's confirmation step DTOs and UI.
