---
title: RDF Data Model
sidebar_position: 7
---

# RDF Data Model

This page is a developer-facing summary of the vocabulary stored on disk. The user-facing reference for the same vocabulary is at [CIM/CGMES Mapping](/reference/cim-mapping); read that page first if you only want the structure of packages, classes, attributes, associations, and enum entries.

## What's actually persisted

For each schema, RDFArchitect persists:

1. **Schema content** — `rdfs:Class` resources, properties (`rdf:Property`), packages (`cims:ClassCategory`), enum entries, plus the CIM extension predicates documented in [CIM/CGMES Mapping](/reference/cim-mapping).
2. **Custom SHACL** — anything reached from a `sh:NodeShape` / `sh:PropertyShape` in an imported file or a SHACL upload.
3. **Diagram layout annotations** — coordinates and box dimensions used by the diagram view. These are stored as triples on the same resources (positioned classes, packages, etc.) using a private RDFArchitect-internal namespace; they are emitted on export when "include layout" is selected and re-consumed on the next import.
4. **History entries** — written for every model mutation; readable from the history view.
5. **Snapshots** — frozen copies of a schema, stored separately from the working copy.

The **generated** SHACL is *not* persisted. It is recomputed from the schema on demand and emitted at export time. Round-trip determinism is the property that makes this safe.

## Namespaces in use

| Prefix | IRI | Used for |
| ------ | --- | -------- |
| `rdf` | `http://www.w3.org/1999/02/22-rdf-syntax-ns#` | Standard RDF terms. |
| `rdfs` | `http://www.w3.org/2000/01/rdf-schema#` | Class/property metadata. |
| `owl` | `http://www.w3.org/2002/07/owl#` | `owl:Class`, `owl:Ontology`. |
| `xsd` | `http://www.w3.org/2001/XMLSchema#` | Datatypes. |
| `sh` | `http://www.w3.org/ns/shacl#` | SHACL. |
| `cim` | configurable | The active CIM model (e.g. `http://iec.ch/TC57/CIM100#`). |
| `cims` | `https://iec.ch/TC57/1999/rdf-schema-extensions-19990926#` | CIM RDFS extensions (multiplicity, datatype, stereotype, …). |
| Internal | (not part of the CIM vocabulary) | Diagram layout, snapshot bookkeeping, history bookkeeping. |

## Conventions for schema content

See [CIM/CGMES Mapping](/reference/cim-mapping) for the canonical Turtle skeleton of packages, classes, attributes, associations, and enum entries, plus the recognised stereotypes and the `cims:multiplicity` syntax.

## Working with internal annotations

When you need to add a new internal annotation (e.g. a new layout property), put it in the RDFArchitect-internal namespace, never in `cims:`. CIM's `cims:` predicates are an external contract; muddying them breaks interoperability with other tools. The exporter is responsible for stripping or emitting internal annotations as configured.

## Round-trip considerations

- **Blank nodes** are renamed deterministically on import. Identity is by structure, not by identifier.
- **Datatype precision** matters: `xsd:integer` and `xsd:int` are distinct datatypes to Jena. Do not normalise them silently.
- **Order**. RDF is a set, not a sequence. Where order is meaningful (enum entries, attribute display order) it is stored explicitly via additional ordering predicates.
- **Whitespace in literals**. Comments contain newlines and indentation that should round-trip. The compare engine normalises whitespace for diffing but never normalises it on disk.
