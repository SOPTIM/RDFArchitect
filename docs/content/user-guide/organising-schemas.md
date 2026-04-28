---
title: Organising a Schema
sidebar_position: 3
---

# Organising a Schema: Datasets, Graphs, Packages

## Creating a package

With a graph selected, choose **Edit → New → Package**. You provide a label, a URI namespace, and (optionally) a parent package for nesting. Packages can be edited (**Edit → Edit → Package**) and deleted (**Edit → Delete → Package**) from the same menu.

The `default` package is reserved — it represents classes that have not been assigned to any explicit package. It cannot be renamed or deleted.

![Add package](/img/screenshots/add-package.png)

## External vs. internal packages

A graph usually contains two categories of packages:

- **Internal packages** — defined in the schema itself; fully editable.
- **External packages** — referenced from another imported schema (for example, the `Core` package of CGMES when you are editing an extension profile). They are visible for navigation and class assignment but not editable.

The navigation tree marks these two categories separately, and the editing menus disable editing actions for external packages automatically.

## Creating a class

From **Edit → New → Class** you pick the target dataset, graph, package, and a URI namespace for the new class. The label must be unique within the graph; the editor validates this before allowing save. On save, the class opens immediately in the class editor on the right.

![Add class](/img/screenshots/add-class.png)

## Deleting

Every destructive action (delete schema, delete dataset, delete package, delete class) goes through a confirmation dialog that states what will be removed. Deletions participate in undo/redo like any other edit (see [Reviewing changes](./history)).
