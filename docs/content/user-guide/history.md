---
title: Reviewing Changes
sidebar_position: 8
---

# Reviewing Changes — Changelog, Undo, Restore

Every edit you make to a graph is tracked. Three features make this tracking visible and reversible.

![Changelog](/img/screenshots/changelog.png)

## Undo / Redo

Cross-cutting, session-level undo and redo for *every* edit — class, attribute, association, enum entry, package, namespace, ontology — is available from **Edit → Undo** / **Redo** or with **Ctrl+Z** / **Ctrl+Y**. There is no per-entity undo history; undo walks the whole graph's edit stream linearly.

The backend keeps up to 256 versions per graph by default (configurable) and compresses older states as you keep editing.

## Changelog view

**View → Changelog** opens a dedicated page that lists the change history of the currently selected graph: who changed what, what was added, updated, or deleted, and an inline diff for each change. The left pane groups changes by class, the right pane shows the full triple-level detail of the selected change with additions in green and deletions in red.

This is the view to use when reviewing what happened between two editing sessions, or when preparing a release note.

## Restore a previous version

From the changelog you can restore the graph to any earlier point. This creates a new entry at the top of the history (so it is itself undoable) and resets every class, package, and namespace to the state it had at that time. It does not touch the custom SHACL content unless the SHACL was also part of the restored history.
