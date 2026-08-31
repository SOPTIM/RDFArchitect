---
title: SHACL — Constraints
sidebar_position: 7
---

# SHACL — Constraints

SHACL (Shapes Constraint Language) is how CGMES and ENTSO-E express the data-quality rules that an exchange file must satisfy: "every `ACLineSegment` must have exactly one `length`", "every `Terminal` must reference a `ConductingEquipment`", and so on. RDFArchitect can generate, import, edit, view, and export SHACL rules; validation of instance data is done outside RDFArchitect.

![SHACL view](/img/screenshots/shacl.png)

## Two sources of SHACL

RDFArchitect distinguishes two kinds of shapes and stores them separately:

- **Generated SHACL.** SHACL shapes that can be derived from the schema itself. They include, among other things, constraints for the multiplicity of associations and the datatype of attributes. This set is always in sync with the current state of the graph.
- **Custom SHACL.** Shapes that you import or author separately — typically the official SHACL files that ship with a CGMES or ENTSO-E release. These can be edited freely in RDFArchitect and are *not* regenerated when the schema changes.

When you view SHACL for a graph, both sets are shown and clearly labelled.

Custom SHACL is held as a **list of documents** rather than one block of text, because a schema's constraints normally arrive as several official files plus whatever you add yourself. Every enabled document applies and none overrides another: two documents that contradict each other are *reported*, never silently resolved. Custom shapes do take precedence over generated ones for the same resource, since the generated ones are derived defaults.

## The constraints workbench

**View → Constraints (SHACL)** (`Ctrl+Shift+L`) opens the workbench for the selected schema.

- **Documents** (left) lists the graph's constraints documents. Add an empty one, import a file, rename, reorder, delete, and switch a document off so it takes no part in validation or the combined export. Each row carries a badge summarising what validation found in it.
- **Editor** (middle) edits the open document as Turtle, with syntax highlighting that extends into the SPARQL inside `sh:select`, and squiggles under whatever validation objects to. `Ctrl+S` saves; `F8` walks the problems.
- **Inspector** (right) shows what the document is, the shapes it declares — click one to jump to it — and which profiles the constraints are being checked against.
- **Problems** (bottom) collects everything found across all of the documents. Clicking an entry opens the document it belongs to and puts the cursor on it.

### What validation checks

Shapes are checked against the live CIM schema of the whole workspace: do the classes and properties they mention exist, do their cardinalities agree with the schema's multiplicities, does the SPARQL embedded in them parse and refer to real terms, and do the documents contradict each other.

The check spans *every* schema in the workspace, not only the one the document belongs to. Official ENTSO-E cross-profile constraints files reference terms from neighbouring profiles on purpose, and checking against one profile alone would report all of those as unknown.

This is validation of the *shapes*, not of instance data — an exchange file is still validated outside RDFArchitect.

## Viewing SHACL at class level

In the class editor, every attribute and association row has a SHACL icon. Clicking it opens the **property-specific constraints (SHACL) dialog** — the subset of both generated and custom shapes that target that exact property on that exact class. This is by far the fastest way to answer the question *"what constraint is enforced on this attribute?"* without leaving the class you are looking at.

A similar dialog exists at the class level to inspect the NodeShapes and the PropertyShapes related to the properties of the selected class.

## Importing custom SHACL

**File → Import → Constraints (SHACL)** uploads a SHACL file into the currently selected graph. Supported formats are the same as for schema import (TTL, RDF/XML, N-Triples); TTL is the default and recommended format.

## Exporting SHACL

**File → Export → Constraints (SHACL)** downloads a SHACL file. The dialog asks which workspace and graph to use, which parts to include (generated, custom, or both), and in which format. TTL is the default.
