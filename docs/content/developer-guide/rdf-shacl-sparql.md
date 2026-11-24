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

`XSDDatatypeMapper` maps a CIM primitive to an XSD datatype. Most primitives are named after their datatype, so it composes `xsd:<primitive label>`; those that are not, such as `MonthDay` (`xsd:gMonthDay`) or `URI` (`xsd:anyURI`), are listed in `CIM_PRIMITIVE_ALIASES`. A label that matches neither falls back to a `BaseDatatype` with the composed URI. `XSDDatatypeMapperAgainstEntsoeProfilesTest` checks that every primitive an official schema declares maps to a datatype that exists.

## Custom SHACL: constraints documents

A workspace holds any number of `ShapesDocument`s. Each carries its name, its origin (imported or authored), the file it was imported from, its position, whether it is enabled, the names of the schemas it applies to, and — importantly — the **verbatim text** it was written from, next to the graph parsed from it.

The text is the source of truth. Official ENTSO-E files carry comments and a deliberate ordering that users expect back byte for byte, and line/column diagnostics need the original characters; round-tripping through Jena destroys both. Anything that edits a document therefore edits *text*, and the parsed graph is derived from it. `ShapesDocumentReader` is the one strict parse every write goes through, against `ShapesDocument.BASE_IRI`, so a relative IRI means the same thing wherever the text is read.

The documents are read through `WorkspaceTransaction.shapesDocuments()`, held by `ShapesDocumentCollection` in the in-memory database, and `services/shacl/SHACLStoringService` is the use-case layer behind `/api/workspaces/{workspaceName}/shacl/documents`. Every read that needs the constraints of a class or a schema goes through `ShapesUnion`, which adds the enabled documents to one model in order:

- The first document to bind a prefix keeps it, so a later document rebinding it cannot change what earlier shapes mean.
- A blank node gets a label scoped by its document (`ShapesUnion.scoped`). A label identifies a blank node only within the document it was normalised in, and merging on it would fold together two lists of different documents that merely look alike.
- The union is a copy, so writing to it changes no document.

Which documents *exist* is versioned like their content. `ShapesDocumentCollection` is a transaction participant, and the changelog's retained versions keep a deleted document reachable, so undoing a delete brings it back with its shapes, text and metadata. Deleting also clears the document's graph in the same commit, which is what lets the changelog entry say what went.

Two invariants worth not breaking:

- **Conjunction, not precedence.** Every enabled document applies. Contradictions between documents are reported as findings (`ShapesConflictAnalyzer`), never resolved by preferring one document.
- **A commit only versions what it changed.** Saving a shapes document leaves `GraphContext.getRdfGraphVersion()` alone, so work derived from the schema stays valid across a shapes save. `WorkspaceTransaction.shapesDocumentsVersion()` is the id to key on for work derived from the documents; it is derived from their content, so an undo returns the id the content had before.

### Where documents live

A document belongs to the workspace and names the schemas it applies to by schema name: official files are often about more than one profile, or about none of their own. Nothing writes to a document when a schema goes away: a name with no schema behind it shows as *Not assigned*, and a schema of that name imported again takes the document back, so replacing a schema needs no special case. Renaming a schema rewrites the name in every document. The changelog files a document's changes under its schemas, as a workspace-scoped participant, which is also why a restore held to one schema leaves documents alone.

A persisted workspace and a share snapshot store each document as its own named graph (`urn:rdfa:shacl:<id>`), plus one **metadata** graph (`urn:rdfa:shacl-meta`) holding every document's name, position, schemas and text. A document holding no triples has no graph of its own, because the store keeps no empty graph, so documents are recovered from the metadata graph as well as from the shapes graphs. The load path turns every named graph it finds into a schema (`SchemaIri.toName`), so it leaves out the graphs `ShapesGraphNaming.isReserved` names first; a new kind of stored graph has to be added there.

The `.rdfa` workspace file holds the documents in `shacl/`, and each schema's `shacl.ttl` for readers of format 1.0; see [The workspace file](./backend-architecture#the-workspace-file).

## Validating and understanding shapes: CIMVocabCheck

Everything that reasons *about* shapes rather than generating them is built on `de.soptim.opencgmes:cimvocabcheck-core`, and all of it hangs off one object: an `RdfsSchemaIndex` built from the workspace's schemas by `services/shacl/validation/SchemaIndexCache`.

| package | question it answers |
|---|---|
| `services/shacl/validation/` | Are these shapes valid against the schema? Shape structure and embedded SPARQL, with source positions. |
| `services/shacl/terms/` | What terms exist, and what does this one mean? Backs editor completion, hover and go-to-definition. |
| `services/shacl/conformance/` | Do a schema and the documents applying to it still agree? |

The cache is bounded by *size* rather than by entry count — the triples its indexes were built from, which is the thing an index is proportional to and is free to count. An entry count says nothing about memory: eight indexes is a few megabytes of one-profile workspaces and something else entirely of eight full CGMES releases. An index nothing has asked for in half an hour is dropped as well, because sessions end without telling anyone and nothing else would ever release one. The most recently used entry is never dropped for size, so a workspace larger than the whole budget is still cached rather than rebuilt on every call.

Four things about validation are load-bearing:

- The cache is keyed on `{schema name → committed version id}` **and the session id**, because the in-memory database is per HTTP session. A commit, an undo or a redo invalidates it without the commit path knowing the cache exists.
- Validation is scoped to **every schema in the workspace**, not to the schemas a document applies to. Official cross-profile constraints files reference neighbouring profiles on purpose, and scoping to one profile would report each of those references as an unknown class.
- Every schema contributes exactly one profile. Schemas the CIM loader does not recognise are indexed generically under their declared `owl:versionIRI`, or a synthetic one — otherwise a profile still being authored would have its own classes reported as unknown.
- A term in a namespace no schema of the workspace uses is information, not an error, because official NC files name each type under every CIM version's namespace. `KnownNamespaces` keeps the error where the namespace is a misspelling: within two edits of a schema's namespace, a schema's namespace missing its closing `#` or `/`, or the namespace most of the document's `sh:path` properties are written in.

Note that positions come back **1-based** for shape findings and **0-based** for findings inside embedded SPARQL; `SourcePositions` normalises them.

### There is no language server

Editor completion, hover and go-to-definition are plain REST endpoints (`/api/workspaces/{workspaceName}/shacl/terms` and `…/terms/detail`) plus Monaco providers, not LSP. The reason is that the language intelligence is not in OpenCGMES's LSP module — it is in `cimvocabcheck-core`'s `SchemaIndex`, which this process already holds. An LSP would have added a WebSocket, a protocol and a schema-injection SPI to reach an index one method call away, and its go-to-definition jumps into RDFS *source files*, which is the wrong destination for an editor whose classes live in a database. `esm/external/monaco-lsp-client` inside monaco-editor is a good reference for the LSP↔Monaco conversions if that ever changes.

A class several schemas declare opens in the first of the schemas the request names (`?schema=`, the open document's schemas), or in the first by name when it names none. The SHACL, RDF, RDFS, OWL and XSD vocabularies are in no CGMES profile, so they come from a static table in the frontend (`src/lib/shacl/standardVocabulary.js`), not from the index.

### Comparing schema with document

`services/shacl/effective/EffectiveConstraints` collapses shapes into one statement per `(class, sh:path)` before anything is compared, because generated and official shapes share no naming convention and both spread a single property's rules over separate cardinality, datatype and value-type shapes. Collapsing is conjunction — the effective lower bound is the largest `sh:minCount` anyone asks for. Datatypes are *collected* rather than merged: two different ones is not a stricter rule but a contradiction, and the comparison has to see it as one. The reader follows a targeted node shape into the shapes it names with `sh:node` and into every member of its `sh:and`, takes a shape that is also an `rdfs:Class` as targeting itself, and, given the schema's `ClassHierarchy`, files a rule under every class deriving from its target, as SHACL applies it.

What `services/shacl/conformance/ConformanceService` compares:

- **The documents' side is every enabled document applying to the schema, read together.** A schema's constraints are their conjunction, and official releases split their rules across files, so one file read alone would report its neighbours' rules as missing. `ConformanceFinding.statedIn` names the documents behind each finding, so a merged answer still points at a file.
- **The schema's side is the workspace's schemas, scoped to this schema.** An NC profile adds properties to classes the Equipment profile declares, so the documents are compared with everything the workspace says about the classes they target, while the schema is only asked to cover what it declares itself. Shapes are generated only for classes in the schema's own vocabulary.
- **Rules in another vocabulary are left out, and so is what the reader cannot follow.** Official NC files state every rule under each CIM version's namespace, and a schema is written in one of them. `OtherVocabularies` takes the others out, and `NotCompared` lists them in the report's `notCompared`, together with shapes chosen by something other than a class (`sh:targetNode`, `sh:targetSubjectsOf`, `sh:targetObjectsOf`) and rules combined with `sh:or`, `sh:xone` or `sh:not`.
- **Coverage is counted apart from agreement.** `compared` is the *overlap* — the keys both sides state — and `agreeing` is that minus the contradictions and differences. Counting the schema's whole surface would score a file's silence about a property as disagreement.
- **Value types are compared by the classes they admit.** Every `sh:class` stated applies, so the admitted classes are their intersection, and a type list (`sh:path (p rdf:type) ; sh:in (…)`) admits its members. Two value types sharing no class contradict each other only when the workspace declares every class they name; otherwise the finding is a difference saying they cannot be checked. A compound attribute reads as a blank node of its class (`CompoundAttributes`).

`ConformanceAcrossDocumentsTest`, `ConformanceInheritanceTest`, `ConformanceAcrossProfilesTest` and `ConformanceAgainstOfficialFilesTest` pin these against the official files.

### What one class is constrained by

`GET /api/workspaces/{workspaceName}/schemas/{schemaName}/classes/{classUUID}/shacl` answers in two halves — the shapes the schema generates and the shapes the documents state — and the class dialog folds them into one row per property. The property dialog's endpoints answer the same way for one attribute or association. What makes that possible lives in `SHACLStoringService`:

- **Provenance.** Shapes are read from the union of the enabled documents, and merging is what throws away the file a reader needs in order to change a rule. `ShapeOrigin` puts it back: the document, and the line the shape starts on in that document's own text. `TurtleSubjectLines` finds the lines in one pass over the text with the tokenizer Jena's Turtle parser is built on, against the base the text was parsed with. Shapes are matched to documents by the id the fetchers produce — `RDFNode.toString()` — which is the IRI for a named shape and the scoped label for a blank node. The index uses the same scoped labels as the union, so an inlined property shape is attributed as reliably as a named one.
- **A summary in words.** `PropertyShapesWrapper.summary` is the conjunction of the wrapper's shapes, read by `EffectiveConstraints` and written by `ConstraintFormatter`. Both are shared with the conformance check on purpose, so `0..1, xsd:float` means the same thing in both places.
- **Inline rules.** The lookups that sort shapes onto a class's properties match on `a sh:PropertyShape`, which `sh:property [ … ]` rules usually leave off. The class-shapes method therefore reads the shapes with that type implied for every value of `sh:property` — which SHACL says it is anyway — so an inline rule lands on its property's row instead of only inside its node shape.
- **Prefixes.** The prefixes returned with the rules (`…/shacl/custom/namespaces` for the property dialog) are the union's, so a rule from a document applying to another schema, or to none, reads with the prefixes it was written with.

Neither dialog writes. A rule shown there may be merged from several documents, so it has no one place to be written back to; the dialogs link to the document and line instead, and the workbench edits the text.

## Diagram layout

Layout positions are persisted as RDF following the Diagram Layout profile of CGMES (IEC 61970-453), with the vocabulary in `dl/rdf/`. The DTOs the renderers read live in `api/dto/rendering/` with renderer-specific subdirectories (`svelteflow/`, `mermaid/`), the DTOs for saving a layout in `api/dto/dl/`. When changing the layout schema, update both the `dl/` model and any code that reads or writes layout data.

- **Classes.** A class has one `DiagramObject` per diagram with a single `DiagramObjectPoint`, which references a `DiagramObjectGluePoint` of the class.
- **Edges.** Every inheritance and association edge between two classes of a diagram has a `DiagramObject` of its own. It references one end of the edge as its identified object and the other through `rdfa:DiagramObject.otherClass`, which is not part of the profile (`dl.data.dto.relations.EdgeKey`). Its edge points carry a `sequenceNumber` and are ordered numerically, counted from the side of the identified object. The first and the last point may be docking points, which pin an end of the edge to the class border: they reference the glue point of their class, and a moved class moves them along. The points in between are bend points.
- **Lazy creation.** Layout data of a class is created when the class is moved or laid out in the diagram for the first time. The edge diagram objects follow: `EdgeLayoutSync` brings them in line with the schema whenever classes get layout data. After a schema change, `SyncDiagramLayoutService` deletes the layout data of classes a diagram no longer shows and syncs the edges as well. It is called inside the transaction of the change, so the change and the sync of the layout are one changelog entry and one undo step.
- **Saving.** The frontend saves the full, ordered list of edge points of an edge (`PUT …/layout/{diagram}/edges`), or classes, edges and labels changed by one action together (`PUT …/layout/{diagram}`). A new point is stored under the UUID the frontend created it with. Every save the user caused is one changelog entry and one undo step; the first layout of a diagram and layouts the editor sends on its own (`automatic`, e.g. the default loop of a new self-association) are stored without a step in the history.

Most `DLObjectFetcher` queries match IRIs with `FILTER(STR(?x) = "...")` and scan the whole layout model, which holds every diagram of the graph. A lookup that runs per edge or per class should follow the model index instead (`listResourcesWithProperty`), like `fetchDOPsForDO` does, or saving a layout gets slow.

## Migration scripts

The migration generator stitches together templates from `src/main/resources/sparql-templates/migration/`. Each template is a parameterised SPARQL UPDATE block — `class-renamed.sparql`, `attribute-renamed.sparql`, etc. — filled in by `SparqlUpdateGenerator`; `SparqlMigrationBuilder` decides which block a given semantic change needs and in which order the blocks are emitted. Both live in `services/schemamigration/artifacts/`. Adding a new migration capability means: (1) adding the template, (2) wiring it into the composer, and (3) extending the wizard's confirmation step DTOs and UI.

The comment a user enters for a change in the wizard's review step is emitted as `#` lines directly above the block it belongs to, so the script carries its own rationale. The wizard's other artefact, the Markdown migration report, is not SPARQL at all — see [Backend Architecture](./backend-architecture#generated-artefacts) for where it is built.
