---
title: Schema Migration
sidebar_position: 10
---

# Schema Migration

RDFArchitect ships a guided **Schema Migration** workflow — **View → Migrate Schema** — that turns the differences between two schema versions into an executable **SPARQL UPDATE** script. This script, when run against instance data that conforms to the *source* schema, migrates the data to conform to the *target* schema.

Migration runs as a five-step wizard.

## Step 1 — Select Schemas

Pick the source and the target. Same three modes as compare (stored/stored, uploaded/stored, uploaded/uploaded). RDFArchitect computes the difference and uses it as the starting point for the remaining steps.

## Step 2 — Review Class Renames

Classes that likely correspond across versions but have different URIs or names are listed. Each proposal can be confirmed, rejected, or edited. Anything you confirm here is translated into a `DELETE/INSERT` block that rewrites the RDF type of every instance.

## Step 3 — Review Property Renames

Same logic, applied to attributes, associations, and enum entries, shown in three sub-tabs. This step handles the common case where a property was renamed between CGMES versions without any change to its meaning.

## Step 4 — Review Default Values

Various properties require default values during the migration, for example new required attributes or attributes with a changed datatype, which can be set in this step. Sub-tabs for attributes, associations, and enum entries. In case of association default values are provided by SPARQL patterns which will be inserted into the final script.

## Step 5 — Generate Script

The wizard produces a zip folder containing a `.sparql` file with all the `DELETE/INSERT WHERE` blocks, in the correct order and a SHACL file. This SHACL file can be used after running the migration script to confirm that the resulting data is still valid for your schema.

The generated script is plain SPARQL and runs on any SPARQL 1.1-compliant endpoint, for example Apache Jena Fuseki.
