---
title: Editing Classes
sidebar_position: 6
---

# Editing Classes

The class editor is the primary editing surface in RDFArchitect.

![Class editor](/img/screenshots/class-editor.png)

## Opening the editor

Open a class either from the diagram (select or focus the class) or from the package navigation tree. Switching to another class swaps the contents in place.

## What you can edit

The editor exposes everything that defines a class:

### Identity and metadata

- **Label** — the human-readable name (`rdfs:label`).
- **IRI** — the class identifier; must be unique within the schema.
- **Package** — the package the class belongs to.
- **Parent class** — single-inheritance pointer (`rdfs:subClassOf`); may be empty.
- **Stereotype** — UML stereotype (e.g. abstract, concrete, primitive, CIM datatype, enumeration). A class may carry more than one.
- **Comment** — multi-line description (`rdfs:comment`).

### Attributes

A class's attributes are listed for inline editing. Each one carries:

- **Name** — used as the IRI fragment.
- **Datatype** — chosen from a drop-down: XSD primitives, CIM primitives, or another enum class.
- **Multiplicity** — `0..1`, `1..1`, `0..*`, `1..*`, or a custom range.
- **Fixed value** — optional default/constant.
- **Stereotype** — free text.
- **Comment** — per-attribute description.

### Associations

Object-valued properties pointing from this class to another. Each one has a name, a target class, source and target multiplicities, and an optional stereotype (e.g. `aggregate`, `composite`).

Associations show up as labelled edges in the diagram, with arrowheads reflecting navigability.

### Enum entries

Visible only when the class is an enumeration. Each entry has a name (becomes the IRI fragment) and an optional comment. Order is preserved so an enumeration can be displayed and exported in a stable sequence.

### SHACL

The editor lists every shape that targets the current class, distinguishing **generated** (auto-derived) and **custom** (imported) shapes. Clicking a property shape reveals its constraints. See [SHACL](./shacl) for the full picture.

## Renames

Renaming a class updates every association inside the same schema that referred to it. The change is recorded in the history with the old and new IRI.

## Validation on save

RDFArchitect runs in-form validity checks before allowing save:

- Empty mandatory fields are highlighted.
- Duplicate IRIs (across the whole schema) are blocked.
- Malformed multiplicity strings are rejected.

Unsaved changes are kept locally as long as the editor stays open. Switching to another class warns about pending edits before discarding them.

## Deleting a class

The delete action asks you to acknowledge that:

- All attributes and associations on this class will be deleted.
- All associations from *other* classes that target this one will be deleted.
- The deletion is reversible via the change history.
