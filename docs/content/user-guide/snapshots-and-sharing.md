---
title: Snapshots and Sharing
sidebar_position: 10
---

# Snapshots and Sharing

A **snapshot** is a frozen, read-only copy of a schema. Snapshots are how you hand a schema to a reviewer who shouldn't (or doesn't want to) edit it.

![Share snapshot](/img/screenshots/share-snapshot.png)

## Creating a snapshot

Open the snapshot dialog from the schema menu and provide:

- **Name** — a short label (e.g. `cgmes-3-rc1`, `2026-04-25-review`).
- **Description** — optional context for reviewers.

When you confirm, RDFArchitect copies the current state of the schema into a separate read-only snapshot. The operation is fast and produces a permalink you can share.

## Sharing the link

The snapshot URL is shown right after creation and is also listed under the snapshot management view. Anyone who can reach your RDFArchitect instance and has the URL can browse the snapshot.

If your deployment requires authentication (it should — see [Access control](/admin-guide/access-control)), reviewers need credentials too. RDFArchitect itself does not gate the link.

## What reviewers see

Following a snapshot link opens the editor in read-only mode pinned to that snapshot. Reviewers can:

- Browse the package tree.
- Open class details (read-only).
- View the diagram.
- Inspect SHACL.
- Compare the snapshot to other schemas, snapshots, or files.

Reviewers cannot edit, import, undo, restore, or delete anything.

## Listing and deleting snapshots

The snapshot management view lists every snapshot of the active schema, with creation time and author. From there you can delete snapshots you no longer need.

Deletion is permanent. The original schema is untouched — only the frozen copy goes away.

## Comparing against a snapshot

The comparison view treats snapshots the same as live schemas, so you can:

- Compare your in-progress working schema against the last review snapshot to see what's changed.
- Compare two snapshots to audit the delta between two milestones.

See [Comparing schemas](./comparing-schemas).

## Exporting a snapshot

Snapshots can be exported just like any read-only schema.

## When to take a snapshot

Good moments to take one:

- Before a risky bulk edit (lets you compare and roll back via comparison).
- At the end of a release-readiness review.
- Before kicking off a migration wizard run.
- Whenever you want to send a colleague a stable URL that won't change as you keep editing.

Snapshots are cheap. There is no penalty for taking many.
