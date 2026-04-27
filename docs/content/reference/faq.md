---
title: FAQ
sidebar_position: 3
---

# FAQ

Answers to the most common questions.

## General

### Is RDFArchitect free?

Yes. Apache 2.0. Use it for anything, including commercial use, with attribution.

### Is there a hosted version?

No official hosted version is offered as of 1.0. Self-host with the Docker images.

### What CIM versions are supported?

RDFArchitect is version-agnostic. It edits whatever schema you import. CIM 16, CIM 17, CIM 18, CGMES 2.4.15, CGMES 3.0, and ENTSO-E network code profiles all import cleanly when supplied as RDF/XML or Turtle.

### Does it replace Sparx Enterprise Architect?

For RDF-level schema editing and review — yes. For full UML-mode­l­ling activities (sequence diagrams, state machines, requirements), no. RDFArchitect is intentionally focused on the RDFS subset.

## Modelling

### Can I have multiple inheritance?

No. RDFS-level single inheritance only, as used in CIM.

### Can a class belong to two packages?

No. Each class belongs to exactly one package. This matches the CIM convention.

### Can I rename a class without breaking instance data?

In RDFArchitect itself, yes — the rename ripples to associations within the same schema and is recorded in the history. For *instance* data living elsewhere, use the migration wizard to generate a SPARQL Update that performs the rename across instance data.

### How are IRIs assigned to new classes?

Combining the active package's namespace and the class name (sanitised). Manual override is allowed in the IRI field. As of 1.0.0, RDFArchitect uses UUIDs for resource identity internally even when display IRIs change.

### Can I define an OCL constraint?

No. SHACL is the constraint language. OCL triples in imported files are preserved verbatim but not interpreted. If you need OCL, evaluate it externally and import only the relevant SHACL.

## Editing workflow

### How do I undo more than one step?

Press undo repeatedly. Or open the changelog and *Restore* to a specific point in time.

### Can two people edit the same schema?

Yes, but RDFArchitect does **not** detect conflicts at the model level. The last save wins. Either coordinate via process or split editing across separate schemas.

### Where did my custom SHACL go after re-importing?

Reimporting *into the same schema* replaces its contents, including custom SHACL. Use the SHACL upload dialog instead — it adds shapes without replacing the model. Or take a snapshot before the import.

### Can I bulk-edit attributes across many classes?

Not from the UI in 1.0. Two workarounds: write a SPARQL Update against Fuseki directly, or export, edit the Turtle externally, and re-import.

## Import / export

### What's the largest file I can import?

The default upload limit is 50 MB (configurable via `spring.servlet.multipart.max-file-size`). For larger files, raise the limit or load directly into Fuseki.

### Why is my exported file slightly different from what I imported?

RDFArchitect re-serialises through Jena, which:

- Sorts triples deterministically.
- Renames blank nodes.
- Normalises whitespace inside literals (only on display; literals are preserved).

The semantics are identical. If you need byte-identical round-trips, store the original file separately.

### Can I export only one package?

Not currently. Export is whole-schema. As a workaround, copy the package's classes into a fresh schema (use the export+import dance) and export that.

## SHACL

### Why are there both generated and custom shapes?

Generated shapes encode what the schema *says* (cardinality, datatype). Custom shapes encode what the schema *can't say* (patterns, ranges, cross-property constraints). Most projects need both.

### Can I edit a generated shape?

No — change the model and the shape regenerates. If you need a constraint the generator doesn't produce, write it as a custom shape.

### Can I author custom SHACL inside RDFArchitect?

Not currently. Author the shapes as a Turtle file and import via the SHACL upload dialog.

### How do I run the SHACL constraints against instance data?

Export the SHACL (generated + custom) and feed it to any SHACL validator (`shacl validate`, pySHACL, TopBraid). RDFArchitect does not validate instance data inside the application in 1.0.

## Migration wizard

### Does the wizard mutate my Fuseki?

No — it only emits a SPARQL Update script. You run it explicitly against the target store.

### Why do some renames need confirmation when they look obvious?

Because automated rename detection is heuristic. Confidence scores are guidance, not certainty. Rejecting a wrong suggestion is cheaper than reverting a bad migration.

### Can I edit the generated script before running it?

Yes — that's part of the design. The output is plain SPARQL; tweak it in your editor, run it, version-control it.

## Operations

### Where are passwords stored?

There are no application-level passwords. Authentication happens at your reverse proxy. Fuseki has its own admin password — store it in your secret manager.

### How do I find out what version I'm running?

The about dialog (top-right user icon) shows app version and commit SHA. Or `curl https://<host>/api/version`.

### How do I report a security issue?

See [`SECURITY.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/SECURITY.md). Don't open public issues for security disclosures.

## Contributing

### How do I propose a feature?

Open a GitHub Discussion describing the use case before writing code. The maintainers will guide on scope and design fit.

### Where's the contribution policy?

[`.github/CONTRIBUTING.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/CONTRIBUTING.md). PRs need DCO sign-off, conventional-commit titles, and green CI.

### Can I get commit access?

Maintainership is granted after a sustained track record of merged PRs and active review. Open the discussion when you feel you're there.
