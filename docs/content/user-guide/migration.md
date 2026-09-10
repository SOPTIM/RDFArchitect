---
title: Schema Migration
sidebar_position: 10
---

# Schema Migration

RDFArchitect ships a guided **Schema Migration** workflow — **View → Migrate Schema** — that turns the differences between two schema versions into an executable **SPARQL UPDATE** script. This script, when run against instance data that conforms to the *source* schema, migrates the data to conform to the *target* schema.

Migration runs as a seven-step wizard. Each step operates on the diff computed once in Step 1; nothing is written back to your stored schemas until you actually run the generated script.

## Step 1 — Select Schemas

Pick the source ("Before") and the target ("After") schema. Four comparison modes are available and can be swapped with the **Swap** button, which also swaps the two selected CGMES versions along with the schemas:

- **Stored → Stored** — compare two graphs already stored in the system (workspace + graph on each side).
- **Uploaded → Stored** — upload a file for the source and pick a stored graph as the target.
- **Stored → Uploaded** — pick a stored graph as the source and upload a file for the target.
- **Uploaded → Uploaded** — upload a file on both sides.

Each side additionally has its own **CGMES Version** selector (3.0 or 2.4.15), used later for validation and for the version metadata embedded in the generated migration report.

You can toggle **Ignore prefixes**, which makes rename detection compare only the local name (label) of a resource instead of its full IRI. This matters when a migration is purely a namespace/prefix change: with the flag enabled, such resources are treated as unchanged and are hidden from the rename-review tables in Steps 3 and 4 (they still count as a `RENAME`, just without being surfaced for manual confirmation) — Step 6 marks them with a neutral "Changed" badge instead of "Renamed" for the same reason.

Confirming this step calls the backend to compute the semantic diff between the two schemas and stores it server-side for the rest of the wizard (`computeMigrationContext`); leaving the wizard early clears that context again.

## Step 2 — Validate Schemas

Both selected schemas are validated independently against their own schema-completeness rules (using the CGMES version chosen for each side in Step 1), and the results are shown side by side under **Before** / **After**. This step is purely informational: reported issues are meant to be reviewed before continuing, but they never block progressing through the wizard.

## Step 3 — Review Class Renames

Classes that likely correspond across versions but were given a different URI or label are listed as *rename candidates*, together with a **confidence score** (how sure the automatic detection is). For each candidate you can:

- accept the suggested target class,
- pick a different one from the classes that were newly added in the target schema, or
- dissolve the mapping entirely, which puts the target class back into the plain "Added Classes" list below.

Classes newly added in the target schema that are linked to a rename candidate are annotated with **"renamed from &lt;old class&gt;"** in that list, so it's easy to see which additions are genuine new classes versus renaming targets that are still waiting to be picked.

Anything you confirm here is translated into a `DELETE/INSERT` block that rewrites the RDF type of every instance of the old class to the new one.

<img width="2557" height="1295" alt="image" src="https://github.com/user-attachments/assets/3bcaa8ef-c892-4b16-9a38-54e92ebbaf93" />

## Step 4 — Review Property Renames

The same rename-candidate/confidence-score/"renamed from" mechanism as Step 3, applied per class to **attributes**, **associations**, and **enum entries**, shown in three sub-tabs. Associations that cannot be instantiated (`associationUsed = false`) are left out of the association tab entirely, since they never carried any instance data that a mapping could rewrite.

This step covers the common case where a property was renamed between CGMES versions without any change to its meaning — moving a property to a *different* class, or merging/splitting properties, is not yet supported by the UI (see the note at the end of this page).

## Step 5 — Review Default Values

Some properties need a value to be filled in for existing instances before the migration can proceed. This step has three sub-tabs, one per property kind:

### Attributes

Applies to newly added attributes, attributes whose data type changed, and attributes that became required. Attributes needing attention are marked with a red **\*** and highlighted; the **Continue** button is disabled while any of them are still open.

For each such attribute you can:

- enter a **Default Value** (a fixed value, or pick one from the enumerated **allowed values** if the attribute's data type is restricted to a list),
- tick **Equivalent** when a data-type change doesn't actually require a default, because the old and new data types are interchangeable for that attribute — all existing values are then kept as-is,
- tick **Don't Init** to leave the attribute uninitialized and continue the migration without a value for it (this clears any value you had entered),
- for attributes that only became optional-with-a-default rather than required, tick **Init Optional** to still initialize existing instances with the given default even though it isn't mandatory.

**Don't Init All** / **Clear Don't Init** buttons apply or clear the "leave uninitialized" choice for every open attribute across all classes at once.

### Associations

Applies to newly added associations and associations whose target class changed. Instead of a literal value, you provide a **SPARQL mapping**: a query fragment that must bind a `?target` variable, which is inserted into the `WHERE` clause of the generated update to compute the association's target for each instance. Leaving the field empty means no default is set for that association. This sub-tab never blocks continuing — an association without a mapping is simply left unset.

### Enum Entries

Applies to enum entries that were deleted in the target schema. Every deleted enum entry needs a **Replacement Value** picked from the entries that still exist in the target schema; this replacement is applied to every instance currently using the deleted value. **Continue** is disabled until every deleted entry has a replacement selected.

## Step 6 — Review Changes

An overview of every computed change (classes, attributes, associations, enum entries), one card per resource, grouped under its class. Changes that a class only inherits from a superclass are filtered out here to avoid repetition — they're listed once, on the superclass.

Each card shows:
- a colored badge for the change kind (**Added** / **Deleted** / **Changed** / **Renamed**; a rename that's only a prefix change under "Ignore prefixes" is shown as a neutral "Changed" instead),
- the old → new IRI (or label, under "Ignore prefixes") when applicable,
- an expandable **Changes** list with the individual field-level diffs (e.g. *Data type changed*, *Made required*, *Multiplicity changed*, *Target changed*, ...), each shown as an old-value → new-value pair,
- a free-text **Comment** field.

Comments entered here are carried through into both the generated migration script (as inline comments on the corresponding update block) and the downloadable migration report.

## Step 7 — Generate Artifacts

The final step lets you download the artifacts you need:

- **Migration Script** — a `.sparql` file with all the `DELETE/INSERT WHERE` blocks in the correct order, together with SHACL shapes for validating the migrated data against the new schema.
- **Summary Report** — a Markdown report listing only directly changed classes; a change inherited by many subclasses is listed once, under the superclass.
- **Detailed Report** — a Markdown report listing every affected class individually, repeating inherited changes on each subclass that has them.

The generated script is plain SPARQL and runs on any SPARQL 1.1-compliant endpoint, for example Apache Jena Fuseki.

:::warning
Script generation may not yet handle every edge case — multiplicity changes on associations being one known example. Always validate the migrated data against the provided SHACL constraints afterwards, and adjust manually if inconsistencies are found.
:::

**Recommended sequence for applying a migration:**
1. Validate your source data against the *old* schema's SHACL constraints to ensure data quality before migrating.
2. Download the migration script (Step 7).
3. Execute the SPARQL UPDATE script against your data.
4. Validate the migrated data against the *new* schema's SHACL constraints to confirm the migration succeeded.
