---
title: SHACL
sidebar_position: 8
---

# SHACL Constraints

SHACL ([Shapes Constraint Language](https://www.w3.org/TR/shacl/)) is the W3C standard for validating RDF data. RDFArchitect treats SHACL as a first-class part of the schema and supports two kinds of shapes side by side: **generated** and **custom**.

![SHACL view](/img/screenshots/shacl.png)

## Generated vs. custom shapes

| | Generated | Custom |
| - | --------- | ------ |
| **Source** | Auto-derived from the schema. | Authored by you (or imported). |
| **Lifecycle** | Recomputed every time the model changes. | Persists with the schema until you change it. |
| **Editable in UI** | No — change the schema instead. | Yes — via SHACL upload or external editing. |
| **Persisted** | Not stored in the schema. | Stored alongside the schema. |
| **Exported** | Yes (when you select "include generated"). | Yes, always. |

## What gets generated

For every class in the model RDFArchitect emits:

- A `sh:NodeShape` with `sh:targetClass` pointing to the class.
- One `sh:property` per attribute, including `sh:datatype`, `sh:minCount`, and `sh:maxCount` derived from the multiplicity.
- One `sh:property` per association, with `sh:class` and the same cardinality treatment.
- For enumerations, a `sh:in` constraint listing the allowed entries.
- Cardinality constraints inherited from the parent class.

A typical CIM class produces roughly 10–30 generated shapes — enough to validate that instance data matches the structural model without any manual SHACL authoring.

## What custom shapes are for

Generated shapes cover *structural* constraints. Custom shapes are where you express constraints that aren't derivable from the schema:

- Pattern matches (`sh:pattern` for ID formats).
- Value ranges (`sh:minInclusive`, `sh:maxInclusive`).
- Cross-property invariants via `sh:sparql`.
- Conditional shapes via `sh:and`, `sh:or`, `sh:xone`, `sh:not`.
- Domain-specific severity levels and messages.

## Inspecting SHACL

Three views, depending on the question:

- **Class-specific view** — inside the class editor, showing every shape that targets the current class. Generated shapes are tagged accordingly; custom shapes are clearly distinguished.
- **Property-specific view** — focuses on a single property: every shape (across the schema) that constrains it, with its severity, message, and any custom predicates.
- **Full schema view** — every shape in the schema in one searchable list, filterable by generated/custom, target class, property path, or constraint type.

## Importing SHACL

Two paths:

- **Inside an imported schema file.** Any `sh:NodeShape` / `sh:PropertyShape` reachable from `rdf:type` is recognised and stored as custom shapes.
- **Into an existing schema.** A dedicated SHACL upload dialog adds shapes without touching the existing model. Conflicts (e.g. an imported shape with the same IRI as an existing one) are surfaced before they are committed.

## Exporting SHACL

The SHACL export lets you choose generated, custom, or both, and the serialization (Turtle is the default).

Exporting "both" produces a file that any standard SHACL validator (Apache Jena's `shacl validate`, TopBraid SHACL, pySHACL, …) will accept directly.

## Severity and messages

Custom shapes can carry `sh:severity` (`sh:Violation`, `sh:Warning`, `sh:Info`) and `sh:message` per language tag. Both are surfaced in the inspection views and preserved on round-trip.

Generated shapes are emitted with `sh:Violation` severity by default.

## Working without SHACL

If you don't care about constraints, you can ignore the SHACL views entirely. Generated shapes are produced on demand and never bloat your stored schema; custom shapes only exist if you import them.
