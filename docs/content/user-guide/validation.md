---
title: Validation
sidebar_position: 8
---

# Validation

RDFArchitect validates on two levels. **Schema validation** checks one schema on its own. **Workspace validation** checks whether the schemas of a workspace fit together — for example whether a class inherits from the same superclass everywhere.

## Schema validation

**View → Validate Schema** opens a dialog where you pick the CGMES version and the schema: either one stored in a workspace or a file from your disk. The same dialog is available from a schema's context menu.

The report lists missing labels, classes without a package, incomplete profile headers, and similar findings for that one schema.

## Workspace validation

Workspace validation runs immediately, without a dialog:

- **View → Validate Workspace** checks the workspace you are currently in.
- **Validate Workspace** in a workspace tab's context menu checks that workspace.
- **Validate Schema in Workspace** in a schema's context menu checks one schema against the other schemas of its workspace, and shows only the findings that involve it.

Both modes run the same checks. The CGMES version is not needed here, it is derived from the schemas themselves.

## Reading the report

The report opens as a table with one row per finding:

| Column | Content |
| --- | --- |
| Severity | Error, warning, or info |
| Check | Which check reported the finding |
| Resource | The class, attribute, association, or enum entry it is about |
| Finding | What is wrong |
| Schemas | The schemas involved, by their `dcat:keyword` |

Clicking a row expands it and shows the full URI plus one line per schema with the value found there — for instance the different superclasses. **Open Class** jumps to that class in its schema's class editor.

Every column header except *Finding* content is clickable and groups the table by that column: by severity, by check, by class, by finding, or by schema. Clicking the same header again removes the grouping. When grouped by schema, a finding appears under every schema it involves, so each group shows everything that concerns that schema. **Collapse All** folds the groups away.

A workspace counts as invalid as soon as one error is reported. Warnings and infos do not make it invalid.

## The checks

### Inheritance

| Finding | Severity |
| --- | --- |
| Class inherits from different superclasses in different schemas | Error |
| Inheritance cycle across the schemas | Error |
| Class has more than one superclass | Warning |
| Class has a superclass in some schemas but none in others | Info |

The last case is normal in CGMES: SV, for example, uses `ConductingEquipment` without repeating its superclass.

### Datatypes

| Finding | Severity |
| --- | --- |
| Datatype is defined as a different kind of class in another schema | Error |
| Datatype has different attributes in different schemas | Error |
| Datatype attribute has a different data type, fixed value, default value, or multiplicity | Error |
| Datatype or datatype attribute has different comments | Info |

The first row covers the case the checks were written for: something that is a `CIMDatatype`, `Primitive`, `Compound`, or enumeration in one schema must not be a concrete class in another.

### Duplicate properties

| Finding | Severity |
| --- | --- |
| Attribute or association belongs to different classes in different schemas | Error |
| Attribute has different data types, association points to different classes | Error |
| Attribute or association is defined in multiple schemas | Warning |
| Attribute or association has different multiplicities | Warning |

A property defined in several schemas is legitimate — `IdentifiedObject.name` exists in several CGMES profiles — so it is only a warning. Multiplicities are compared normalised, `M:1` and `M:1..1` count as equal. Attributes of datatypes are left to the datatype checks.

### Profiles and versions

| Finding | Severity |
| --- | --- |
| Schemas of the workspace use different CIM versions | Error |
| A single schema uses classes of multiple CIM versions | Error |
| Keyword or version IRI is used by multiple schemas | Error |

### Associations, enum entries, labels

| Finding | Severity |
| --- | --- |
| Association has different inverse associations in different schemas | Error |
| Enum entry belongs to different enumerations in different schemas | Error |
| Label differs between schemas | Info |

### Technical findings

A resource that cannot be read is reported as a warning, as is a check that fails unexpectedly. Neither makes the workspace invalid, and the remaining checks keep running.

## Validating the official CGMES profiles

The ENTSO-E profiles of CGMES 3.0 and 2.4.15 pass without errors. They do produce warnings for properties defined in several profiles, and infos for classes whose superclass is omitted in some profile. Both are intentional in those releases.
