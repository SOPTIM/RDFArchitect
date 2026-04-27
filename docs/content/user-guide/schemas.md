---
title: Working with Schemas
sidebar_position: 4
---

# Working with Schemas

Everything you edit in RDFArchitect lives inside a **schema**. This page is the reference for managing them.

## Selecting the active schema

The toolbar shows which schema is currently active. Every editor action — class edits, imports, comparisons, SHACL inspection — targets it. Switching schemas is a single click.

## Creating, importing, and deleting

You can:

- **Create** a new, empty schema.
- **Import** a file into a new schema or replace the contents of an existing one (see [Import & export](./import-export)).
- **Delete** a schema. Its change history and snapshots are removed too.
- **Mark a schema read-only** when it shouldn't change anymore (see [Read-only mode](./readonly-mode)).
- **Take a snapshot** to share a frozen, browsable copy (see [Snapshots and sharing](./snapshots-and-sharing)).

## How to organise multiple schemas

Common patterns:

- **One schema per profile.** For CGMES this often means one schema per profile (`EquipmentProfile`, `TopologyProfile`, `SteadyStateHypothesis`, `SVProfile`, …).
- **One schema per version.** Keep `cim16-equipment` and `cim17-equipment` side by side when planning a migration.
- **Working / blessed pair.** A live working schema plus a stable, snapshot-protected baseline.

## Datasets

If your installation hosts multiple unrelated bodies of work, those can live in separate datasets. See [Core Concepts](./concepts) for what a dataset is.

## Read-only mode

A schema (and optionally an entire dataset) can be locked. Read-only schemas:

- Show every UI screen, but with editing controls hidden or disabled.
- Cannot accept imports or migrations.
- Can still be exported, compared, and snapshotted.

See [Read-only mode](./readonly-mode) for the full list of effects.

## Storage

Schemas are stored on the server side; you don't manage files yourself. Administrators looking after the underlying triple store should read [Backups](/admin-guide/backups).

## Practical limits

- Very large schemas (millions of triples) render and edit slower in the browser.
- Diagrams with hundreds of classes per package degrade visibly.

If your model exceeds those limits, split it into multiple schemas along package boundaries or use the diagram filter view to hide detail you don't need.
