---
title: Getting Started
sidebar_position: 2
---

# Getting Started

This page gets you from a fresh checkout to the first useful actions in RDFArchitect. For full deployment details, use the [installation guide](/admin-guide/installation).

## Start the application

The shortest path for evaluation is Docker Compose.

1. Check out the repository:

```bash
git clone https://github.com/SOPTIM/RDFArchitect.git
cd RDFArchitect
```

2. **(Optional)** Start Apache Jena Fuseki for snapshot storage. The [Fuseki quickstart](/admin-guide/installation#fuseki-quickstart) has the exact command.
3. From the repository root, start RDFArchitect:

```bash
cd docker
docker compose up --build
```

4. Open `http://localhost:3000` and select **Open Editor**.

For source development, run the backend and frontend separately. See [Local development setup](/admin-guide/installation#2-local-development-setup).

## First steps in the editor

1. Import a schema with **File → Import → Schema (RDFS)**. RDF/XML, Turtle, and N-Triples are supported. See [Workspace and importing data](/user-guide/workspace-and-importing).
2. Select a package in the left navigation tree to inspect the diagram and class list. See [Organising a schema](/user-guide/organising-schemas).
3. Select a class to review labels, namespace, package, attributes, associations, stereotypes, comments, and constraints (SHACL). See [Editing classes](/user-guide/editing-classes).
4. If the dataset is read-only, use **Enable Editing** before making changes. See [Read-only mode](/user-guide/read-only-mode).
5. Export the schema or create a snapshot when you are ready to share it. See [Sharing and exporting](/user-guide/sharing-and-exporting).

For a complete walkthrough, continue with the [User Guide](/user-guide/overview). Administrators should also review [Configuration](/admin-guide/configuration) and [Security](/admin-guide/security) before running a shared instance.
