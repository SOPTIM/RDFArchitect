---
title: SHACL — Constraints and Validation
sidebar_position: 7
---

# SHACL — Constraints and Validation

SHACL (Shapes Constraint Language) is how CGMES and ENTSO-E express the data-quality rules that an exchange file must satisfy: "every `ACLineSegment` must have exactly one `length`", "every `Terminal` must reference a `ConductingEquipment`", and so on. RDFArchitect treats SHACL as a first-class citizen.

![SHACL view](/img/screenshots/shacl.png)

## Two sources of SHACL

RDFArchitect distinguishes two kinds of shapes and stores them separately:

- **Generated SHACL.** Produced by RDFArchitect from the schema itself. Every class becomes a `NodeShape`; every attribute and association becomes a `PropertyShape` with `sh:path`, `sh:datatype` or `sh:class`, `sh:minCount`, `sh:maxCount`, and `sh:in` (for enums) derived from what you modelled. This set is always in sync with the current state of the graph.
- **Custom SHACL.** Shapes that you import or author separately — typically the official SHACL files that ship with a CGMES or ENTSO-E release. These are preserved byte-for-byte and are *not* regenerated when the schema changes.

When you view SHACL for a graph, both sets are shown and clearly labelled.

## Viewing SHACL at graph level

**View → Constrains (SHACL)** opens the full-view dialog. Two tabs: **Generated** (read-only TTL output) and **Custom** (editable TTL). The custom tab has inline TTL syntax highlighting and validates as you type; the save button stays disabled until the TTL parses.

## Viewing SHACL at class level

In the class editor, every attribute and association row has a SHACL icon. Clicking it opens the **property-specific SHACL dialog** — the subset of both generated and custom shapes that target that exact property on that exact class. This is by far the fastest way to answer the question *"what constraint is enforced on this attribute?"* without leaving the class you are looking at.

A similar dialog exists at the class level to inspect the full `NodeShape` of the selected class.

## Importing custom SHACL

**File → Import → Constrains (SHACL)** uploads a SHACL file into the currently selected graph. Supported formats are the same as for schema import (TTL, RDF/XML, N-Triples); TTL is the default and recommended format.

## Exporting SHACL

**File → Export → Constrains (SHACL)** downloads a SHACL file. The dialog asks which dataset and graph to use, which parts to include (generated, custom, or both), and in which format. TTL is the default.
