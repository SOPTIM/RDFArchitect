---
title: Core Concepts
sidebar_position: 2
---

# Core Concepts

RDFArchitect is built around a small set of concepts derived from RDF and UML. \
This page provides a brief introduction to these concepts.

## Dataset

A **dataset** is a top-level container. It holds a collection of schemas, plus per-dataset namespace prefixes.

## Schema

A **schema** is the editable artifact you spend most of your time with. It contains all the packages, classes, attributes, associations, enumerations, and SHACL constraints of one model. In CIM/CGMES terms, a schema is what's called a *profile*.

Each dataset contains one or more schemas. Most editing actions target the currently active schema:

- Comparing two schemas produces a diff.
- The change history, snapshots, and version restore are per-schema.
- Read-only locking is per-schema.

Importing a Turtle / RDF/XML / N-Triples file places its content into the active dataset, either as a new schema or by replacing an existing one. You can keep many schemas side by side — for example one per CGMES profile, or one per CIM version while you plan a migration.

## Package

A **package** groups classes that belong together logically. Packages are the primary navigation aid: the navigation tree groups classes by package, and the diagram view focuses one package at a time.

A class always belongs to exactly one package.

## Class

A **class** is the central modeling element. In CIM terms, every `Breaker`, `BaseVoltage`, `Terminal`, etc. is a class. A class carries:

- An IRI and a human-readable label.
- Zero or one parent class (single inheritance).
- A set of **attributes** (typed literal properties).
- A set of **associations** (object properties pointing to other classes).
- For enumerations: a set of enum entries.
- A package assignment.
- Optional descriptive comment.
- Optional class-level SHACL constraints.

## Attribute

An **attribute** is a literal-valued property: a name, a datatype (string, integer, boolean, date, …), a multiplicity, and optional metadata such as a fixed value or stereotype.

## Association

An **association** points from one class to another. It carries a name, a multiplicity on each end, and (optionally) a stereotype. Associations show up as edges in the diagram.

## Enumeration

An **enumeration** is a class whose only purpose is to define a fixed set of allowed values (e.g. `BreakerType` with entries `Air`, `Vacuum`, `SF6`). Enum entries are first-class citizens with their own IRIs.

## SHACL Shapes

[**SHACL**](https://www.w3.org/TR/shacl/) (Shapes Constraint Language) is the W3C standard for validating RDF data. RDFArchitect treats SHACL in two complementary ways:

- **Generated shapes** are derived from your model automatically. Every class becomes a `sh:NodeShape`, every attribute and association becomes a `sh:PropertyShape`.
- **Custom shapes** are SHACL files you author yourself (or import). They live alongside the generated shapes and can express constraints that don't fit naturally into RDFS — pattern matches, value ranges, `sh:sparql` constraints, and so on.

Both are inspectable from the SHACL views and from the class editor.

## Namespace and Prefix

A **namespace** is the IRI base used for resources in your schema (e.g. `http://iec.ch/TC57/CIM100#`). A **prefix** is the short name you use for it in serialized RDF (`cim:`).

See [Prefixes](./prefixes) for managing them.

## Snapshot

A **snapshot** is a frozen copy of a schema at a point in time, made primarily for sharing. When a reviewer follows a snapshot link, they see a read-only view of the schema as it was when the snapshot was taken — they can browse, compare, and inspect, but not edit it through that link.

A snapshot itself is just a schema-shaped artifact. You (or anyone with appropriate access) can import it back into a dataset as a new schema and continue editing from there. Snapshots are therefore stable reference points, not permanent locks: they preserve a state for review without preventing anyone from picking up that state and continuing from it.

## Change history

Every write operation against a schema is recorded. The history view lets you inspect changes, undo, redo, and restore earlier states. See [History](./history).

## Read-only mode

A schema can be marked **read-only**. Read-only schemas are perfectly browsable but every editing affordance is hidden or disabled.
