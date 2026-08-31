---
title: SHACL — Constraints
sidebar_position: 7
---

# SHACL — Constraints

SHACL (Shapes Constraint Language) is how CGMES and ENTSO-E express the data-quality rules that an exchange file must satisfy: "every `ACLineSegment` must have exactly one `length`", "every `Terminal` must reference a `ConductingEquipment`", and so on. RDFArchitect generates, imports, edits, validates and exports SHACL rules, and can tell you whether an imported constraints file still agrees with the schema it describes.

Validation of *instance data* — checking a CIM/XML exchange file against the shapes — is deliberately left to other tools. Everything here is about the shapes themselves.

## Two sources of SHACL

RDFArchitect distinguishes two kinds of shapes and stores them separately:

- **Generated SHACL.** Shapes derived from the schema itself — the multiplicity of associations, the datatype of attributes, and so on. This set is always in sync with the current state of the graph and cannot be edited: change the schema and it changes with it.
- **Custom SHACL.** Shapes you import or author — typically the official SHACL files that ship with a CGMES or ENTSO-E release, plus whatever your organisation adds. These are yours to edit and are *not* regenerated when the schema changes.

Custom SHACL is held as a **list of documents** rather than one block of text, because a schema's constraints normally arrive as several official files. Two rules follow from that, and they are worth knowing:

- **Every enabled document applies, and none overrides another.** SHACL is conjunctive: constraints add up. Two documents that contradict each other are therefore *reported* as a problem, never silently resolved in favour of one of them.
- **Custom shapes do take precedence over generated ones** for the same resource, because the generated ones are derived defaults.

Switching a document **off** means it takes no part in validation or in a combined export. It is still there, still editable, and can still be exported on purpose.

## The constraints workbench

**View → Constraints (SHACL)** (`Ctrl+Shift+L`) opens the workbench for the selected schema. It is also reachable from a schema's context menu and from the constraints popup in the class editor.

- **Documents** (left) lists the graph's constraints documents: add an empty one, import a file, rename, reorder, delete, and switch one off. Each row carries a badge summarising what validation found in it, so a file with problems is visible without opening it. The first row is not a document but the **generated rules** — what RDFArchitect derives from the schema itself, shown read-only so you can read it beside whatever you imported.
- **Editor** (middle) shows the open document in one of three views — see below.
- **Inspector** (right) shows what the document is, the shapes it declares — click one to jump to it — and which profiles the constraints are being checked against.
- **Problems** (bottom) collects everything found across *all* of the documents. Clicking an entry opens the document it belongs to and puts the cursor on it.

In a **read-only** workspace the workbench reads and validates as usual, but nothing can be changed and the header says so.

### Turtle view

The document as text, with syntax highlighting that extends into the SPARQL inside `sh:select`, and squiggles under whatever validation objects to. `Ctrl+S` saves; `F8` walks from one problem to the next; hovering a marker shows the message.

The text you wrote is what is stored, byte for byte — comments and ordering included. That matters for the official ENTSO-E files, which carry both and which you will want back unchanged.

Typing gets you more than highlighting:

- **Completion** on `:` offers the classes, properties and enumeration members that the workspace's schema actually declares, written the way the open document writes them. You never have to type an IRI.
- **Hover** over a term shows its label, its `rdfs:comment`, and for a property its domain, range and multiplicity, together with the profiles that declare it.
- **Completion** also covers the vocabularies a constraints file is *written in* — `sh:`, `rdf:`, `rdfs:`, `owl:` and the XSD datatypes — with a one-line explanation of each on hover. These are in no CGMES profile, so the schema knows nothing about them.
- **Ctrl+click** a class or property and you land on it in the class editor, with the class highlighted on its package diagram. For a property that means the class it belongs to, since a property is edited as a row of its class. `F12` and the context menu do the same thing.

### Form view

The same document, shown as shapes rather than as text, for people who would rather not read Turtle. Each shape says which class it applies to; under it, one card per rule — which property, how many values, of what type, what message to show when it is broken. Classes and properties are picked from the live schema.

An edit here rewrites exactly the shape you changed and copies the rest of the file through untouched, so using the form on an imported official file does not reformat it. The one thing that cannot survive is a comment written *inside* the shape you edited, and you are told when that happens.

Some shapes are shown **read-only** with a "Turtle only" marker. Those use something the form does not model — an embedded SPARQL query, or a path expression rather than a plain property. They are displayed rather than hidden, but only the Turtle view will edit them, because writing them back from a form would drop the part it cannot represent. Official ENTSO-E constraints files are largely of this kind; the form is at its best on constraints you write yourself.

### Schema check

Answers the question only RDFArchitect can answer: **do this schema's constraints still agree with the schema they describe?**

It generates the shapes your schema implies and compares them, property by property, with what the graph's constraints state. The comparison reads **every enabled document together**, not just the open one — an official release splits its rules across several files, and the CGMES 3.0 DiagramLayout constraints are a good example: one file defers most of its property shapes to the shared IdentifiedObject file, and two more carry a single cross-profile rule each.

What it finds is grouped:

| | |
|---|---|
| **Contradiction** | The two cannot both be satisfied — different datatypes, or the schema requires more values than the documents allow. Someone has to decide which is right. |
| **Difference** | Both can be satisfied, but they do not say the same thing. Usually the profile deliberately narrowing what the schema allows. |
| **Not covered** | The schema implies a constraint no document states. |
| **Not in the schema** | A document constrains a property the schema does not have on that class. |

Coverage and agreement are counted separately, and the headline only turns red for a contradiction. A file that says nothing about a property does not disagree with the schema about it: the report says how many of the constraints *both* sides state agree, and reports the rest as a gap. Each finding also names the document that states it, so a report over several files still points at the one to open.

Shapes are matched by class and property, never by name, because generated and official shapes share no naming convention and both spread one property's rules over several shapes.

### What validation checks

Shapes are checked against the live CIM schema of the whole workspace: do the classes and properties they mention exist, do their cardinalities agree with the schema's multiplicities, does the SPARQL embedded in them parse and refer to real terms, and do the documents contradict each other.

The check spans *every* schema in the workspace, not only the one the document belongs to. Official ENTSO-E cross-profile constraints files reference terms from neighbouring profiles on purpose, and checking against a single profile would report all of those as unknown.

A document with problems still saves. Validation is a report, not a gate — you could not otherwise use the editor to finish a half-written file. Turtle that does not *parse* is the exception: there is nothing to store, and the message tells you the line and column where the parser stopped.

## Viewing SHACL at class level

In the class editor, every attribute and association row has a SHACL icon. Clicking it opens the **property-specific constraints (SHACL) dialog** — the subset of both generated and custom shapes that target that exact property on that exact class. This is by far the fastest way to answer *"what constraint is enforced on this attribute?"* without leaving the class you are looking at.

A similar dialog at class level shows the class rules, property rules and inherited property rules that target the selected class, alongside the classes that reference it.

**Both dialogs read; the workbench writes.** They show constraints merged from every enabled document, and merged shapes cannot be written back — there is no way to tell which document a rule came from once they are combined, and the endpoints that used to try wrote every edit into the graph's default document instead. **Edit in workbench** takes you to the document the rule really lives in.

## Importing custom SHACL

**File → Import → Constraints (SHACL)** uploads a SHACL file into the currently selected graph, as a new document named after the file. The workbench's import button does the same thing without leaving it. Importing a file whose name is already taken adds a `(2)` rather than replacing anything. Supported formats are the same as for schema import (TTL, RDF/XML, N-Triples); TTL is the default and recommended, and is the only one that preserves the file's text exactly.

## Exporting SHACL

**File → Export → Constraints (SHACL)** downloads the constraints as one file. The dialog asks which workspace and schema, then **which parts to include**: the generated shapes, and any of the graph's constraints documents. A document that is switched off can still be ticked — off means "takes no part in validation", not "cannot be exported". TTL is the default format.
