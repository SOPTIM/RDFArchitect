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

This is the feature to use when you want reviewers to look at a profile without sending RDF files around. Snapshots are stored in Fuseki and persist until the snapshot dataset is deleted from Fuseki. When no Fuseki server is reachable, snapshots fall back to the backend's memory: the links still work across sessions, but they do not survive a backend restart.

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
- `class` — select a schema term and open the package diagram containing it. `dataset` and `graph`
  narrow where it is looked for; either works on its own, and without both, every schema in the
  session is searched until the term is found.

Despite its name, `class` accepts any term of the model:

| The IRI names an…                     | What the editor opens                                       |
| ------------------------------------- | ----------------------------------------------------------- |
| class (also accepted as a UUID)       | the class, selected in its package diagram                   |
| attribute, association or enum entry  | the class **declaring** it, with that row revealed and briefly highlighted |

So `?class=cim:ACLineSegment.r` opens `ACLineSegment` and points at its `r` attribute. Note that a
term is declared once: deep-linking an inherited attribute opens the superclass that declares it,
not the class you happened to read it on. A term that no schema in the session contains — including
one whose class was deleted — reports "Not found".

A CIM term is usually declared in **several profiles**, and an unqualified `class` link opens
whichever graph the term is found in first. Add `graph` to say which profile you mean —
`?class=<iri>&graph=<profile graph>` — which is what the CIMNotebook IDE extensions send when you
pick a profile there.

All parameters refer to the browser session's own datasets. They can be combined with a
snapshot link — `/?snapshot=<token>&class=<iri>` first loads the snapshot, then navigates to
the term.
