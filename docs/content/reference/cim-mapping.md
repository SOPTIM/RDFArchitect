---
title: CIM / CGMES Concepts Mapping
sidebar_position: 2
---

# CIM / CGMES Concepts Mapping

RDFArchitect uses a small, deliberately generic vocabulary so that it works equally well for CGMES profiles, ENTSO-E Network Code Profiles, internal extension profiles, and vendor-specific variants. This page maps that vocabulary onto the concepts you know from the CIM and CGMES worlds.

## Vocabulary map

| RDFArchitect term   | Typical CIM / CGMES meaning                                                                                                                                                       |
| ------------------- |-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Dataset**         | An in-memory workspace. Often one dataset per CGMES release you are working with.                                                                                                 |
| **Graph** / **Schema** | One *profile*. E.g. `EquipmentProfile`, `TopologyProfile`, `SteadyStateHypothesisProfile`, `StateVariablesProfile` — each becomes one graph.                                   |
| **Package**         | A UML package within a profile. Corresponds to the `uml:Package` hierarchy used in the CIM UML, e.g. `Core`, `Wires`, `Generation::Production`.                                   |
| **Class**           | An RDFS `Class`. For CGMES: every `IdentifiedObject` subtype, every `«CIMDatatype»`, every `«Compound»`, every `«enumeration»`.                                                   |
| **Attribute**       | An `rdf:Property` with a literal datatype — an RDFS datatype property. Cardinalities are carried as SHACL.                                                                        |
| **Association**     | An `rdf:Property` with a class as its range — an RDFS object property. Cardinalities and inverse roles carried as SHACL.                                                          |
| **Enum entry**      | An instance of an `«enumeration»` class. In SHACL this becomes an `sh:in` list on properties that point at the enum.                                                              |
| **Stereotype**      | A CIM UML stereotype applied to a class. Common values: `«enumeration»`, `«CIMDatatype»`, `«Primitive»`, `«Compound»`, `«NetworkCodeProfile»`, `«Package»`.                       |
| **Profile header**  | The `cim:Ontology` resource (or equivalent ENTSO-E / DCAT resource) placed at the top of a profile file, carrying title, version, conformsTo, keyword, license, description, etc. |
| **Namespace**       | The prefix/URI pair used to name resources. For CGMES: `cim:` → `http://iec.ch/TC57/CIM100#`, `entsoe:` → `http://entsoe.eu/CIM/SchemaExtension/3/1#`, etc.                       |
| **Snapshot**        | An immutable Fuseki-backed copy of a dataset shared via URL.                                                                                                                      |
| **Changelog**       | The edit history of a graph (= of a profile).                                                                                                                                     |
| **Migration script**| A SPARQL UPDATE that transforms instance data from source profile version to target profile version.                                                                              |

## A CGMES release in RDFArchitect

A typical CGMES release arrives as a zip containing several RDF/XML files — one per profile — plus accompanying SHACL files. In RDFArchitect that maps to:

- **One dataset** for the release (e.g. `cgmes-3.0.0`).
- **One graph per profile** inside the dataset:
  - `EquipmentProfile`
  - `TopologyProfile`
  - `SteadyStateHypothesisProfile`
  - `StateVariablesProfile`
  - …and so on.
- **One set of custom SHACL** per profile, imported alongside that profile's graph.
- **The profile header** of each file is editable in RDFArchitect as the ontology metadata.

Classes that appear in multiple profiles (for example, `Terminal`) are modelled once in whichever profile owns the definition, and referenced from the others via external-package references.

## An ENTSO-E Network Code Profile in RDFArchitect

Network Code Profiles (e.g. the CGMES-based profiles that underpin the various Network Codes — OPDE, OPDM, etc.) follow the same structure. Because Network Code Profiles are typically *extension profiles* on top of a base CGMES version, a common setup is:

- **Dataset** = one dataset per working context (e.g. `nc-opde-staging`, `nc-opde-released`).
- **Graph(s)** = the profile(s) being edited. Base CGMES packages are referenced as *external packages*, not re-imported — the classes are visible in the navigation tree and can be associated to, but cannot be modified from within this dataset.
- **Profile header** = the ENTSO-E ontology block (title, version, conformsTo, keyword, license, description, and the `Ontology.baseUriScheme` / `Ontology.versionIRI` fields). All of these are reachable from the "Add known fields" menu in the profile header editor.
- **SHACL** = the official release SHACL is imported as custom SHACL; any additional internal rules you maintain are authored in the same custom SHACL document. The generated SHACL is always also available for comparison.

## How RDFArchitect relates to the CIM UML

RDFArchitect is an **RDFS + SHACL editor**, not a UML editor. The connection to the CIM UML is that CGMES and ENTSO-E profiles are themselves expressed as RDFS — so when you edit a profile in RDFArchitect you are editing the same artefacts that the UML-based toolchains (Enterprise Architect, CIMTool, etc.) produce when they export to RDFS.

Practically this means:

- You can edit any CGMES or ENTSO-E profile in RDFArchitect without first going through a UML tool.
- You can produce a profile from scratch in RDFArchitect and it will be interoperable with UML-based downstream tooling.
- Stereotypes, packages, cardinalities, inheritance, enumerations and comments are preserved on import and on export.
- If your organisation still maintains the canonical profile in UML, RDFArchitect is a good *consumer* view — it is particularly useful for browsing, review, SHACL work, and migration planning without needing a UML tool licence.

## What RDFArchitect does not do

- It does not produce CGMES/CIMXML *instance* files. It edits *schemas* (profiles), not data. Instance-level tooling is a separate concern (see [`SOPTIM/OpenCGMES`](https://github.com/SOPTIM/OpenCGMES) for related tooling).
- It does not run SHACL validation against instance data. Generated and custom SHACL are produced and managed here, but the validation run is performed by an external SHACL engine (Apache Jena's SHACL validator, TopBraid, pySHACL, …).
- It does not maintain a UML model. If your workflow requires UML as the single source of truth, RDFArchitect is downstream of it.
