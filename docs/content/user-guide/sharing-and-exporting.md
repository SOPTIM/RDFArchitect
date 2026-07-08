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

## Share snapshot

**File → Share Snapshot** creates an immutable snapshot of the currently selected dataset and returns a link of the form `https://<host>/?snapshot=<token>`. Anyone opening that link loads the dataset as it was at the moment the snapshot was taken — packages, classes, associations, SHACL, everything — and can navigate the schema exactly like the author did, without needing to install anything.

![Share snapshot](/img/screenshots/share-snapshot.png)

This is the feature to use when you want reviewers to look at a profile without sending RDF files around. Snapshots are stored in Fuseki and persist until the snapshot dataset is deleted from Fuseki.

Three things to be aware of:

- The snapshot link is *the* access control. Anyone with the link can view.
- Snapshot links load read-only datasets by default. The loaded dataset can be made editable, but the stored snapshot is not modified.
- In the current version, snapshots cannot be deleted via the UI.

## Deep links

The editor page accepts URL parameters that select a model on load, so external tools (for
example the [CIMNotebook](https://opencgmes.soptim.de/cimnotebook/overview) IDE extensions) can
link straight to a diagram or class:

```
https://<host>/mainpage?dataset=<name>&graph=<uri>&package=<uuid|iri|default>&class=<iri|uuid>
```

- `dataset`, `graph`, `package` — open the given package diagram. `package` accepts a package
  UUID, a package IRI, or `default` for the default package.
- `class` — select a class (by IRI or UUID) and open the package diagram containing it. When
  `dataset`/`graph` are omitted, every schema in the session is searched for the class.

All parameters refer to the browser session's own datasets. They can be combined with a
snapshot link — `/?snapshot=<token>&class=<iri>` first loads the snapshot, then navigates to
the class.
