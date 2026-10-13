---
title: XMI Import Mapping
sidebar_position: 12
---

# XMI Import Mapping

What an Enterprise Architect XMI 2.5.1 export contains, how it maps onto the RDFArchitect model,
and where the limits are. This is the reference for the XMI import (RDFA-633) and the answer to
"what exactly do we support?".

Everything here was derived from two UML models and the RDFS released for them, published at
[ENTSO-E: CIM for grid models exchange](https://www.entsoe.eu/data/cim/cim-for-grid-models-exchange/#_ENTSO_E_CGMES_Extension_and_Profile):
**CGMES 2.4.15** and **CGMES 3.0** (which includes the Network Code profiles). Each is available
both as a profiles-only and as a full-model export, and the two differ in places; where that
matters it is said. Later references use the short names.

## Scope

| Capability | Status |
| --- | --- |
| Classes, attributes, associations, enumerations, packages, documentation | supported |
| Multiplicities, default and fixed values, stereotypes | supported |
| `cims:AssociationUsed`, `cims:inverseRoleName` | supported, derived |
| `cims:stereotype ofAggregate` | rule verified, needs a new field on `CIMAssociation` |
| Package nesting (`cims:belongsToCategory` between packages) | needs backend work, see [Package](#package-class-enumeration) |
| Profile namespace and prefix table | supported where the export carries one |
| Profile header | supported in both conventions |
| Class diagrams with node and edge-label positions | supported |
| Partial exports: missing metadata class, dangling references | rejected with a reason, see [limitation 3](#limitations) |
| OCL constraints, SHACL generated from them | not supported |
| EA styling, notes, ArchiMate, instance specifications | not supported |
| Node sizes, edge routing | not supported |

## How an export is structured

Three parts, joined over `xmi:id` / `xmi:idref`:

| Part | Holds |
| --- | --- |
| `<uml:Model>` | packages, classes, enumerations, associations, generalizations, attributes with type references and bounds, OCL constraints; also the UMLDI diagrams (`xmi:type="umldi:Diagram"`, `dc:bounds`, `dc:waypoint`) |
| `<xmi:Extension extender="Enterprise Architect">` | `<elements>`, `<connectors>`, `<diagrams>`: documentation, stereotypes, tagged values, role names, multiplicities, navigability, EA's own diagram list |
| stereotype applications, direct children of `<xmi:XMI>` | one element per application in EA's vendor namespaces (`<thecustomprofile:CIMDatatype base_Class="EAID_..."/>`). The only carrier of an element's **full** stereotype set, see [Stereotypes](#stereotypes) |

Neither of the first two is sufficient alone. An `<uml:Association>` always references both ends via
`<memberEnd>` but owns a varying number of them as `<ownedEnd>`, differing per export, so the
`<connector>` is the one to read. Diagrams need both parts too, see [Diagrams](#diagrams).

Parser facts: **windows-1252** encoding, and two `xmi:` namespaces, `omg.org/spec/XMI/20131001` for
the model and `omg.org/spec/XMI/20161101` for the embedded UML profile section. Handling only the
first misreads a few hundred elements, all inside `<uml:Profile>` blocks that
[do not map](#what-does-not-map) anyway.

## Identifying a profile

A `uml:Package` is not flagged as a profile by a stereotype or a tagged value. The marker is its
content: **a package is a profile exactly when it directly contains a metadata class**, a class
with at least one attribute whose name *begins with* one of `versionIRI`, `baseURI`, `entsoeURI`,
`europeanProfileURI`, `uri`, `namespaceURI` and whose `defaultValue` is an absolute IRI.

Two details decide whether the rule works: prefix matching is required (`EquipmentVersion` names
its attributes `baseURIcore`, `baseURIoperation`, `baseURIshortCircuit`), and `isStatic`/`isReadOnly`
must **not** be, because not every metadata class carries them. So stated it selects exactly the
released profiles of both models, excludes `DomainProfile` (whose `DomainVersion` has `baseUML`,
`version` and `date` but no profile URI), and on the 2.4.15 full model additionally selects
`Extension`, whose `ExtensionVersion` holds the extension namespace rather than a profile.

Matching on the *shape* of the data rather than on a known model matters, because the conventions
differ per release and a vendor model will use a fourth:

| Convention | Carrier | Profile URI from |
| --- | --- | --- |
| CGMES 2.4.15 | abstract class `<Profile>Version` | `baseURI` / `entsoeURI` |
| CIM100 / NC 3.0 | `«owl»` class `<Profile>-EU_Ontology` | `versionIRI` |
| canonical CIM packages | tagged values on the package | `uri`, `nsuri` |

`versionIRI` needs one transformation: strip the trailing version segment and append `#`, turning
`http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0` into `.../CoreEquipment-EU#`. CGMES 2.4.15 has no
profile namespace at all; its package resource lives in the model namespace, which is not a gap to
fill.

Several candidates are common: `EquipmentVersion` offers six, beside four IRI-valued attributes that
are not profile URIs (`differenceModelURI`, `modelDescriptionURI`, `namespaceRDF`, `namespaceUML`).
One becomes the namespace, the rest become ontology header fields, or stay on the version class for
2.4.15 where there is no header. If none is found, the namespace has to come from outside the
file.

### Which namespace a resource gets

The profile namespace and the namespace of the model elements are **not the same**. In a released
profile nearly every resource sits in the CIM namespace, the `Entsoe`/`European` ones in the
extension namespace, and only two (the package and the ontology) in the profile namespace.

| Namespace | Used for | Source |
| --- | --- | --- |
| model | classes, attributes, associations, enum entries | `namespaceUML` default (2.4.15); the prefix table's `nsuri:cim` (3.0), **not** the bare `nsuri` tag on the CIM package, which reads `.../CIM100.1.1.1#` and matches nothing |
| extension | elements stereotyped `Entsoe` or `European` | `ExtensionVersion.namespaceURI` (2.4.15 full model; **absent** from a profiles-only export -> asked for); `nsuri` on `EuropeanCIMExtensions` (3.0) |
| profile | the package resource and the ontology header | the profile URI above |

CGMES 3.0 carries a prefix table of `nsuri:*` / `nsprefix:*` pairs. It is a good starting set but
not authoritative: `dcterms` and `dc` carry a trailing `#` the release does not use, and `prof`
points at `http://www.w3.org/ns/dx/prof/#` where the release uses `.../CIM/prof-cim#`.

## What maps

### Graph

A profile package becomes one graph, whose content is **the package and all its sub-packages,
recursively**, **closed transitively over attribute types and superclasses, with the referenced
classifiers copied in**. The recursion is load-bearing: with a direct-children-only slice the
CGMES 2.4.15 profiles collapse to three classes each and none reproduces.

| `GraphDTO` field | Source |
| --- | --- |
| `uri` | a workspace-local graph name, as today. **Not** the profile namespace, which belongs on the package and ontology IRIs and the prefix binding |
| `label` | metadata `title`, otherwise the package name |
| `keyword` | metadata `keyword` (3.0) or `shortName` (2.4.15) |
| `description` | metadata `description`, otherwise the package documentation |
| `versionIris` | `versionIRI` (3.0) or `baseURI` / `entsoeURI` (2.4.15) |
| `versionInfo` | `versionInfo` (3.0); **no 2.4.15 source** |
| `ontologyHeader`, `profileClassIri`, `profileClassUuid` | derived from the convention the export uses |

These fields are **derived read-only output** of the graph query, not input: the importer writes the
underlying `owl:Ontology` node or `<Profile>Version` class and they follow. It must also write the
`cim` prefix, on which profile recognition depends silently ([limitation 8](#limitations)).

### Package, class, enumeration

| XMI source | RDFArchitect target |
| --- | --- |
| nested `uml:Package` | `CIMPackage`; name -> `rdfs:label`, documentation -> `rdfs:comment`. **The parent link cannot be stored**, see below |
| `packagedElement` `uml:Class` **whose extension `<element xmi:type>` is also `uml:Class`** | `CIMClass`; name -> `rdfs:label` + URI suffix |
| extension `properties/@documentation` | `rdfs:comment` |
| `generalization/@general` | `rdfs:subClassOf` |
| `properties/@isAbstract="false"` | stereotype `...UML#concrete`; EA models *abstract*, RDFArchitect *concrete*, the flag is inverted. **Not** applied to a class carrying any of the four datatype stereotypes, enumerations included |
| extension `properties/@stereotype` | `cims:stereotype` |
| owning package | `cims:belongsToCategory` |
| `uml:Enumeration` | class with stereotype `enumeration`, as a URI; see [what the importer writes](#output-shape) |
| `ownedLiteral` | `CIMEnumEntry` with stereotype `enum`; documentation from the matching extension `<attribute>`, joined by id |

**Package nesting needs backend work, though not where it looks.** The read path has it already:
`listInternalPackages` queries `cims:belongsToCategory` on the package itself and `PackageMapper`
maps it into `PackageDTO`, so a parent link written straight into the graph is stored and served.
What loses it is editing. `CIMUpdates.insertPackage` writes `rdf:type`, `rdfs:label`, the uuid and
`rdfs:comment` and no `cims:belongsToCategory`, and `AddPackageUseCase` says so in its Javadoc;
`replacePackage` is `deletePackage` plus `insertPackage`, so renaming a package drops its parent.
`CIMPackage.belongsToCategory` and `PackageDTO` exist and MapStruct maps them, so sending a parent
yields a success response and silent data loss, and the frontend package model has no parent field
either, leaving a hierarchy that is stored, served and invisible. Only one flat level survives a
round trip today, which happens to match CGMES 3.0, where a release declares a single
`cims:ClassCategory` per profile, but not 2.4.15, where it declares one per sub-package including
the foreign `#Package_DomainProfile`.

### Attribute

| XMI source | RDFArchitect target |
| --- | --- |
| `ownedAttribute` without `@association` | `CIMAttribute`; owning class -> `rdfs:domain` |
| type -> classifier stereotyped `CIMDatatype`, `Primitive` or **`Compound`** | `cims:dataType`, as `CIMSDataType.Type.PRIMITIVE` |
| type -> classifier that is an **enumeration** | `rdfs:range`, as `CIMSDataType.Type.RANGE` |
| type -> EA built-in `uml:PrimitiveType` (no stereotype) | `cims:dataType` pointing at the profile's matching `Primitive` class, **not** at an `xsd:` IRI; the releases always point at an in-graph class |
| `lowerValue` + `upperValue` | `cims:multiplicity` as `M:lower..upper`, `*` -> `n` |
| extension `documentation/@value` | `rdfs:comment` |
| extension `stereotype/@stereotype` | `cims:stereotype` |
| model `defaultValue` + **extension** `properties/@changeability="frozen"` | `cims:isFixed` |
| model `defaultValue` otherwise | `cims:isDefault` |

Two traps. `CIMSDataType.Type` *selects* the predicate rather than labelling it, so the stereotype
of the type decides between `cims:dataType` and `rdfs:range`. And the `isFixed` rule spans both
parts of the file, `<defaultValue>` in the model and `changeability` only in the extension; reading
the model alone makes every 2.4.15 version-class value a `cims:isDefault`, and since the profile
reader takes keyword and version IRIs from the *fixed* values, the profile then silently loses its
metadata.

Type resolution succeeds for all but a handful: some attributes use an EA built-in, a few have no
type at all, and a few reference a classifier present only in the extension.

Input defects to normalise rather than trust:

* **Two spellings of the unbounded upper bound.** `*` is the normal one, in an attribute's
  `upperValue` and in a connector end's `multiplicity` alike. `n` occurs only in the CGMES 3.0 full
  model, 329 times against 2489 `*`, and never in 2.4.15. A parser that maps `*` -> `n` and
  otherwise expects an integer breaks on those 329.
* a 2.4.15 profile URI default with a **leading space**, which RDFArchitect's `URI` rejects.
* a connector end with a malformed multiplicity (`"1.."`).

### Association

Both halves of a `CIMAssociationPair` come from the EA `<connector>`, and both
`properties/@ea_type="Association"` **and `"Aggregation"`** count; dropping the latter leaves
profiles short of their release.

**Each end produces the half owned by the class at the *other* end.** The end
`role/@name="ConverterDCSides"` on `ACDCConverter`, opposite `Terminal`, becomes
`Terminal.ConverterDCSides`, with domain `Terminal` and range `ACDCConverter`. Getting the cross-over
backwards points every association the wrong way.

| XMI source, read at one connector end | Target for the half this end names |
| --- | --- |
| the **opposite** end's `model/@name` | `rdfs:domain` |
| this end's `model/@name` | `rdfs:range` |
| this end's `role/@name` | `rdfs:label` + URI suffix |
| the opposite end's `role/@name`, composed as `<model ns><opposite domain>.<opposite role>` | `cims:inverseRoleName` |
| this end's `type/@multiplicity` | `cims:multiplicity`, **verbatim** with an `M:` prefix and `*` -> `n`. Do not expand to two bounds: the releases keep `M:1` and `M:1..1` apart |
| this end's `documentation/@value` | `rdfs:comment` |
| this end's `style/@value`, entry `Navigable=` | `cims:AssociationUsed`: `Navigable` -> `Yes`, `Non-Navigable` and `Unspecified` -> `No` |
| this end's `type/@aggregation` `shared` or `composite` | `cims:stereotype ofAggregate` on the **opposite** half |

The navigability locator matters: EA writes it twice and the two disagree. The `;`-separated
`style/@value` reproduces the release exactly ([validation](#validation)), while the obvious-looking
`modifiers/@isNavigable` often does not. `properties/@direction` is an equally exact second carrier for
cross-checking, but has **four** values: `Bi-Directional` besides the three obvious ones, the only
case where both ends read `Navigable`. `Navigable=Unspecified` is written explicitly; it maps to
`No` and is not an absent value.

### Ontology header

The 3.0 metadata class declares an attribute for **every** field of `KnownOntologyFields`. Two
carry no value in practice: `issued` never has a default and `backwardCompatibleWith` almost never.
`conformsTo` defaults are **pipe-separated multi-values** that the release emits as separate
triples, so they have to be split; the class also has an `imports` attribute RDFArchitect has no
field for.

CGMES 2.4.15 has no ontology header at all. The values live in the `<Profile>Version` class and
stay there, as the released RDFS does. A profile without a metadata class simply has no header.

### Stereotypes

| Tier | Values | Effect |
| --- | --- | --- |
| Semantic | `CIMDatatype`, `Primitive`, `Compound`, `enumeration` | `DatatypeStereotypes.isDatatype()`; drives validation, SHACL generation and rendering |
| | `concrete`, `attribute`, `enum` | the Abstract checkbox; resource typing of attributes and enum entries |
| Documentation | `ShortCircuit`, `Description`, `Operation`, `European`, `Entsoe` | one section each in the AsciiDoc and HTML profile export |
| Everything else | any string without spaces | stored, rendered as `«value»`, exported, round-tripped, editable |

**Every stereotype is imported unchanged, with two caveats.** First a locator problem:
`properties/@stereotype` and `stereotype/@stereotype` hold exactly one value, while the releases do
emit co-occurring stereotypes (`European` + `deprecated`, `Operation` + `ShortCircuit`), which
those two attributes lose. The **stereotype-application section** at the root of the file carries
the complete set per element without string parsing, so that is the carrier to read; filter out
`GUIDBasedOn` and `IsBasedOn`, which are UML bookkeeping.

Second, three values must not be passed through: `abstract`, which neither release emits and
RDFArchitect expresses through the inverted `concrete` flag, and `attribute` and `enum`, which
`CIMUpdates` writes automatically, so supplying them duplicates the first and has the second
silently replaced.

Tier 4 is a supported state, not a fallback: the releases keep values like `NC`, `profcim`,
`European` and `deprecated` too, and CGMES 3.0 uses a few dozen names RDFArchitect does not model.
Promoting one to the documentation tier later is a one-line change in
`ProfileDocumentationStructure.STEREOTYPES`.

`ofAggregate` **does not survive a round trip**, the same way a package parent does not:
`CIMAssociation` has no stereotype field, `CIMUpdates` writes `cims:stereotype` for classes,
attributes and enum entries only, and the name appears nowhere in the codebase. Written straight
into the graph it is stored and exported, but `replaceAssociations` deletes every triple of a
class's association halves and rewrites them from the DTO, so the first save of either end loses
it. Supporting it means a new field on the DTO, its mapper, the select and update queries and the
frontend model. The derivation rule is verified; only the storage is missing.

### Diagrams

An imported diagram is a `CustomDiagramDTO` plus `dl` data. Join UMLDI to EA's list on
`umldi:Diagram@xmi:id` to `<diagram>@xmi:id`, and `@modelElement` to `<element>@subject`.

| XMI source | RDFArchitect target |
| --- | --- |
| `umldi:Diagram` of type `UMLClassDiagram` | one custom diagram; `UMLObjectDiagram` and `UMLPackageDiagram` are out of scope |
| `UMLClassifierShape` + `dc:bounds` x, y | `DiagramObject` with `belongsToDiagramObjectStyle = CLASS`, `ClassPositionDTO` position |
| EA `<diagram>/<elements>/<element>@seqno` | `ClassPositionDTO.zPosition`; **not in UMLDI**, which has no `seqno` anywhere |
| `UMLMultiplicityLabel` + `dc:bounds` | `LabelPositionDTO`, kind `multiplicity` |
| `UMLAssociationEndLabel` + `dc:bounds` | `LabelPositionDTO`, kind `associationLabel` |

`UMLClassifierShape` is the right filter and needs no widening. Neither neighbouring shape type
carries a classifier: `UMLShape` is used exclusively for notes, boundaries and text, and every
`UMLCompartmentableShape` points at an `EAPK_` id, so they are package shapes, which have no
counterpart because a package here *is* a diagram rather than a node on one. `UMLTypedElementLabel`
(3.0 only) has no target either.

A label's `@modelElement` does **not** resolve like a shape's. It is the `xmi:id` of an
`<ownedEnd>` / `<ownedAttribute>` in the model part, appearing in neither `<element>@subject` nor
`<connector>@xmi:idref`. The chain is: label `@modelElement` -> that end's `@association` -> the
connector of the same id -> its source or target end -> cross over to the half that end names.

Only the two label kinds travel through `LabelPositionDTO`; class positions go through
`ClassPositionDTO`, and the label update service silently drops a `LabelPositionDTO` of kind
`class`.

Two properties of the source data bear on how much of this is worth importing. **Volume**: a
full-model export carries over a thousand diagrams, most of which have nothing to do with any one
profile. **Containment**: a diagram can draw classes from several profiles, which RDFArchitect
supports as a cross-profile diagram, so whether a diagram is complete has to be judged against
everything an import creates, not against a single graph. In practice a profile's own diagrams are
nearly always self-contained.

What arrives is thinner than what EA shows: about three quarters of the node entries resolve to a
real classifier, the rest being notes, boundaries and package shapes with no counterpart, and the
layout was drawn around them.

## What the importer writes {#output-shape}

The tables above say where each value is *read*. Several triples are mandatory regardless of the
export's content, and omitting them produces a graph that is stored and partly or wholly invisible.

| Element | Invariant triples, beyond the mapped values |
| --- | --- |
| profile package | `rdf:type cims:ClassCategory`, `rdfs:label`, `rdfa:uuid`. URI `<profile ns>Package_<Name>` |
| class | `rdf:type rdfs:Class`, `rdfa:uuid`, `cims:belongsToCategory`. URI `<model ns><Name>` |
| attribute | `rdf:type rdf:Property`, `rdfa:uuid`, `rdfs:domain`, **`cims:stereotype <...UML#attribute>`**, `cims:multiplicity`. URI `<model ns><Class>.<name>` |
| association half | `rdf:type rdf:Property`, `rdfa:uuid`, `rdfs:domain`, `rdfs:range`, `cims:multiplicity`, `cims:inverseRoleName`, `cims:AssociationUsed`. URI `<model ns><Domain>.<role>` |
| enumeration | like a class, plus `cims:stereotype <...UML#enumeration>` |
| enum entry | `rdf:type <the enumeration class>`, `rdfa:uuid`, `cims:stereotype "enum"`. URI `<model ns><Enum>.<literal>` |
| ontology (3.0 only) | `rdf:type owl:Ontology`; the IRI is fixed as `<namespace>Ontology` and cannot be chosen |

* **`rdf:type` gates everything.** `GraphUtils.enhanceWithUUIDs` assigns `rdfa:uuid` only to typed
  resources and the readers filter on `rdfa:uuid`; an untyped resource is stored and never shown.
* **The `attribute` stereotype is mandatory.** `findUndisplayableProperties` reports any
  domain-bound `rdf:Property` carrying neither it nor `cims:AssociationUsed` as "stored but
  invisible".
* **Literal or IRI is not free.** `attribute`, `concrete`, `enumeration` and `ofAggregate` are
  `...NonStandard/UML#` IRIs; `enum`, `CIMDatatype`, `Primitive`, `Compound`, `European`, `Entsoe`
  and every unmodelled value are plain literals. `cims:multiplicity` and `cims:inverseRoleName` are
  absolute IRIs. The wrong form breaks datatype checks, SHACL and rendering silently.

## What does not map

| XMI content | Why there is no target |
| --- | --- |
| OCL constraints (`ownedRule` -> `uml:OpaqueExpression`) | constraints are SHACL, generated from the model; no automatic translation |
| `uml:Dependency` other than `«IsBasedOn»` | package dependencies, `«imports»`, `«use»` have no counterpart |
| `«IsBasedOn»` / `GUIDBasedOn` | links a profile element to its canonical CIM element; no provenance field |
| `uml:InstanceSpecification`, `uml:Artifact`, `uml:Usage` | no instance-level or deployment concept |
| `uml:Stereotype` / `uml:Extension` profile definitions | no UML profile model, only stereotype literals |
| EA styling (`style`, `styleex`, `appearance`, colours, swimlanes) | not modelled, and not desirable to model |
| Notes, `uml:Boundary`, `NoteLink`, `ownedComment` | diagram annotations; class documentation comes from `properties/@documentation` |
| ArchiMate elements | not UML class model content |
| Node sizes, edge waypoints | `dl` stores positions only; edges are routed by SvelteFlow |
| EA `xrefs` custom properties (for example `isID`) | a proprietary string DSL, no field |
| `project/@author`, `created`, `modified`, `status` | RDFArchitect keeps its own changelog |

## What RDFArchitect stores that the export cannot supply

| Field | Why |
| --- | --- |
| `RDFSLabel.lang` | the export carries no language information anywhere; the DTO mappers default it to `en` |
| `RDFSComment.format` | comments are plain text; normalisation to `xsd:string` also discards any language tag |
| `dct:issued` | declared on every metadata class but never valued; the timestamp comes from the generator |
| `owl:backwardCompatibleWith` | declared on the metadata class, almost never valued |
| `Diagram.orientation` | EA has no equivalent; RDFArchitect writes `NEGATIVE` everywhere today |
| extension namespace (2.4.15 profiles-only export) | present in the full-model export, absent here although the release uses it |
| everything from the metadata class, if the export omits it | a partial export is valid input |

The export has a `license` attribute with no RDFArchitect field; it would go into `dct:rights` or
be dropped. Object UUIDs are generated, but EA ids *are* UUIDs (`ea_guid="{64EB5856-...}"`), so they
could seed stable identities across re-imports, which is worth deciding when the importer is
written. Changelog entries, snapshots, generated SHACL and migration scripts come from using
RDFArchitect, not from reading a profile.

## Validation {#validation}

The rules above were derived by building a graph from the XMI and comparing it against the RDFS
released for the same profile, which is what RDFArchitect imports today.

**18 of the 19 CGMES profiles reproduce exactly**: same classes, attributes, associations and enum
entries, nothing missing, nothing extra. The two exceptions are CGMES 3.0 Dynamics and the Network
Code profiles, where the UML and the release differ in both directions and the differences track
release dates rather than any selection rule. **Treat those as not reproducible** until a matching
UML revision is available.

The comparison is on those four sets of names, not on every triple, so `rdfs:subClassOf`,
`cims:stereotype`, `rdfs:comment`, `cims:isFixed`/`isDefault`, `rdfs:label`, the package hierarchy
and the `cims:dataType`-versus-`rdfs:range` choice are invisible to it. Geographical Location
reproduces exactly on names while the datatype rule got four of its attributes wrong, which is why
the `Compound` branch exists. "Reproduces exactly" means the *element selection* is right, not that
every mapping rule is.

Three rules only the comparison could settle: the closure is required for 2.4.15 and a no-op for
3.0, but only if the metadata class is lifted first; that metadata class stays a class in 2.4.15
and becomes the ontology header in 3.0; and of the two places EA records navigability, only
`style/@value` reproduces the release, while `modifiers/@isNavigable` disagrees often enough to be
unusable. Derived multiplicities and `cims:inverseRoleName` match the release on every profile that
reproduces.

## Limitations

| # | Limitation |
| --- | --- |
| 1 | **A CGMES 2.4.15 profile is not self-contained**: its attributes reference datatypes that live in a shared `DomainProfile`. The closure rule handles it; without it the graph is full of unresolvable datatypes. CGMES 3.0 packages do not have the problem |
| 2 | **A package can carry several namespaces.** `EquipmentVersion` defines `baseURIcore`, `baseURIoperation` and `baseURIshortCircuit`, correlating with the attribute stereotypes `Operation` and `ShortCircuit`. "One package = one graph URI" does not hold in general |
| 3 | **A partial export is valid input**, and dangling references occur even in complete exports: type references dangle, superclasses are unresolvable, the metadata class may be missing entirely, in which case the file carries no namespace, version or header at all. Such a file is refused with a reason rather than half imported ([decision 7](#decisions)). That is a different matter from the defects the releases themselves carry, which are normalised ([Attribute](#attribute)) |
| 4 | **File size and file type.** A full-model export reaches 100 MB and needs a streaming parser; the progress reporting it also needs exists already, in `ImportJob` and `ImportProgressListener`. Two concrete blockers: the server-wide multipart limit is 50 MB, and the frontend accepts only `.rdf`, `.ttl`, `.nt` and `.zip`, so XMI has to be added on every upload path, not just drag-and-drop. `supportedRDFMediaTypes` is not that place — the export dialogs and the user settings read the same list, where XMI would show up as an export format. The import needs a list of its own |
| 5 | **OCL bodies are double-escaped.** The file literally contains `self.BaseVoltage &amp;lt;&amp;gt; null` |
| 6 | **`uml:Boundary` and `uml:Text` are exported as `uml:Class`**, many of them nameless. The only discriminator is the extension's `<element xmi:type>`, and one extension entry carries no `xmi:type` at all, so the lookup must tolerate its absence. A name check is still needed as a backstop: the 2.4.15 Dynamics profile yields `dynamics model` and `static power system model`, which `URI` rejects for the spaces |
| 7 | **A class can have several `<generalization>` elements**, while `CIMClass` holds a single `superClass` and a validation rule flags multiple inheritance. Which one wins is undecided |
| 8 | **Profile recognition fails silently, with three preconditions.** The graph query wraps the graph as a CIM profile and swallows the resulting `IllegalArgumentException`, so an unmet precondition costs `label`, `keyword`, `description`, `versionInfo`, `versionIris`, `ontologyHeader`, `profileClassIri` and `profileClassUuid` without an error. (a) A `cim` prefix must be bound, (b) to one of the three namespaces the backend knows (CIM16, CIM100, UCAIUG), which also bounds the "a vendor model will use a fourth convention" premise. (c) For CGMES 2.4.15 the graph must additionally contain a `...Version.shortName` **and** a `...Version.entsoeURI*` or `baseURI*` as **fixed** values, exactly what a partial export may omit. The existing RDF import has a prefix subsystem for (a) and (b); an XMI import brings a whole prefix table and inherits the same collision problem |

## Decisions {#decisions}

1. **`cims:AssociationUsed`**: `Navigable` -> `Yes`, everything else -> `No`. Measured, not chosen.
2. **Granularity**: one profile package per graph, closed transitively over attribute types and
   superclasses with the referenced classifiers copied in. Referenced packages do *not* become
   separate graphs.
3. **Several namespace candidates**: one graph, stereotypes preserved, surplus namespaces into the
   ontology header. Splitting the Equipment profile along its sub-profile stereotypes is not done:
   the release ships it as four *combinations* formed from exactly those stereotypes.
4. **Unknown stereotypes**: imported unchanged, except `abstract`, `attribute` and `enum`.
5. **Write path**: the importer builds the graph and hands it over whole, the way the RDF import
   does, rather than driving the use cases a hand edit drives. The facade is the *reading*
   contract: an XMI-backed `ICIMModelFacade` lets the workspace validation judge an export before
   it is imported, and lets the comparison below run facade against facade. It is not the writing
   contract, because `ICIMClassCategory` carries no parent and `ICIMAssociation` no stereotypes.
6. **One import run**: scan the file, offer the profiles it holds, import the chosen ones into one
   graph each, always into a new workspace.
7. **Partial exports**: refused with a reason rather than degraded. The defects the releases
   themselves carry are normalised and are not what this covers.
8. **Prefixes**: a new workspace contests nothing, so the prefix negotiation the RDF import
   performs is skipped rather than removed. The CGMES 3.0 table is taken over with its three wrong
   entries corrected; 2.4.15 carries no table, so its prefixes are derived; `cim` is checked
   against the three namespaces the backend knows instead of trusted.

Still open: how diagrams are selected and whether they are imported at all by default; where the
2.4.15 extension namespace comes from, since it is not in a profiles-only export; which
generalization wins; whether EA ids seed object UUIDs; and the package hierarchy together with
`ofAggregate`, which no round trip survives today.
