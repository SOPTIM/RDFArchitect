---
title: History and Undo
sidebar_position: 9
---

# History and Undo

Every write operation on a schema is recorded. You can review history, undo recent steps, or roll an entire schema back to an earlier point in time.

![Change history](/img/screenshots/changelog.png)

## Undo and redo

Undo and redo cover the most recent operations on the active schema. Operations are atomic: a single user action (e.g. "save class") is one undo step, even if it touches many triples internally.

The undo stack is per-schema. Switching schemas does not clear it.

## The history view

The history page lists every change to the active schema in reverse chronological order. Each entry shows when it happened, what kind of action it was, what it targeted, and an expandable triple-level diff.

Filters at the top of the list narrow by type of action, by target, or by time range.

## Inspecting a change

Expanding an entry reveals the exact triples added and removed:

```turtle
# Removed
ex:Breaker a rdfs:Class ; rdfs:label "Breaker" .

# Added
ex:Breaker a rdfs:Class ; rdfs:label "Circuit breaker" .
```

This is the same inline-diff format used in the comparison view ([Comparing schemas](./comparing-schemas)).

## Restoring an earlier state

Each history entry has a restore action. Choosing it asks you to confirm and then reverts the entire schema to the state immediately *before* that entry. Three things to know:

1. Restoring is itself recorded in the history. You never lose changes.
2. Restoring after a long sequence of changes can be a much larger operation than a single undo — review the diff before confirming.
3. If the schema has snapshots, restoring does not affect them.

## Author tracking

If your installation is configured to identify users (see [Access control](/admin-guide/access-control)), history entries are tagged with that identity. Otherwise entries are anonymous.

## What is and isn't tracked

| Tracked | Not tracked |
| ------- | ----------- |
| Class / package / attribute / association / enum-entry CRUD. | Diagram zoom and pan state. |
| Custom SHACL imports. | Filter view selections (session-local). |
| Renames, including ripple updates to associations. | Read-only mode toggles. |
| Layout changes (when persisted). | Snapshot creation (recorded separately). |
| Schema-level imports (replace and merge). | |
| Restore-to operations. | |
