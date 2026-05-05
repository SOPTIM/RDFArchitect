---
title: Schema Migration
sidebar_position: 10
---

# Schema Migration

RDFArchitect ships a guided **Schema Migration** workflow — **View → Migrate Schema** — that turns the differences between two schema versions into an executable **SPARQL UPDATE** script. This script, when run against an exchange dataset that conforms to the *source* schema, transforms it into a dataset that conforms to the *target* schema.

Migration runs as a five-step wizard.

## Step 1 — Select Schemas

Pick the source and the target. Same three modes as compare (stored/stored, uploaded/stored, uploaded/uploaded). RDFArchitect computes the difference and uses it as the starting point for the remaining steps.

## Step 2 — Review Class Renames

Classes that likely correspond across versions but have different URIs or names are listed. Each proposal can be confirmed, rejected, or edited. Anything you confirm here is translated into a `DELETE/INSERT` block that rewrites the RDF type of every instance.

## Step 3 — Review Property Renames

Same logic, applied to attributes, associations, and enum entries, shown in three sub-tabs. This step handles the common case where a property was renamed between CGMES versions without any change to its meaning.

## Step 4 — Review Default Values

For every property that exists in the *target* schema but not in the *source*, RDFArchitect asks what value to insert when migrating existing instances. Sub-tabs for attributes, associations, and enum entries. You can specify a fixed value, the result of a SPARQL expression, or leave it blank (in which case the target property simply has no value for migrated instances, which may itself be a SHACL violation you need to review).

## Step 5 — Generate Script

The wizard produces a single `.sparql` file containing all the `DELETE/INSERT WHERE` blocks, in the correct order. A warning is shown that multiplicity changes on associations are not yet handled automatically, and that the migrated data should be validated against the target profile's SHACL afterwards.

The generated script is plain SPARQL and runs on any SPARQL 1.1-compliant endpoint (e.g. Apache Jena Fuseki, the triple store RDFArchitect uses itself).
