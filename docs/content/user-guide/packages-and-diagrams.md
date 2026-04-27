---
title: Packages and Diagrams
sidebar_position: 7
---

# Packages and Diagrams

Packages structure the model; diagrams visualise one package at a time.

## The package navigation tree

The navigation tree shows every package in the active schema, alphabetically. Each package node expands to reveal its classes.

![Add package](/img/screenshots/add-package.png)

From the tree you can:

- Click a package to switch the diagram view to it.
- Click a class to open it in the class editor.
- Use context actions to create / rename / delete a package or add a class.
- Use the search bar to jump straight to a class without expanding the tree.

## Creating and editing packages

When creating a package you provide a name (used as the IRI fragment and label), an optional parent package, and an optional comment.

Renaming a package updates every class's package reference accordingly. Deleting a package asks you to choose what happens to its classes:

- **Move them to another package** — recommended, non-destructive.
- **Delete them too** — only when you really mean it.

## The diagram view

The center pane is a UML-style diagram of the active package. Class boxes show:

- The class name and stereotype.
- A divider, then the attributes (name, datatype, multiplicity).
- Associations rendered as labelled edges to other class boxes — possibly to classes in other packages, in which case those boxes are shown faded.

![Add class](/img/screenshots/add-class.png)

You can pan, zoom, and drag class boxes around. Manual positions are persisted so re-opening the package later restores your layout.

## Filtering and view options

The diagram view has filters that hide elements you don't currently need to see (attributes, external associations, enumerations, abstract classes). Filters are local to the current browser session — they don't change the underlying model.

## Diagram layout

Layout is computed automatically the first time you open a package. After that, your manual adjustments are persisted with the schema, so:

- Re-opening the package later restores your layout.
- Exporting the schema (with layout) preserves it.
- Re-importing an exported schema brings the layout back.

If a layout becomes a mess, resetting it re-runs the auto-layout from scratch. The reset is recorded in the history and can be undone.

## Adding classes from the diagram

The diagram offers actions for creating classes and drawing associations directly inside the canvas. Pre-fills (target package, namespace) come from the diagram context.

## Cross-package context

Associations to classes in *other* packages are shown as connectors leaving the canvas, terminating at a faded representation of the foreign class. Following the ghost takes you to the package that actually owns it.

## Performance notes

Very large packages render slower. Two strategies help:

- Split a large package into sub-packages.
- Use the filter view to hide details you don't currently need.
