---
title: The Editor and Importing Data
sidebar_position: 2
---

# The Editor and Importing Data

## The editor

The main editor, reachable from the **Open Editor** button on the homepage or via the top navigation, is split into three panels:

- **Left — navigation tree.** The graphs inside the active workspace, and the packages inside each graph. Classes live under their packages and can be opened from here.
- **Centre — diagram canvas.** A UML-style class diagram of the currently selected package: classes with their attributes and associations, inheritance arrows, and links that leave the package. The diagram is rendered with SvelteFlow and supports pan, zoom, and auto-layout.
- **Right — class editor.** When a class is selected, its full definition is available here: label, URI namespace, package, super class, stereotypes, comment, attributes, associations, enum entries, and class-level constraints (SHACL).

Across the top there is a **menu bar** (File, Edit, View, Help), a **workspace tab bar** for switching between open workspaces, a **search bar** for finding classes/attributes/packages, and indicators for read-only status and unsaved changes.

In a narrow window the toolbar makes room for the menu bar:

- Below a window width of 1024 pixels, **Enable Editing** shows only a lock icon. Its tooltip reads *The workspace is read-only. Enable editing*.
- Below 768 pixels, the search bar folds into a magnifier button, see [Search](./search-and-tips#search).
- Below 640 pixels, the logo shows only **RDFA**.

A colour legend and classic diagram conventions are used throughout: stereotypes like `«enumeration»` or `«Primitive»` appear above the class name, cardinalities are shown on association ends, and inheritance uses a hollow triangle.

![Editor overview](/img/screenshots/editor.png)

## Arranging the diagram

The layout of a diagram is stored together with the schema: where the classes are, the course of every edge, and where the labels of the edges sit. Every change is saved right away, so the diagram looks the same after a reload. In a read-only workspace the diagram can be viewed but not rearranged.

- **Automatic layout.** A diagram that has never been arranged is laid out automatically when it is first opened. **Reset Layout** in the toolbar above the diagram lays it out anew and replaces all class positions, edge courses, and label positions.
- **Classes.** Drag a class to move it; selected classes move together, and the ends of their edges move along. Where classes overlap, the **Move** submenu in the context menu of a class decides which one lies on top: it moves the class up or down one step or all the way to the front or bottom, and its **Layer** slider sets the z position of the class directly.
- **Selecting edges.** Click an edge to select it. A selected edge is drawn above the classes, so it can be grabbed where a class covers it, and shows its edge points.
- **Edge points.** The course of an edge is set by its edge points. Bend points, shown in blue, are the corners of the edge between its two ends. Docking points, shown in green, pin an end of the edge to a place on the class border. An end without a docking point docks on its own, where the line towards the class crosses its border.
- **Bend points.** A selected edge shows an inactive bend point, a faint blue circle, in the middle of each segment. Drag it to create a bend point there, and drag a bend point to move it. **Ctrl+Q** adds a bend point to the edge closest to the cursor, as does **Add bend point here** in the context menu of the edge. A bend point that lies on a straight line between its neighbours is removed when the edge is saved.
- **Docking points.** A selected edge shows an inactive docking point, a faint green circle, where an end without a docking point meets its class. Drag it to pin the end of the edge to a place on the border. A docking point slides along the border while it is dragged and keeps its place on the class when the class is moved or changes its size.
- **Removing edge points.** **Delete edge point** in the context menu of a point deletes a single bend or docking point. **Ctrl+Shift+Q** is the counterpart to **Ctrl+Q**: it deletes the point under the cursor, and while the keys are held it works like an eraser brush that deletes every point the cursor passes over. The context menu of an edge removes several points at once: **Remove all edge points** draws the edge straight between its classes, **Remove bend points** keeps the docking points, and **Remove docking points** lets both ends dock on their own again.
- **Self-associations.** An association from a class to itself is drawn as a loop above the class, from a docking point at its top border over two bend points back to another docking point. Its points can be moved and added like those of any other edge, but it keeps at least one bend point and one docking point, so the loop never collapses onto the class. Instead of the entries that remove several points at once, its context menu offers **Reset to default shape**. Selecting the class lifts its loops above it, so a loop dragged over the class can be grabbed again.
- **Labels.** Multiplicities and role names can be dragged to another place near their end of the edge.
- **Rounded corners.** In **File → Settings**, **Smoothed edges** rounds off the corners of edges at their bend points, and **Corner rounding (%)** sets how strongly. The edges do not become curves: the segments between the bend points stay straight, only the corners where they meet are rounded.

Edges hidden by a filter keep their course and are shown with it again once the filter is lifted. After **Reset Layout**, they are drawn straight.

## Getting data into RDFArchitect

There are four ways content lands in the editor.

### Import a schema (File → Import → Schema (RDFS))

This is the standard entry point for CGMES profiles and ENTSO-E Network Code Profiles. Pick a workspace (existing or a new name), drop in an `.rdf`, `.ttl`, or `.nt` file, and the contents become a graph inside that workspace. The schema is named after the file, without its extension and otherwise unchanged (`my-profile.ttl` becomes `my-profile`), and the name can be overridden. A schema name may hold any character a file name may hold on Windows and Linux, except `;`, and may be up to 200 characters long — the dialog says so when it does not. Leading and trailing spaces are dropped. If the workspace already has a schema of that name, the import appends a number (`my-profile-2`). If the target workspace has been set to read-only, the import is blocked until editing is re-enabled.

Supported input formats are **RDF/XML (.rdf)**, **Turtle (.ttl)**, and **N-Triples (.nt)**. CGMES releases are typically shipped as RDF/XML and can be imported directly. You can select several files at once, or a `.zip` archive, in which case every schema at the root of the archive is imported as its own graph.

The dialog stays open while the import runs and shows a progress bar together with one row per file, so you can see which file is being read, checked and stored. When it is done, the same list reports what was imported, which files could not be imported, and which properties were stored but will not be displayed (properties without the CIM stereotype or association metadata). Cancelling stops the import after the file it is currently working on and keeps the schemas that were already imported. Closing the dialog opens the first imported schema.

![Import schema dialog](/img/screenshots/import-schema.png)

### Import a model (File → Import → Model (Enterprise Architect XMI)) {#xmi-import}

The other entry point for CGMES: the Enterprise Architect export the profiles are maintained in,
rather than the RDFS released from it. One export becomes **one workspace of its own**, so a name a
workspace already has is refused. Upload the entire model including any possible metadata to
prevent missing references; which packages are actually imported is chosen afterwards.
Supported are Enterprise Architect **XMI 2.5.1** exports (Publish → Publish As… → XMI 2.5.1) and
**XMI 2.1** exports; a 2.1 export carries no diagrams.
Multiple inheritance is not fully supported: a class with several superclasses may lose all but one
of them.

Every package can become a schema, and a schema is the package you pick plus everything its classes
need, which is what the two numbers per row mean: `26 classes + 7 dependencies`. A package marked
`profile` declares a namespace of its own and is taken whole, the way a release ships it. The
export's class diagrams sit in the tree under their package; one drawing a class you did not pick
is greyed out. Each schema is named after its package, following the same rules as a schema
imported from a file.

Two namespaces decide what the resources are called, **Model** and **Extension**, and both can be
changed in the last step: an export carries the namespaces of the generation it was made in, which
are not always the ones a schema is to be written in. The rest of the prefix table comes along
unchanged.

Where something could not be carried over as the export states it, the import reports it as
`n warnings`: attributes typed with something the export does not declare, classes declared more
than once, and header fields no ontology field matches. Such an attribute keeps its type as a
dangling reference, so importing the package that declares the type completes it.

### Import a SHACL file (File → Import → Constraints (SHACL))

A separate import path for custom SHACL shapes. These are stored *next to* the schema graph and can later be viewed from the same UI as the generated SHACL (see [SHACL](./shacl)).

### Import a workspace file (File → Import → Workspace (.rdfa))

**File → Export → Workspace (.rdfa)** saves the whole workspace as one `.rdfa` file: every schema with its custom SHACL, diagrams and layouts, plus the workspace's namespaces, diagrams, cross-profile colours and read-only state. The undo history is not included. Importing the file creates a **new** workspace; its name defaults to the one the workspace was exported under. Nothing is imported unless the whole file is valid; otherwise the dialog lists every problem, with the entry inside the file it concerns.

The workspace file is a zip; rename it to `.zip` to look inside. Its top level holds the workspace, each schema sits in `schemas/<schema name>/schema.ttl` and can be used on its own, and `manifest.json` lists the schemas. The manifest's `formatVersion` (`MAJOR.MINOR`) says which RDFArchitect versions read it: any file of the same major version imports, and one with a newer minor version imports with a notice that the parts this version does not know were left out.

### Create an empty schema (Edit → New → Schema)

Starts a new, empty graph in the selected workspace. Useful when you want to build a small extension profile from scratch rather than import one.

Once a schema is imported, it becomes the active graph and the navigation tree on the left updates to show its packages.
