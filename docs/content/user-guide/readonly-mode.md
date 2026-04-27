---
title: Read-Only Mode
sidebar_position: 14
---

# Read-Only Mode

A schema (or an entire dataset) can be marked **read-only**. Read-only is the right state for any model that is "blessed" or shared with reviewers who shouldn't be able to mutate it accidentally.

## Where read-only is set

| Scope | Effect |
| ----- | ------ |
| **Snapshot** | Always read-only. Cannot be made writable. |
| **Schema** | All editing of that schema is locked. |
| **Dataset** | Every schema in the dataset is locked, including any new schemas you create. |
| **Application-wide** | Set by an administrator; locks every schema across every dataset. |

A clear read-only badge is shown whenever you are looking at read-only content.

## What changes when read-only is on

| Feature | Read-only schema |
| ------- | ---------------- |
| Browsing the model | ✅ Works |
| Diagram navigation | ✅ Works |
| Class / package / SHACL inspection | ✅ Works |
| Comparison | ✅ Works |
| Export | ✅ Works |
| Snapshot creation | ✅ Works |
| Importing into the schema | ❌ Disabled |
| Creating / editing / deleting classes, packages, attributes, etc. | ❌ Disabled |
| Undo / redo / restore | ❌ Disabled (history view stays browsable) |
| Migration wizard runs | ❌ Disabled when the *target* is read-only |

Editing affordances aren't merely greyed out — they're hidden, so the UI looks like a clean reading surface rather than a disabled editor.

## Toggling

Toggling read-only is itself a recorded action. Flipping a schema back to writable returns every editing affordance immediately.

## Why use it?

- **Reviewer-friendly handover.** Send a read-only schema (or its snapshot) to colleagues without worrying about accidental edits.
- **Stable references.** Lock a schema that other schemas compare against.
- **Demo and training.** A read-only dataset on a shared host gives newcomers something to explore without the risk of breaking things.

## Combining read-only with snapshots

Snapshots are always read-only. Marking the source schema read-only is a different decision — useful when you want to lock the working copy too, after declaring a milestone reached.
