---
title: Getting Started
sidebar_position: 3
---

# Getting Started

## 1. Open the application

After accessing the application URL, you should see a welcome page with some introductory information.

To open the application, click on "Open Editor", which will take you to the main editor interface.

![Homepage](/img/screenshots/homepage.png)

## 2. Import a schema

Open the import dialog from the toolbar. Pick the file of the schema you want to load.

![Import dialog](/img/screenshots/import-schema.png)

When the import finishes, the imported schema should appear in the navigation tree on the left.

## 3. Browse the model

The editor combines schema navigation, a diagram of the active package, and contextual editing actions on a single page.

![Editor overview](/img/screenshots/editor.png)

- The package tree groups classes by package. Click a package to open its diagram; click a class to open it in the class editor.
- The diagram shows a UML-style view of the active package: class boxes with attributes and edges for associations.
- The contextual actions cover class editing, SHACL inspection, and history.

## 4. Make a change

Try one of the following:

- Open a class and edit its comment.
- Add a new attribute (give it a name, datatype, and multiplicity).
- Create a new schema and package, then add a fresh class to it.

Save the change. A new entry appears in the change history, and the diagram redraws automatically.

## 5. Generate or inspect SHACL

The SHACL views show the constraints RDFArchitect derived from your model. If your import contained custom SHACL, those are visible alongside the generated ones.

![SHACL inspection](/img/screenshots/shacl.png)

## 6. Share your work

Two complementary mechanisms:

- **Export** the schema as Turtle (or RDF/XML, N-Triples) — useful for handover to other tools.
- **Snapshot** the schema and share the link — useful for easy sharing with non-technical stakeholders.

With Snapshots, you can share a dataset via a URL. Upon following the link, it imports the dataset into their session.

## What next?

- Read [Editing classes](./editing-classes) for the editor reference.
- Read [SHACL](./shacl) for generated vs. custom shapes.
- Read [Migration wizard](./migration-wizard) when you need to move data between schema versions.
