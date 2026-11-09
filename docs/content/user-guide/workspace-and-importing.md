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

### Import a SHACL file (File → Import → Constraints (SHACL))

A separate import path for custom SHACL shapes. These are stored *next to* the schema graph and can later be viewed from the same UI as the generated SHACL (see [SHACL](./shacl)).

### Import a workspace file (File → Import → Workspace (.rdfa))

**File → Export → Workspace (.rdfa)** saves the whole workspace as one `.rdfa` file: every schema with its custom SHACL, diagrams and layouts, plus the workspace's namespaces, diagrams, cross-profile colours and read-only state. The undo history is not included. Importing the file creates a **new** workspace; its name defaults to the one the workspace was exported under. Nothing is imported unless the whole file is valid; otherwise the dialog lists every problem, with the entry inside the file it concerns.

The workspace file is a zip; rename it to `.zip` to look inside. Its top level holds the workspace, each schema sits in `graphs/<folder>/schema.ttl` and can be used on its own, and `manifest.json` names the graph each folder holds. The manifest's `formatVersion` (`MAJOR.MINOR`) says which RDFArchitect versions read it: any file of the same major version imports, and one with a newer minor version imports with a notice that the parts this version does not know were left out.

### Create an empty schema (Edit → New → Schema)

Starts a new, empty graph in the selected workspace. Useful when you want to build a small extension profile from scratch rather than import one.

Once a schema is imported, it becomes the active graph and the navigation tree on the left updates to show its packages.
