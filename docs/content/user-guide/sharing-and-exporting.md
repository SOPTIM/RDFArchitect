---
title: Sharing and Exporting
sidebar_position: 11
---

# Sharing and Exporting

## Exporting a schema

**File → Export → Schema (RDFS)** exports the currently selected graph to a file. The dialog lets you choose:

- **Format** — RDF/XML (`.rdf`), Turtle (`.ttl`), or N-Triples (`.nt`). RDF/XML is the default for CGMES/ENTSO-E compatibility.
- **Namespaces** — which prefixes to include in the exported file's header.
- **Profile header** — whether to emit the ontology block first (matching the ENTSO-E release convention) and, if so, whether to auto-generate any missing standard entries from the graph metadata.

The exported file is self-contained: it can be re-imported into RDFArchitect, loaded into any SPARQL engine, or handed to downstream CIM tooling.

## Exporting SHACL

See [SHACL — Exporting](./shacl#exporting-shacl). TTL by default.

## Share snapshot

**File → Share Snapshot** creates an immutable snapshot of the currently selected dataset and returns a link of the form `https://<host>/?snapshot=<token>`. Anyone opening that link sees a read-only copy of the dataset at the moment the snapshot was taken — packages, classes, associations, SHACL, everything — and can navigate the schema exactly like the author did, without needing to install anything.

![Share snapshot](/img/screenshots/share-snapshot.png)

This is the feature to use when you want reviewers to look at a profile without sending RDF files around. Snapshots are stored in the underlying triple store (Fuseki) and persist until the triple store is reset.

Three things to be aware of:

- The snapshot link is *the* access control. Anyone with the link can view.
- Snapshots are read-only by construction — the viewer cannot accidentally edit.
- In the current version, snapshots cannot be deleted via the UI.
