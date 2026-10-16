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
- **Removing edge points.** **Delete edge point** in the context menu of a point deletes a single bend or docking point. **Ctrl+Shift+Q** is the counterpart to **Ctrl+Q**: it deletes the point under the cursor, and while the keys are held it works like an eraser brush that deletes every point the cursor passes over. The context menu of an edge removes several points at once: **Remove all bend points** keeps the docking points, **Remove docking points** lets both ends dock on their own again, and **Remove all edge points** draws the edge straight between its classes.
- **Labels.** Multiplicities and role names can be dragged to another place near their end of the edge.
- **Rounded corners.** In **File → Settings**, **Smoothed edges** rounds off the corners of edges at their bend points, and **Corner rounding (%)** sets how strongly. The edges do not become curves: the segments between the bend points stay straight, only the corners where they meet are rounded.

Edges hidden by a filter keep their course and are shown with it again once the filter is lifted. After **Reset Layout**, they are drawn straight.

## Getting data into RDFArchitect

There are three ways content lands in the editor.

### Import a schema (File → Import → Schema (RDFS))

This is the standard entry point for CGMES profiles and ENTSO-E Network Code Profiles. Pick a workspace (existing or a new name), drop in an `.rdf`, `.ttl`, or `.nt` file, and the contents become a graph inside that workspace. The graph name defaults to a sanitised version of the filename but can be overridden. If the target workspace has been set to read-only, the import is blocked until editing is re-enabled.

Supported input formats are **RDF/XML (.rdf)**, **Turtle (.ttl)**, and **N-Triples (.nt)**. CGMES releases are typically shipped as RDF/XML and can be imported directly. You can select several files at once, or a `.zip` archive, in which case every schema at the root of the archive is imported as its own graph.

The dialog stays open while the import runs and shows a progress bar together with one row per file, so you can see which file is being read, checked and stored. When it is done, the same list reports what was imported, which files could not be imported, and which properties were stored but will not be displayed (properties without the CIM stereotype or association metadata). Cancelling stops the import after the file it is currently working on and keeps the schemas that were already imported. Closing the dialog opens the first imported schema.

![Import schema dialog](/img/screenshots/import-schema.png)

### Import a SHACL file (File → Import → Constraints (SHACL))

A separate import path for custom SHACL shapes. These are stored *next to* the schema graph and can later be viewed from the same UI as the generated SHACL (see [SHACL](./shacl)).

### Create an empty schema (Edit → New → Schema)

Starts a new, empty graph in the selected workspace. Useful when you want to build a small extension profile from scratch rather than import one.

Once a schema is imported, it becomes the active graph and the navigation tree on the left updates to show its packages.
