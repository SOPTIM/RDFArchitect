---
title: Reviewing Changes
sidebar_position: 9
---

# Reviewing Changes — Changelog, Undo, Restore

Every edit you make in a workspace is tracked. Three features make this tracking visible and reversible.

![Changelog](/img/screenshots/changelog.png)

## Undo / Redo

Cross-cutting, session-level undo and redo is available from **Edit → Undo** / **Redo** or with **Ctrl+Z** / **Ctrl+Y**. The history belongs to the whole workspace, not to a single schema: one linear stream of changes across every schema in it, including the ones that span two schemas at once, such as copying a class from one into another.

What is tracked goes beyond class, attribute, association, enum entry, package, and ontology edits. Importing, deleting, and renaming a schema, the namespace table of the workspace, custom SHACL shapes, diagrams, and diagram layout are part of the same history.

Because undo reaches the whole workspace, a step can take back something you are not looking at. RDFArchitect says what it undid and in which schema, and asks first when the step would make a whole schema or diagram disappear.

The backend keeps up to 256 changes per workspace by default (configurable) and folds older states into the state they started from as you keep editing.

## Changelog view

**View → Changelog** opens a dedicated page that lists the change history of the currently selected workspace: what was added, updated, or deleted, with an inline diff for each change. The left pane groups changes by class, the right pane shows the full triple-level detail of the selected change with additions in green and deletions in red, labelled with the schema each part of the change belongs to.

This is the view to use when reviewing what happened between two editing sessions, or when preparing a release note.

## Restore a previous version

From the changelog you can restore the workspace to any earlier point, which takes back every change made after it, in all of its schemas rather than only the one you have open.
