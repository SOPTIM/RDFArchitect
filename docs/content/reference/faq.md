---
title: FAQ & Troubleshooting
sidebar_position: 3
---

# FAQ & Troubleshooting

Answers to common questions, in the order they typically come up.

## General

### Who is RDFArchitect for?

Anyone who works with CIM-based RDF schemas: electrical engineers who need to read, review, or modify CGMES and ENTSO-E Network Code Profiles; software architects who maintain internal extension profiles; and project managers who need to plan releases and migrations. It is explicitly designed so that a user does not need to write code to get full value out of it.

### How does it relate to CIMTool, Enterprise Architect, or other UML-based tools?

Those tools work with the CIM UML model. RDFArchitect works with the RDFS representation of profiles — the same artefacts those tools produce on export. If your organisation maintains the profile in UML as the single source of truth, RDFArchitect is a powerful *consumer* tool: browsing, SHACL work, migration planning, and sharing for review. If you do not have a UML source of truth, RDFArchitect can also be used as the primary editor.

### Is it free to use?

Yes. RDFArchitect is released under the Apache License 2.0. There is no paid tier, no feature gate, and no data collection.

### Where is my data stored?

In the triple store you configure — **Apache Jena Fuseki** by default. If you run everything locally, everything stays on your machine. RDFArchitect does not send data anywhere external.

### Is there a public hosted version?

Not from the project itself. Any hosting is done by individual users or organisations.

## Imports and formats

### Which file formats can I import?

Schemas: **RDF/XML (.rdf), Turtle (.ttl), N-Triples (.nt)**. SHACL: the same three. Most CGMES and ENTSO-E releases ship as RDF/XML and can be imported directly without conversion.

### Can I import multiple profiles at once?

Yes, its possible to select multiple profiles at once during the import. You can also directly import .zip files with profiles in the root folder, which then get imported.

### My import says the dataset is read-only. What now?

The dataset has been locked against edits. Select the dataset and use **Edit → Enable Editing** in the menu. If the menu entry is not available, the dataset is a snapshot and is read-only by design — create or choose a different dataset for the import.

### The imported file does not show all classes I expected.

Check whether the classes are in a package that is not visible under the current view filter. Open **Edit → View Filter** and ensure external packages are shown if the missing classes come from an imported dependency.

### My RDF/XML file is larger than 50 MB.

The default upload limit is 50 MB. For larger files, an administrator can raise the limits described in the [installation guide](/admin-guide/installation#file-upload-size). For most CGMES releases, 50 MB is more than enough; the profiles themselves are usually a few MB each.

## Editing

### I made a mistake — how do I undo it?

**Ctrl+Z** or **Edit → Undo**. Undo works across every kind of edit (classes, attributes, associations, packages, namespaces, ontology, SHACL). Up to 256 steps of history per graph.

### Why can't I edit this class / package?

Three possibilities:

1. The **dataset is read-only** — enable editing (Edit → Enable Editing) or choose a different dataset.
2. The class belongs to an **external package** — an imported dependency. By design these are not editable from the current graph; edit them in their owning graph instead.
3. You are viewing a **snapshot**. Snapshots are read-only by construction.

### What is the "default" package?

A reserved package that represents classes which have not been assigned to any explicit package. It cannot be renamed or deleted. When importing a profile that uses explicit packages everywhere, this is usually empty.

## SHACL

### Why are there two separate SHACL views — generated and custom?

Because they answer two different questions.

- **Generated SHACL** tells you *"what constraints follow directly from the schema?"* It is recomputed from the schema whenever you look at it, so it is always in sync.
- **Custom SHACL** is *"the SHACL I imported from somewhere else"* — typically the official SHACL that ships with a CGMES or ENTSO-E release. It is preserved byte-for-byte and does not change when the schema changes.

Keeping them separate means you can see where a given constraint came from, and you can freely edit the schema without clobbering imported SHACL.

### My custom SHACL file won't save — what's wrong?

The TTL parser rejects the file. The dialog highlights the offending line; most often it is an unknown prefix or a dangling comma. Fix the TTL and the Save button re-enables.

### How do I validate instance data against the SHACL RDFArchitect produces?

RDFArchitect does not run SHACL validation itself — it produces and manages the SHACL. To validate instance data, export the SHACL (File → Export → Constrains (SHACL)) and run it against your data with any SHACL engine (Apache Jena's `shacl` CLI, TopBraid, pySHACL, etc.).

## Migration

### The migration wizard says it cannot handle all edge cases. Which ones?

As of 1.0.0, multiplicity changes on associations are the known gap. If a property changed from `0..1` to `1..*` (or vice versa) between the source and target schemas, the generated script does not automatically split or merge instance triples accordingly — you need to handle this case in a follow-up SPARQL script.

### Should I trust the migration script blindly?

No. Always run it against a test dataset first, then validate the result with the target profile's SHACL, and only then run it against production data. This is true of any mechanical migration, not specific to RDFArchitect.

### Can I edit the migration script before running it?

Yes. The output is plain SPARQL 1.1 UPDATE and can be opened, inspected, and modified in any text editor before execution.

## Sharing and collaboration

### I shared a snapshot but the recipient can't open it.

The recipient needs network access to the same RDFArchitect instance. Snapshots are URLs, not self-contained files. If the recipient is outside your network, host the instance somewhere they can reach, or export the schema and send the file.

### Can I un-share a snapshot?

Not from the UI as of 1.0.0. Snapshots are stored as datasets in the triple store; an administrator can delete the corresponding dataset from Fuseki directly. See the [administrator's guide](/admin-guide/security#snapshot-links).

### Two people are editing the same graph at the same time. What happens?

Last write wins. RDFArchitect does not currently have multi-user conflict resolution. In practice, teams coordinate per-graph ownership (one editor per profile at a time) or use the snapshot + review workflow for collaborative work.

## Performance

### The changelog view is slow.

The changelog of a long-lived graph can contain thousands of entries. Older entries are transparently compressed, but rendering the full list still takes time. Using the search bar at the top of the changelog is faster than scrolling.

## Operations (for administrators)

### The backend cannot reach Fuseki.

Check `database.http.endpoint` in the backend config. From inside the backend container, `curl http://<endpoint>/$/ping` should return `pong`. If it does not, check container networking — the Docker Compose file uses `host.docker.internal:3030` on the assumption that Fuseki runs on the host.

### I want to move from the file-based store to Fuseki.

Export every dataset as RDF/XML, switch `database.databaseType` to `http`, point `database.http.endpoint` at Fuseki, restart, create the datasets you need in Fuseki, and re-import the files. File-based storage is explicitly a development-only option.

### I'm getting 413 Payload Too Large on large imports.

Raise the upload limits described in the [installation guide](/admin-guide/installation#file-upload-size). If a reverse proxy sits in front of the backend, it likely has its own body size limit that also needs to be raised (`client_max_body_size` for nginx).

## Where to get help

- **Documentation**: this site.
- **Bug reports and feature requests**: [GitHub Issues](https://github.com/SOPTIM/RDFArchitect/issues).
- **Questions and discussion**: [GitHub Discussions](https://github.com/SOPTIM/RDFArchitect/discussions).
- **Commercial support, professional services, custom extensions**: contact SOPTIM AG at [opencgmes@soptim.de](mailto:opencgmes@soptim.de).
- **Security reports**: see [`SECURITY.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/SECURITY.md) for responsible disclosure.
