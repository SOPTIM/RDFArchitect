---
title: The Editor and Importing Data
sidebar_position: 2
---

# The Editor and Importing Data

## The editor

The main editor, reachable from the **Open Editor** button on the homepage or via the top navigation, is split into three panels:

- **Left — navigation tree.** The graphs inside the active workspace, and the packages inside each graph. Classes live under their packages and can be opened from here.
- **Centre — diagram canvas.** A UML-style class diagram of the currently selected package: classes with their attributes and associations, inheritance arrows, and links that leave the package. The diagram is rendered with SvelteFlow by default and supports pan, zoom, and auto-layout; a Mermaid rendering is also available.
- **Right — class editor.** When a class is selected, its full definition is available here: label, URI namespace, package, super class, stereotypes, comment, attributes, associations, enum entries, and class-level constraints (SHACL).

Across the top there is a **menu bar** (File, Edit, View, Help), a **workspace tab bar** for switching between open workspaces, a **search bar** for finding classes/attributes/packages, and indicators for read-only status and unsaved changes.

A colour legend and classic diagram conventions are used throughout: stereotypes like `«enumeration»` or `«Primitive»` appear above the class name, cardinalities are shown on association ends, and inheritance uses a hollow triangle.

![Editor overview](/img/screenshots/editor.png)

## Getting data into RDFArchitect

There are four ways content lands in the editor.

### Import a schema (File → Import → Schema (RDFS))

This is the standard entry point for CGMES profiles and ENTSO-E Network Code Profiles. Pick a workspace (existing or a new name), drop in an `.rdf`, `.ttl`, or `.nt` file, and the contents become a graph inside that workspace. The graph name defaults to a sanitised version of the filename but can be overridden. If the target workspace has been set to read-only, the import is blocked until editing is re-enabled.

Supported input formats are **RDF/XML (.rdf)**, **Turtle (.ttl)**, and **N-Triples (.nt)**. CGMES releases are typically shipped as RDF/XML and can be imported directly. You can select several files at once, or a `.zip` archive, in which case every schema at the root of the archive is imported as its own graph.

The dialog stays open while the import runs and shows a progress bar together with one row per file, so you can see which file is being read, checked and stored. When it is done, the same list reports what was imported, which files could not be imported, and which properties were stored but will not be displayed (properties without the CIM stereotype or association metadata). Cancelling stops the import after the file it is currently working on and keeps the schemas that were already imported. Closing the dialog opens the first imported schema.

![Import schema dialog](/img/screenshots/import-schema.png)

### Import a model (File → Import → Model (Enterprise Architect XMI)) {#xmi-import}

The other entry point for CGMES: the Enterprise Architect export the profiles are maintained in,
rather than the RDFS released from it. One export becomes **one workspace of its own**, and you
choose what of it becomes a schema. A name a workspace already has is refused, the export not
being something to add to what is there.

Import the **whole model**, metadata included, wherever you have it. A schema takes the classes
its own depend on along with it, and one the export does not hold is reported rather than invented
— a partial export therefore costs references. Which parts of it become schemas is chosen
afterwards, so nothing is lost by reading all of it.

The export is **read first and imported second**. Reading stores nothing — it only reports what the
file holds, which for a full CGMES 3.0 model is 369 packages. Nothing exists in the workspace until
you have picked from them and confirmed.

Picking and confirming are two steps, and **Back** returns to the first with everything as you left
it — the same packages ticked, the same rows opened.

#### What you are picking

Every package of the export can become a schema, and a schema is **the package you pick plus
everything its classes need**: the datatypes their attributes are typed with and the classes they
inherit from, wherever those live. That is why each row shows two numbers — `26 classes +
7 dependencies` means the package declares 26 and another 7 are required to make them complete.
Both end up in the schema.

A package marked `profile` declares a namespace of its own, through a metadata class stating a
`versionIRI` or a `baseURI`. That has two consequences:

- it is **one schema, taken whole**, sub-packages included — the same thing a release ships as one
  RDFS file. You cannot pick a part of it, because a part of it is not a profile.
- its package resource and its ontology header are named in that namespace. A package without one
  takes the model namespace instead, which is what CGMES 2.4.15 does throughout.

Everything else is an ordinary package. One holding other packages is a **group**: ticking it ticks
what is inside, the way a folder does, and each of those becomes a schema of its own. Where such a
package also declares classes beside its sub-packages, it offers an extra row for exactly those —
without it they could not be picked at all.

The tree is titled after the one package every export wraps everything in — `Model` for a full
CGMES model — and starts at what that package holds, so the parts of the model are in view rather
than one folder to open first.

The tree says what you pick; the list beside it says what you get. Each schema is a row of its
own, and opening one shows what goes into it: the diagrams it takes, the classes it declares and
the classes its closure adds on top of them. Each of the three opens up in turn and names what it
holds, so a schema can be checked class by class before anything is written.

#### The namespaces

The second step shows what the import would create and under which names. Two namespaces decide
what the schemas call their resources, and both can be changed there:

- **Model** — nearly every resource of a schema: its packages, classes, attributes, associations
  and enum entries.
- **Extension** — only the elements stereotyped `Entsoe` or `European`. The field is shown only
  for an export that holds such an element at all, so a model carrying none is never asked about
  it. Whether the packages picked from it happen to hold one makes no difference: the field stays
  where it is rather than appearing and going again as the selection changes.

Both fields start empty and show what the export states as their placeholder — CGMES 3.0 states
both in its prefix table, the CGMES 2.4.15 full model states them as attributes. Left that way,
that is what is used. They offer the namespaces the releases are published in beside the export's
own, each named after its release. That the field has to be editable is not a nicety: an export carries the
namespaces of the generation it was made in, which are not always the ones a schema is to be
written in. The CGMES 3.0 model states `https://cim.ucaiug.io/ns#` and
`https://cim.ucaiug.io/ns/eu#` where its released RDFS uses `http://iec.ch/TC57/CIM100#` and
`http://iec.ch/TC57/CIM100-European#`. Which pair is meant is yours to decide.

A CGMES 2.4.15 **profiles-only** export states no extension namespace at all, although its release
uses one. Left empty there, the import is refused rather than writing those elements under a
guessed name.

The rest of the export's prefix table comes along unchanged — 20 of the 22 entries in a full CGMES
3.0 model, `rdf`, `rdfs`, `owl` and the like. None of them decides what a resource is called, so
they are listed rather than asked about and can be changed any time afterwards under
**Workspace → Namespaces**.

#### The diagrams

The export's class diagrams sit **in the tree**, under the package they belong to: a package says
how many it carries, and expanding it lists them. Each is marked `schema-diagram`, or
`workspace-diagram` where it draws across several schemas and no single one can hold it. The list
of schemas beside the tree names them too, each under the schema it joins. Everything available is ticked to begin with, so
a selection made without opening anything takes them all; the count beneath the schema list says how
many that is.

Two kinds of package have no row of their own to be shown under — one holding no class anywhere is
not offered as a schema, and a profile is offered whole rather than drilled into. A diagram declared
in either is shown under the nearest package that *is* offered, which for 267 of the CGMES 3.0
model's 926 diagrams is not the package declaring it.

Which ones are available follows from the packages: a diagram can be taken exactly when **every
class it draws** is part of the import. One reaching into a package you did not pick is shown greyed
out and cannot be ticked — half a diagram looks complete and is not. That answer follows every tick,
so widening the selection is what makes more of them available.

For a selection of profiles this costs nothing, since a profile's diagrams draw that profile's
classes: of the CGMES 3.0 model's 1311 diagrams, 926 draw classes at all and three profiles offer
30 of them, none left half-drawn. Picking model packages instead is where it bites — there 318
further diagrams would be a fifth full at best.

The arrangement comes over as the export has it, pulled apart by half. Both editors size a class
box to its contents and RDFArchitect's is the larger — at least 180 pixels wide against a median of
153 in the CGMES 3.0 model, with rows set half again as tall — so at the coordinates as drawn the
boxes would crowd and overlap. Every class moves by the same factor, which leaves the arrangement
itself untouched.

#### What comes along, and what does not

Where the import could not carry something over faithfully, it says so as a line reading
**`n warnings`**; opening it lists them in the same table the validation report uses, so they can
be grouped by the reason, by the schema or by the resource they are about. There are three kinds:
attributes whose type the export refers to but does not contain, classes it declares more than
once, and header fields stated under a name no ontology field matches. Nothing is guessed at, and
where there is nothing to report the line does not appear — a full CGMES 3.0 model imports without
one.

### Import a SHACL file (File → Import → Constraints (SHACL))

A separate import path for custom SHACL shapes. These are stored *next to* the schema graph and can later be viewed from the same UI as the generated SHACL (see [SHACL](./shacl)).

### Create an empty schema (Edit → New → Schema)

Starts a new, empty graph in the selected workspace. Useful when you want to build a small extension profile from scratch rather than import one.

Once a schema is imported, it becomes the active graph and the navigation tree on the left updates to show its packages.
