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

- **Documents** (left) lists the graph's constraints documents: add a new one, import a file, rename, reorder, delete, switch one off, and **download** one — which saves that document's text exactly as it is stored. A new document is not empty: it starts with the prefixes a constraints file needs (`sh:`, `rdf:`, `rdfs:`, `xsd:`, the schema's CIM namespace) and a namespace for its own shapes, so both the form and completion can write short names from the first line. Everything a document can have done to it — including being renamed, switched off, emptied or deleted — rewinds with the schema's history. Each row carries a badge summarising what validation found in it, so a file with problems is visible without opening it. The first row is not a document but the **generated rules** — what RDFArchitect derives from the schema itself, shown read-only so you can read it beside whatever you imported.
- **Editor** (middle) shows the open document in one of three views — see below.
- **Inspector** (right) shows what the document is, the shapes it declares — click one to jump to it — and which profiles the constraints are being checked against.
- **Problems** (bottom) collects everything found across *all* of the documents. Clicking an entry opens the document it belongs to and puts the cursor on it.

In a **read-only** workspace the workbench reads and validates as usual, but nothing can be changed and the header says so.

Unsaved changes are not thrown away quietly. Opening another document, following a finding to the file it came from, or leaving the workbench altogether — including the editor's own Ctrl+click, the browser's back button, and closing the tab — asks first, and offers to save.

### Turtle view

The document as text, with syntax highlighting that extends into the SPARQL inside `sh:select`, and squiggles under whatever validation objects to. `Ctrl+S` saves; `F8` walks from one problem to the next; hovering a marker shows the message.

The text you wrote is what is stored, byte for byte — comments and ordering included. That matters for the official ENTSO-E files, which carry both and which you will want back unchanged.

Typing gets you more than highlighting:

- **Completion** on `:` offers the classes, properties and enumeration members that the workspace's schema actually declares, written the way the open document writes them. You never have to type an IRI.
- **Hover** over a term shows its label, its `rdfs:comment`, and for a property its domain, range and multiplicity, together with the profiles that declare it.
- **Completion** also covers the vocabularies a constraints file is *written in* — `sh:`, `rdf:`, `rdfs:`, `owl:` and the XSD datatypes — with a one-line explanation of each on hover. These are in no CGMES profile, so the schema knows nothing about them.
- **Ctrl+click** a class or property and you land on it in the class editor, with the class highlighted on its package diagram. For a property that means the class it belongs to, since a property is edited as a row of its class. `F12` and the context menu do the same thing.

### Form view

The same document, shown as shapes rather than as text, for people who would rather not read Turtle. Shapes are listed in the order the document writes them. Each one opens into a card that says what it applies to — one or more classes, or the subjects or objects of a property, or named nodes — along with its own name, description, message, severity and whether it is closed. Under it, one card per rule, grouped by the question it answers:

- **How many** — minimum and maximum values.
- **What kind of value** — value type (datatype), value class, value form (`sh:nodeKind`).
- **Between which values** — `sh:minInclusive`, `sh:maxExclusive` and the rest of the ranges.
- **What the text must look like** — shortest and longest length, a pattern (`sh:pattern`) with its match flags.
- **What it is called and reports** — name, description, order, group, the message shown when the rule is broken, and its severity. This group and the ranges start shut, because most rules say nothing there.

**One of** (`sh:in`) is a list you add values to and remove them from; **Must be exactly** is `sh:hasValue`. Classes and properties are picked from the live schema. A range keeps the way the document writes it: `"0.0"^^xsd:float` stays a quoted float when you change the digits, rather than becoming a bare decimal. Only a range the document did not have yet is written as a plain number.

An edit changes exactly the clause you changed — one value replaced, one line added or removed — and copies the rest of the file through untouched, comments inside the shape included. Using the form on an imported official file therefore does not reformat it.

**What the form does not write, it keeps.** A clause the form has no field for — an embedded SPARQL query, `sh:qualifiedValueShape`, a property of your own — no longer makes the shape read-only. The card lists it under *Kept as written*, and it is still there after your edit. A field whose value the form cannot spell again — a message with a language tag (`"…"@en`), a `sh:closed` that is neither `true` nor `false` — shows that value with a lock and the reason, instead of an empty box that would claim the document says nothing.

**Locking is per rule, not per shape.** A rule the form cannot tell apart from another, because both are written exactly alike, locks itself and leaves the rest of the shape editable. The only thing that still locks a whole shape is a subject written as more than one statement: an edit could not know which of them to change. A locked shape or rule is marked **Turtle only**, with the reason.

**Shared rules.** Official `-Con-Simple-` profiles write their rules as shapes of their own and let many node shapes point at them. Those rules are listed once under **Shared rules**, can be edited on their own card, and each says which shapes use it. A shape can be given a reference to a rule the document already has, and have one taken away without the rule going with it.

Changing a shared rule from under one shape asks first, because it is not a change to that shape — it is a change to every shape that uses it. The dialog offers two things:

- **Give this shape its own copy** (the default). The rule is copied under a new name you can correct, the copy starts out saying exactly what the original says — comments included — and only this shape's reference is moved to it before the change is applied.
- **Change it for all *n* shapes**, as a deliberate second choice.

Closing the dialog without choosing puts the field back to what the document says.

**Finding your way in a big file.** The filter above the list matches a shape's name and IRI, its target classes, and every rule's property, name and message, whether you type `cim:ACLineSegment` or paste the full IRI. **Locked only** narrows the list to what the form will not write. Any number of cards can be open at once, and the filter, the toggle and which cards are open survive switching to the Turtle view and back.

**Between the two views.** Each shape and each rule has a **show in Turtle** button that opens the Turtle view on the line it is written on. The way back is **Show in the Form view** in the Turtle editor's context menu, which opens the card holding the line under the cursor. Clicking a finding in the Problems panel while the form is showing does the same.

A document that does not parse cannot be shown as a form; the view says where the parser stopped and sends you to the Turtle view to fix it.

### Schema check

Answers the question only RDFArchitect can answer: **do this schema's constraints still agree with the schema they describe?**

It generates the shapes your schema implies and compares them, property by property, with what the graph's constraints state. The schema side is the **whole workspace**, scoped to the graph: profiles build on each other — an NC profile adds properties to classes the Equipment profile declares — so the documents are compared with everything the workspace says about the classes they target, while the graph is only asked to cover the classes and properties it declares itself. The comparison reads **every enabled document together**, not just the open one — an official release splits its rules across several files, and the CGMES 3.0 DiagramLayout constraints are a good example: one file defers most of its property shapes to the shared IdentifiedObject file, and two more carry a single cross-profile rule each.

What it finds is grouped:

| | |
|---|---|
| **Contradiction** | The two cannot both be satisfied — different datatypes, unrelated value classes, or the schema requires more values than the documents allow. A document that permits an enumeration value (`sh:in`) the schema does not list is reported here too, because that is how an extended enumeration drifts from its schema. Someone has to decide which is right. |
| **Difference** | Both can be satisfied, but they do not say the same thing. Usually the profile deliberately narrowing what the schema allows. |
| **Not covered** | The schema implies a constraint no document states. |
| **Not in the schema** | A document constrains a property the schema does not have on that class — or a class or property no schema in the workspace declares, and the finding says which. |

Coverage and agreement are counted separately, and the headline only turns red for a contradiction. A file that says nothing about a property does not disagree with the schema about it: the report says how many of the constraints *both* sides state agree, and reports the rest as a gap. Each finding also names the document that states it, and the name is a link that opens it, so a report over several files still points at the one to open.

Shapes are matched by class and property, never by name, because generated and official shapes share no naming convention and both spread one property's rules over several shapes.

What is compared, per property: `sh:minCount`, `sh:maxCount`, `sh:datatype`, `sh:nodeKind`, the value class, and the permitted values of `sh:in`. A value class is compared by the instances it admits: RDFArchitect and the CGMES files state it as the list of types a value may have (`sh:path (p rdf:type) ; sh:in (…)`), NC files as `sh:class`, and the two agree when they admit the same concrete classes. A shape switched off with `sh:deactivated true` states nothing, and a constraint stated only at `sh:Warning` or `sh:Info` is at most a difference, never a contradiction — data breaking it still conforms.

Not compared: value ranges (`sh:minInclusive` and friends), patterns, `sh:hasValue`, embedded SPARQL, logical combinations (`sh:or`, `sh:not`, …), and any path expression other than the value-type list above, such as the inverse cardinality RDFArchitect generates.

### What validation checks

Shapes are checked against the live CIM schema of the whole workspace: do the classes and properties they mention exist, do their cardinalities agree with the schema's multiplicities, does the SPARQL embedded in them parse and refer to real terms, and do the documents contradict each other.

The check spans *every* schema in the workspace, not only the one the document belongs to. Official ENTSO-E cross-profile constraints files reference terms from neighbouring profiles on purpose, and checking against a single profile would report all of those as unknown.

A term in a namespace that *no* profile in the workspace uses cannot be checked at all, so it is reported as information rather than as an error. Official NC files rely on this: they name each class under every CIM namespace in use (`cim16`, `CIM100` and the current one) so that one file validates data of any of those versions. A misspelt term is still an error, because a misspelling stays in a namespace the workspace knows.

A document with problems still saves. Validation is a report, not a gate — you could not otherwise use the editor to finish a half-written file. Turtle that does not *parse* is the exception: there is nothing to store, and the message tells you the line and column where the parser stopped.

## Viewing SHACL at class level

In the class editor, every attribute and association row has a SHACL icon. Clicking it opens the **property-specific constraints (SHACL) dialog** — the subset of both generated and custom shapes that target that exact property on that exact class. This is by far the fastest way to answer *"what constraint is enforced on this attribute?"* without leaving the class you are looking at. Each custom rule names the document it is written in, and clicking the name opens that document in the workbench at the rule.

A similar dialog at class level answers the same question for a whole class, and is worth knowing properly:

- **One row per property**, showing what the rule requires in words — `0..1, xsd:float` — so you do not have to read Turtle to learn a cardinality. Expanding a row shows the shapes it is made of, including rules written inline in a node shape (`sh:property [ … ]`, which is how the form writes a new rule). Class-level rules such as `sh:closed` are listed apart, under *On the class*.
- **Generated and custom rules are merged**, because "what is enforced on this property?" is the question, and which half a rule came from is an answer to a different one. The **Generated / Custom** buttons narrow it when you do want one half. Where the two disagree, both readings are shown side by side.
- **Every row names its sources** — `generated`, or the constraints document. Clicking a document opens it in the workbench with the cursor on the rule.
- **A filter** matches property names and rules alike, so `xsd:float` finds every float-valued property.
- **Referenced by** lists the classes that point at this one, in the class's own schema. Relations that reference nothing are left out.

**Both dialogs read; the workbench writes.** They show constraints merged from every enabled document, and a merged shape cannot be written back as it is shown — the endpoints that used to try wrote every edit into the graph's default document, whichever document the rule came from. The way to change a rule is the document name next to it, which opens the workbench on that document at that rule. **Edit in workbench** in the dialog's header opens the first document the class's (or property's) custom rules name, or the workbench on its own when every rule is generated. Either way the workbench opens on the class's schema, even when that is not the one selected in the navigation.

## Importing custom SHACL

**File → Import → Constraints (SHACL)** (`Ctrl+Shift+I`) uploads a SHACL file into a schema of your choice — the selected one to begin with — as a new document named after the file. The workbench's import button does the same thing without leaving it. Importing a file whose name is already taken adds a `(2)` rather than replacing anything. Supported formats are TTL, RDF/XML and N-Triples; TTL is recommended, and is the only one that preserves the file's text exactly.

A **read-only** workspace cannot take an import: the menu entry and its shortcut are disabled while one is selected, the dialog does not offer read-only workspaces, and an import aimed at one is refused.

## Exporting SHACL

**File → Export → Constraints (SHACL)** (`Ctrl+Shift+E`) downloads the constraints as one file. The dialog asks which workspace and schema, then **which parts to include**: the generated shapes, and any of the graph's constraints documents. TTL is the default format.

What is ticked to begin with is what validation uses: the documents that are switched on. A document that is switched off is labelled so and can still be ticked — off means "takes no part in validation", not "cannot be exported". The generated shapes are only ticked when the schema has no constraints documents; next to an official constraints file they restate most of it in other words, so ticking both repeats most rules in the file.

Exporting **one document on its own, as TTL**, gives back its text exactly as it is stored — comments and ordering included — the same file the workbench's **download** saves. Any other combination is merged and written out afresh, which keeps what the shapes say but not how the files spelled it.
