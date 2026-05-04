---
title: Overview
sidebar_position: 1
---

# User Guide

A practical walkthrough of RDFArchitect for people who work with **CIM/CGMES** and the **ENTSO-E Network Code Profiles**, but who do not want to write code.

RDFArchitect is a browser-based editor for **RDFS schemas with CIM extensions** and their **SHACL constraints**. It is designed around the daily work of the CIM community: importing a profile, inspecting its package and class structure, making controlled changes, validating against SHACL, comparing releases, planning a migration, and sharing the result.

This guide assumes you have the application running. If you don't, see [Installation](/admin-guide/installation).

## Core concepts in 2 minutes

Before the tour, three terms do a lot of work in RDFArchitect and it is worth fixing them up front.

**Dataset.** A dataset is the outermost container. Think of it as a workspace held in backend memory. A typical setup has one dataset called `default`, plus one dataset for each snapshot that has been loaded. Every import, every graph, every change is scoped to exactly one dataset.

**Graph (schema).** Inside a dataset, each **schema** lives in its own named graph. When you import a CGMES profile — say, `EquipmentProfile_v3.0.0.rdf` — that profile becomes *one graph* inside the dataset. You can have many graphs side-by-side in the same dataset (e.g. EQ, TP, SSH, SV profiles of a CGMES release), and you can move between them from the navigation tree on the left.

**Package.** Inside a graph, classes are grouped into UML-style packages (e.g. `Core`, `Wires`, `Generation::Production`). Packages are the first-class organisational unit for the diagram area: when you select a package, the centre canvas draws exactly the classes that belong to it, with associations to classes in other packages shown at the boundary.

The rest of the editor is built on top of those three. The navigation tree reads *"Dataset → Graph → Package → Class"*, and almost every action you trigger is implicitly scoped by what you have selected in that tree.

## How this guide is organised

1. **[The workspace and importing data](./workspace-and-importing)** — the editor layout and how to get content in.
2. **[Organising a schema](./organising-schemas)** — datasets, graphs, packages.
3. **[Editing classes](./editing-classes)** — the right-hand class editor.
4. **[Working with namespaces](./namespaces)**.
5. **[The profile header](./profile-header)** — ontology metadata.
6. **[SHACL — constraints and validation](./shacl)**.
7. **[Reviewing changes](./history)** — changelog, undo, restore.
8. **[Comparing schemas](./comparing-schemas)**.
9. **[Schema migration](./migration)** — the five-step wizard.
10. **[Sharing and exporting](./sharing-and-exporting)**.
11. **[Read-only mode](./read-only-mode)**.
12. **[Search & tips](./search-and-tips)**.
