---
title: Schema Migration Wizard
sidebar_position: 12
---

# Schema Migration Wizard

The migration wizard helps you produce a SPARQL Update script that brings instance data from one schema version to another. It walks through guided steps and emits a transparent, auditable artefact you can run against your own triple store.

## When to use it

Use the wizard when you need to evolve instance data along with a schema, for example:

- Migrating CGMES instance files from CIM 16 to CIM 17.
- Renaming a property family across an entire dataset.
- Removing deprecated classes and re-homing their data on a successor.

Use plain export-and-reimport when you only care about the schema, not about pre-existing instance data.

## The steps

### Select source and target schemas

Pick the *from* and *to* schemas. Both can be live schemas, snapshots, or uploaded files. The wizard runs the comparison engine against the pair so the next steps know what changed.

The wizard offers candidate operations based on the comparison: classes that were renamed, properties that changed datatype, enum entries that were removed, etc. You always get the final say.

### Confirm class renames

A list of detected class renames, with confidence scores. For each candidate you can accept the suggestion, override the target manually, or mark the pair as unrelated. You can also add renames the comparison missed.

### Confirm property renames

Same process for attribute and association renames. Each row shows old/new datatype and old/new cardinality if those changed too.

### Confirm defaults for new mandatory properties

If the target schema introduces a property with `multiplicity ≥ 1` that the source schema didn't have, instance data won't validate without a value. The wizard asks you to provide a default — a literal, a SPARQL expression, or "skip and let validation flag it later".

### Export migration script

The final step produces a SPARQL Update script (a sequence of `DELETE/INSERT` blocks) plus an explanatory README. You can download the file or copy it to the clipboard.

The wizard does **not** mutate any RDFArchitect schema automatically — running the script is a deliberate, auditable act you perform externally.

## Example output

```sparql
# RDFA-generated migration script
# Source: cim-16-equipment   Target: cim-17-equipment
# Generated 2026-04-25

# --- Class rename: Breaker -> CircuitBreaker ---
DELETE { ?s a cim16:Breaker }
INSERT { ?s a cim17:CircuitBreaker }
WHERE  { ?s a cim16:Breaker } ;

# --- Attribute rename: name -> displayName on Terminal ---
DELETE { ?t cim16:name ?v }
INSERT { ?t cim17:displayName ?v }
WHERE  { ?t cim16:name ?v ; a cim17:Terminal } ;
```

## Running the script

Recommended sequence:

1. Take a backup or snapshot of the data you're about to migrate.
2. Run the script in a non-production environment first.
3. Validate the result with the target schema's SHACL shapes.
4. If validation passes, replay against production.
