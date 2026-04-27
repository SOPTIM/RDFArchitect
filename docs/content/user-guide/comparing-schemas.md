---
title: Comparing Schemas
sidebar_position: 11
---

# Comparing Schemas

The comparison view answers the question "what changed between these two schemas?" — at the package, class, and property level, with an inline diff for individual triples.

![Compare schemas](/img/screenshots/compare.png)

## Opening a comparison

From the toolbar, open the compare dialog. You pick a *left* and a *right* side. Either side can be:

- A live schema in the dataset.
- A snapshot of any schema.
- A file you upload from disk.

The comparison runs server-side; large schemas may take a few seconds.

## Reading the result

The result page is split into expandable sections:

| Section | What it lists |
| ------- | ------------- |
| **Packages** | Packages added on the right, removed from the left, or renamed. |
| **Classes** | Per-package, the classes added/removed/changed. |
| **Attributes** | Per-class, the attributes added/removed/changed (datatype, cardinality, fixed value, etc.). |
| **Associations** | Per-class, association deltas including renamed targets. |
| **Enum entries** | For enumerations, the entries added/removed/renamed. |
| **SHACL shapes** | Constraint shapes that differ. |
| **Other triples** | Anything that doesn't fall into the categories above. |

Each row can be expanded to show the inline triple-level diff.

## Inline diffs

Inline diffs show:

```diff
- <removed triple>
+ <added triple>
```

Whitespace differences inside `rdfs:comment` literals are normalized so re-flowed comments don't drown the real change.

## Filtering

Filter chips on the result page let you hide additions, removals, or changes; restrict to specific kinds of element; or focus on one package.

## Use cases

- **Pre-merge review.** Compare your working schema to the blessed baseline before importing the latter back.
- **Release planning.** Compare two snapshots that flank a release window.
- **Migration planning.** Compare the source and target schemas before launching the [migration wizard](./migration-wizard).
- **External alignment.** Compare your in-house extension against the upstream CIM/CGMES file you started from.

## A note on rename detection

The compare engine does its best to detect renames (same triples on a class with a new IRI), but a rename combined with structural changes can still appear as a delete-plus-add pair. Inspect manually if it looks suspicious.
