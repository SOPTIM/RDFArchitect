---
title: Sharing and Exporting
sidebar_position: 11
---

# Sharing and Exporting

## Exporting a schema

**File → Export → Schema (RDFS)** exports the currently selected graph to a file. The dialog includes:

- **Format** — RDF/XML (`.rdf`), Turtle (`.ttl`), or N-Triples (`.nt`). RDF/XML is the default for CGMES/ENTSO-E compatibility.
- **Namespaces** — the exported file uses the active namespace table for its prefixes.
- **Profile header** — whether to emit the ontology block first (matching the ENTSO-E release convention) and, if so, whether to auto-generate any missing standard entries from the graph metadata.

The exported file is self-contained: it can be re-imported into RDFArchitect, loaded into any SPARQL engine, or handed to downstream CIM tooling.

## Exporting SHACL

See [SHACL — Exporting](./shacl#exporting-shacl). TTL by default.

## Exporting documentation

**File → Export → Documentation (HTML, AsciiDoc)** exports documentation for the currently selected graph. The dialog includes:

- **Document Format** — HTML (`.html`) or AsciiDoc (`.adoc`). Both describe the same content.
- **Diagram File Type** — PNG or SVG for the package diagrams.

The generated ZIP file contains the document plus an `images/` folder with one diagram per package. The documentation lists all classes, categorized by stereotype, each with its package diagram, comment, native members, inherited members and — for enumerations — its values.

### AsciiDoc output

The AsciiDoc export is meant for documentation toolchains (Asciidoctor, Antora, DocBook or PDF conversion):

- Sections start at level 1 (`==`), so the file renders as a standalone document. To place it deeper inside a larger document, include it with an offset:

  ```asciidoc
  include::EQ.adoc[leveloffset=+1]
  ```

- Every class gets an anchor prefixed with the name of the exported graph, for example `[[EQ_BoundaryPoint]]`. This keeps anchors unique when several profiles are included in the same document, and lets you reference a class from your own text with `xref:EQ_BoundaryPoint[]`.
- Types and super classes are cross-referenced (`xref:`) when the referenced class is part of the same export; classes outside the export are written as plain text so that no reference dangles.
- Package diagrams are referenced as `link:images/<package-uuid>.<png|svg>[<package>]`. Keep the `images/` folder next to the `.adoc` file, or point Asciidoctor at it with `:imagesdir:`.


## Share snapshot

**File → Share Snapshot** creates an immutable snapshot of the currently selected dataset and returns a link of the form `https://<host>/?snapshot=<token>`. Anyone opening that link loads the dataset as it was at the moment the snapshot was taken — packages, classes, associations, SHACL, everything — and can navigate the schema exactly like the author did, without needing to install anything.

![Share snapshot](/img/screenshots/share-snapshot.png)

This is the feature to use when you want reviewers to look at a profile without sending RDF files around. Snapshots are stored in Fuseki and persist until the snapshot dataset is deleted from Fuseki.

Three things to be aware of:

- The snapshot link is *the* access control. Anyone with the link can view.
- Snapshot links load read-only datasets by default. The loaded dataset can be made editable, but the stored snapshot is not modified.
- In the current version, snapshots cannot be deleted via the UI.
