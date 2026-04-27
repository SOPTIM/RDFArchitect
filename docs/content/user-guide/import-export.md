---
title: Import and Export
sidebar_position: 5
---

# Import and Export

RDFArchitect speaks the standard RDF serializations and a clearly defined SHACL convention.

## Importing a schema

Open the import dialog from the toolbar.

![Import schema dialog](/img/screenshots/import-schema.png)

You pick the file and choose whether to import it into a **new schema** (gives it a name) or **replace** the contents of an existing schema. Importing replaces — the previous state is captured in the history and is therefore restorable.

### Supported serializations

| Format | Extension | Notes |
| ------ | --------- | ----- |
| Turtle | `.ttl` | Recommended default. |
| RDF/XML | `.rdf`, `.xml` | Works for the standard CIM XMI/RDF exports. |
| N-Triples | `.nt` | |
| N-Quads | `.nq` | Quads are preserved if the file contains named graphs. |
| TriG | `.trig` | |
| JSON-LD | `.jsonld`, `.json` | |

The serialization is detected from the file extension.

### What gets recognised

During import RDFArchitect classifies triples into three buckets:

1. **Schema content** — `rdfs:Class`, `rdf:Property`, packages (`cims:ClassCategory`), enum entries, attribute and association declarations, multiplicity, stereotypes, fixed and default values, etc.
2. **SHACL shapes** — anything reached from a `sh:NodeShape` or `sh:PropertyShape`. These become the **custom shapes** for the schema.
3. **Other triples** — preserved verbatim. RDFArchitect does not silently drop triples it doesn't understand; they round-trip on export.

### Layout information

If the imported file contains diagram-layout annotations produced by a previous RDFArchitect export, the layout is restored. Otherwise the diagram engine computes a layout automatically the first time you open a package.

## Adding SHACL to an existing schema

Importing a SHACL-only file *into an existing schema* adds those shapes alongside the model — the recommended way to add hand-written constraints to a schema you already imported. See [SHACL](./shacl).

## Exporting a schema

Open the export dialog from the toolbar.

You can choose:

- **What to include** — schema only, SHACL only, or both.
- **The serialization** — Turtle is the default and recommended for human-readable diffs.
- **Whether to include diagram layout** — exports the layout annotations so a re-import preserves the visual positioning.

The first resource in an export is always the `Ontology` declaration (with its prefixes), so re-imports are deterministic.

## Round-trip guarantees

RDFArchitect aims for a **lossless round-trip**: import → save → export should produce semantically identical RDF.

- All triples are preserved, including ones the editor doesn't display.
- Prefixes from the imported file are merged into the dataset's prefix list.
- Diagram layout is preserved when you opt to include it.
- Custom SHACL shapes are preserved unchanged.

Generated SHACL shapes are *not* persisted — they are computed on demand and emitted at export time, so they always reflect the current schema.

## What does not survive

- **Comments inside the source file** (`# this is a comment`). RDF storage does not preserve them.
- **Specific blank-node identifiers.** Blank nodes are renamed deterministically on import.
- **Non-canonical literal forms.** For example, `"true"^^xsd:boolean` and `true` collapse to the same value.

If you need exact byte-for-byte preservation, keep the source file in a separate version-control repository alongside RDFArchitect.
