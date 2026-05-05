---
title: Features Reference
sidebar_position: 1
---

# Features Reference

A structured checklist of what RDFArchitect does, organised by the role most likely to care about it. Each feature links to the full explanation in the [User Guide](/user-guide/overview) where appropriate.

## For electrical engineers and CIM modellers

### Import a CGMES / ENTSO-E profile

Open `.rdf`, `.ttl`, or `.nt` files directly from the UI. Every file becomes one graph inside a dataset of your choice. Multiple profiles from the same release (EQ / TP / SSH / SV, or the different Network Code Profiles) can live side-by-side in one dataset.

### Navigate a schema by package

The navigation tree mirrors the UML package structure of the profile. The diagram canvas always shows exactly the package you selected, with associations that cross into other packages drawn at the boundary. External packages (imported dependencies like the CGMES Core) are visible but non-editable.

### Inspect a class in full

The class editor shows label, URI namespace, package, super class, stereotypes (`«enumeration»`, `«CIMDatatype»`, `«Primitive»`, `«Compound»`, …), AsciiDoc comment, all attributes, all associations, enum entries if applicable, and the SHACL shapes targeting that class — all on one page.

### Create and edit classes, attributes, associations, enum entries

Full create/update/delete for every modelling element, with inline validation (duplicate labels, duplicate URIs, invalid characters, cardinality conflicts).

### Primitive datatypes

Attribute datatypes are offered from the full CIM primitive datatype set (`ActivePower`, `ApparentPower`, `Voltage`, `PerCent`, `CurveStyle`, etc.) in addition to the XSD base types.

### Enum entries as first-class content

When a class carries the `«enumeration»` stereotype, its enum entries appear as an ordered list in the class editor. Each entry has label, URI, and comment and is exported as SHACL `sh:in` automatically.

### Work with the profile header

Edit the ontology metadata block (title, version, conformsTo, keyword, license, description, …). A library of known ENTSO-E and DCAT fields can be added with one click.

### UML-style diagrams

Diagrams render classes with full attribute and association detail, inheritance arrows, stereotypes, and cardinalities. SvelteFlow is the default renderer (pan/zoom/auto-layout); Mermaid is available as an alternative, for example for embedding in Markdown-based documentation.

### Visualise SHACL constraints on the class you are looking at

Every attribute and association row in the class editor has a SHACL icon that opens exactly the constraints that target that property on that class. No need to grep through a SHACL file.

## For software architects and schema maintainers

### Dataset / graph / package model

One triple store, many datasets, many graphs per dataset, many packages per graph. The hierarchy is enforced by the UI and by the REST API, and every edit is scoped by it.

### Namespace management

Per-dataset namespace table with validation for unique prefixes and warnings for namespaces that are still in use. Export honours the active namespace table.

### Undo, redo, and version history

Every edit is journaled. Up to 256 versions per graph by default; older states are compressed as history grows. Undo and redo are global per graph and available via menu and keyboard shortcut.

### Changelog view

A dedicated page that lists the change history of a graph: additions, updates, deletions, grouped by class, with full triple-level diff (green for additions, red for deletions) for each change.

### Restore previous versions

Roll a graph back to any earlier state from the changelog. The restore itself is a new history entry, so it is undoable.

### Schema comparison

Three-way compare: stored↔stored, upload↔stored, upload↔upload. Package-level and class-level summary with drill-down into attributes, associations, enum entries, and comments. Whitespace-normalised comment diffs avoid noise.

### Schema migration wizard

A five-step wizard that turns the difference between two schema versions into a **SPARQL UPDATE script**:

1. Select source and target schemas.
2. Confirm class renames.
3. Confirm attribute / association / enum entry renames.
4. Choose default values for newly added properties.
5. Generate and download the script.

The script can be run against any SPARQL 1.1 endpoint to migrate instance data from the source schema to the target.

### SHACL generation

SHACL shapes are generated automatically from the schema: `NodeShape` per class, `PropertyShape` per attribute/association, `sh:datatype`, `sh:class`, `sh:minCount`, `sh:maxCount`, `sh:in` for enumerations. Always in sync with the schema.

### Custom SHACL side-by-side

Import the official SHACL of a CGMES/ENTSO-E release and store it next to the generated shapes. The custom SHACL is preserved byte-for-byte and not regenerated when the schema changes. Both sets are visible at graph level and at class level.

### Read-only datasets

A toggle that locks a dataset against all editing actions. Used to protect released profiles and to make shared snapshots safe.

### Snapshots

One-click immutable copy of a dataset, stored in the triple store, reachable by a URL with a base64 token. Anyone with the URL can view the full dataset — navigation, diagrams, SHACL — in read-only mode without installing anything.

### Export

Every graph can be exported to RDF/XML, Turtle, or N-Triples, with configurable namespace prefixes and an optional auto-generated profile header placed as the first resource (matching ENTSO-E release conventions). SHACL can be exported separately as TTL.

### Open REST API

Every feature in the UI is backed by a REST endpoint. Swagger UI is served at `/swagger-ui.html` on the backend and lists every operation with full request/response schemas. This makes RDFArchitect scriptable for CI pipelines (e.g. export a canonical representation on every commit, run a diff in a pull request, generate a release note from the changelog).

## For project managers and product owners

### Sharing and review

**Snapshots** let you hand a complete, navigable view of a profile to a stakeholder without installing software, without sending RDF files, and without risk of accidental edits. This is the primary feature for review cycles between modellers and non-modellers.

### Changelog and release notes

Every change is tracked with who, what, and when. The changelog view in the UI is directly usable as a starting point for release notes.

### Compare releases

The compare view produces a structured difference between two schema versions that can be read at package level, at class level, or drilled down to attribute level. Suitable for a release readiness meeting.

### Migration planning

The migration wizard externalises every decision that has to be made to migrate instance data from one profile version to the next: class renames, property renames, defaults for new fields. The output is a reviewable SPARQL script that can be run in a controlled way on staging data before touching production.

### No vendor lock-in

Apache 2.0 licence. Data is RDF throughout; imports and exports are standard W3C formats. The triple store is Apache Jena Fuseki. The generated migration scripts are plain SPARQL. Nothing in the tool chain is proprietary.

### Predictable dependencies

Spring Boot backend, SvelteKit frontend, Apache Jena, Apache Jena Fuseki. All mature, widely adopted, and available as container images.

## Feature-by-feature: where in the UI

| Feature                             | Location                                         |
| ----------------------------------- | ------------------------------------------------ |
| Import schema (RDFS)                | File → Import → Schema (RDFS)                    |
| Import SHACL                        | File → Import → Constrains (SHACL)               |
| Export schema                       | File → Export → Schema (RDFS)                    |
| Export SHACL                        | File → Export → Constrains (SHACL)               |
| Share snapshot                      | File → Share Snapshot                            |
| Delete schema                       | File → Delete → Schema (RDFS)                    |
| Delete dataset                      | File → Delete → Dataset                          |
| New class                           | Edit → New → Class                               |
| New package                         | Edit → New → Package                             |
| New empty schema                    | Edit → New → Schema (RDFS)                       |
| Edit profile header                 | Edit → Edit → Profile header                     |
| Edit package                        | Edit → Edit → Package                            |
| Undo / Redo                         | Edit → Undo / Redo (Ctrl+Z / Ctrl+Y)             |
| Enable / Disable editing            | Edit → Enable / Disable Editing                  |
| Manage namespaces                   | Edit → Manage Namespaces                         |
| Delete profile header               | Edit → Delete → Profile header                   |
| Delete package                      | Edit → Delete → Package                          |
| Changelog                           | View → Changelog                                 |
| Compare schemas                     | View → Compare Schemas                           |
| Migrate schema (5-step wizard)      | View → Migrate Schema                            |
| Full SHACL view (generated+custom)  | View → Constrains (SHACL)                        |
| Help / feedback / about             | Help menu                                        |
| Search across the dataset           | Search bar at the top                            |
| Property-specific SHACL             | Class editor → SHACL icon next to each row       |
| Class-level SHACL                   | Class editor → SHACL section                     |
| API documentation (Swagger UI)      | `http://<backend-host>:8080/swagger-ui.html`     |

## Current limitations (as of 1.0.0)

- The migration script generator does **not** yet handle every edge case; multiplicity changes on associations in particular need manual review. It is strongly recommended to validate migrated data against the target profile's SHACL after running the script.
- Snapshots cannot currently be deleted via the UI.
- There is no multi-user conflict resolution; two people editing the same graph simultaneously will see last-write-wins behaviour.
- Access control is per-snapshot-link; there is no built-in user management. For multi-user deployments this is typically handled by putting the service behind an SSO-capable reverse proxy.
