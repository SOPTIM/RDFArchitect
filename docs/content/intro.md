---
slug: /
title: Introduction
sidebar_position: 1
---

# RDFArchitect

**RDFArchitect** is an open-source, web-based tool for visualizing, editing, validating, and sharing RDFS schemas with **CIM** extensions — as used in **CGMES** and the **ENTSO-E** network code profiles — together with their **SHACL** constraints.

It provides a practical modeling workflow for CIM-based RDF schemas without requiring a desktop modeling tool or RDF expertise from every team member who needs to read or review the model.

![RDFArchitect editor](/img/screenshots/editor.png)

## What can you do with it?

- **Import** RDFS, OWL, and SHACL files (Turtle, RDF/XML, N-Triples, etc.).
- **Edit** classes, attributes, associations, enumerations, and notes through a UML-style diagram editor.
- **Validate** schemas using both auto-generated and hand-written SHACL shapes.
- **Compare** two schema versions side-by-side and review changes at the package, class, and property level.
- **Migrate** instance data from one schema version to the next via a guided wizard.
- **Share** read-only snapshots so reviewers can browse a schema without installing anything.
- **Track** every change in a per-schema history, with undo/redo, and version restore.

## Who is it for?

| Role | Why you care |
| ---- | ------------ |
| **Power-system / electrical engineers** | Read, navigate, and review CIM-based schemas without a heavyweight UML tool. |
| **Information modelers / data architects** | Author RDFS classes, attributes, associations, and SHACL constraints with full versioning and comparison. |
| **Project managers / product owners** | Inspect snapshots, comment on diagrams, and compare profile versions during release reviews. |
| **Developers** | Build on a Spring-Boot/SvelteKit codebase that exposes a clean REST API and is documented end-to-end. |
| **Administrators** | Deploy via Docker Compose, point at a Fuseki triple store, and integrate access control via a reverse proxy. |

## Pick your starting point

import DocCardList from '@theme/DocCardList';

<DocCardList items={[
  { type: 'link', href: '/user-guide/overview', label: 'User Guide', description: 'Step-by-step walkthrough of the editor, import/export, SHACL, comparison, migration, and sharing.' },
  { type: 'link', href: '/developer-guide/overview', label: 'Developer Guide', description: 'Architecture, contribution workflow, and end-to-end feature recipes for the codebase.' },
  { type: 'link', href: '/admin-guide/installation', label: 'Administration', description: 'Installation, configuration, Fuseki, backups, access control, and upgrades.' },
  { type: 'link', href: '/reference/cim-mapping', label: 'CIM/CGMES Mapping', description: 'How RDFArchitect concepts map to CIM, CGMES, and ENTSO-E terminology.' },
  { type: 'link', href: '/reference/faq', label: 'FAQ & Troubleshooting', description: 'Answers to the most common questions and known issues.' },
]} />

## Maintenance and commercial support

RDFArchitect is actively developed and maintained by **[SOPTIM AG](https://www.soptim.de/)**. The project is open source and free to use under Apache 2.0; commercial support — including consulting, custom feature development, deployment assistance, and CIM/CGMES advisory — is available on request.

If your organisation needs paid support or has integration questions, contact [opencgmes@soptim.de](mailto:opencgmes@soptim.de).

## License

RDFArchitect is released under the [Apache License 2.0](https://github.com/SOPTIM/RDFArchitect/blob/main/LICENSE).
