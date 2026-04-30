---
slug: /
title: Introduction
sidebar_position: 1
---

# RDFArchitect

Documentation for **RDFArchitect 1.0.0** — the open-source web-based editor for CIM/CGMES and ENTSO-E RDFS schemas with SHACL constraints.

RDFArchitect is a web-based tool for **visualising, editing, and sharing RDFS schemas with CIM extensions** — as used in CGMES and the ENTSO-E Network Code Profiles — together with their **SHACL constraints**. It is an open-source alternative to proprietary CIM/RDF modelling tools.

![RDFArchitect homepage](/img/screenshots/homepage.png)

## Who should read what

If you are new to RDFArchitect, start with the **User Guide**. Otherwise, the table below points you to the most relevant section for your role.

| If you are…                                  | Start with                                                            | Then read                                                                                        |
| -------------------------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| An **electrical engineer** / CIM modeller    | [User Guide](/user-guide/overview)                                    | [CIM/CGMES concepts mapping](/reference/cim-mapping), [Features](/reference/features)            |
| A **software architect** / schema maintainer | [User Guide](/user-guide/overview)                                    | [Features](/reference/features), [CIM mapping](/reference/cim-mapping)                           |
| A **project manager** / product owner        | [Features](/reference/features)                                       | [User Guide — sharing](/user-guide/sharing-and-exporting), [migration](/user-guide/migration)    |
| An **administrator** deploying the service   | [Installation](/admin-guide/installation)                             | [Administrator's guide](/admin-guide/overview), [FAQ](/reference/faq)                            |
| A **developer** contributing or extending    | [Developer guide](/developer-guide/overview)                          | [`CONTRIBUTING.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/CONTRIBUTING.md)    |

## What you can do with it

- **Import and export** CGMES and ENTSO-E profiles in RDF/XML, Turtle, or N-Triples.
- **Visualise** classes, attributes, associations, and inheritance as UML-style diagrams, organised by package.
- **Edit** every aspect of a profile — classes, attributes, associations, enum entries, stereotypes, comments, namespaces, and the profile header — through a validating UI.
- **Generate SHACL** automatically from the schema, and manage imported SHACL alongside it.
- **Track changes** with an edit changelog, undo/redo, and the ability to restore any previous state.
- **Compare** two versions of a profile and see a structured list of additions, removals, and modifications.
- **Migrate** instance data between profile versions by generating a reviewable **SPARQL UPDATE script** through a guided wizard.
- **Share** a complete, read-only, navigable view of a profile via a single URL — no downloads, no tooling required by the recipient.

## Architecture, briefly

- **Frontend** — SvelteKit single-page application
- **Backend** — Spring Boot REST service
- **Triple store** — Apache Jena Fuseki (any SPARQL 1.1 endpoint works)

Everything is open-source, Apache 2.0 licensed.

## Maintenance and commercial support

RDFArchitect is actively developed and maintained by **[SOPTIM AG](https://www.soptim.de/)**. The project is open source and free to use under Apache 2.0; commercial support — including consulting, custom feature development, deployment assistance, and CIM/CGMES advisory — is available on request.

If your organisation needs paid support or has integration questions, contact [opencgmes@soptim.de](mailto:opencgmes@soptim.de).

## Community and support

- **Bugs and feature requests**: [GitHub Issues](https://github.com/SOPTIM/RDFArchitect/issues)
- **Questions and discussion**: [GitHub Discussions](https://github.com/SOPTIM/RDFArchitect/discussions)
- **Security reports**: see [`SECURITY.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/SECURITY.md)
- **Contributing**: see [`CONTRIBUTING.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/CONTRIBUTING.md)

## License

RDFArchitect is released under the [Apache License 2.0](https://github.com/SOPTIM/RDFArchitect/blob/main/LICENSE).

*Version: 1.0.0 — Released 2026-04-24. See [Changelog](/reference/changelog) for the release history.*
