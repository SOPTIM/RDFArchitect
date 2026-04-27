---
title: Prefixes and Namespaces
sidebar_position: 13
---

# Prefixes and Namespaces

Every RDF resource lives in a namespace, and every namespace has a short prefix used to write IRIs concisely. RDFArchitect manages prefixes both globally and per dataset.

![Manage namespaces](/img/screenshots/manage-namespaces.png)

## Where prefixes show up

- In the class IRI field (`cim:Breaker`).
- In the attribute datatype drop-down (`xsd:string`).
- In the SHACL inspection views.
- In every export.

When a prefix is missing, IRIs are shown in their full form. That's a sign you should add the prefix.

## Levels of configuration

| Level | Scope | Used for |
| ----- | ----- | -------- |
| **Global** | All datasets, system-wide. | Sensible defaults that almost everyone needs (`rdf`, `rdfs`, `owl`, `xsd`, `sh`, `cim`). |
| **Dataset** | One dataset and all its schemas. | Project-specific prefixes (e.g. `entso-e`, your in-house extension). |

Dataset-level prefixes override global prefixes with the same short name.

## The prefix manager

Open the namespaces dialog from the dataset menu. Each row shows:

| Column | Description |
| ------ | ----------- |
| **Prefix** | Short name (e.g. `cim`). |
| **Namespace IRI** | Full URI (e.g. `http://iec.ch/TC57/CIM100#`). |
| **Source** | Whether the prefix comes from the global defaults or from this dataset. |

You can add a new prefix, edit a dataset-level one, or remove one. Removing a dataset-level prefix falls back to the global definition (if any).

Changes take effect immediately for new operations; already-rendered diagrams may need a refresh.

## What gets imported

When you import a Turtle file, every prefix declaration in the file is added to the dataset's prefix list (without overwriting existing dataset-level prefixes). This is what makes round-tripping a foreign file pleasant: re-exports come back with the same prefixes.

## Common pitfalls

- **Two prefixes for the same namespace** — usually the result of importing files from two upstream sources. The prefix manager flags duplicates.
- **A namespace IRI without a trailing `#` or `/`** — RDF concatenates the local name onto the namespace verbatim. Make sure your namespace ends with the separator the file expects.
- **Prefix used inside literal values** — prefixes only expand inside IRIs. A string `"cim:foo"` stays a string.
