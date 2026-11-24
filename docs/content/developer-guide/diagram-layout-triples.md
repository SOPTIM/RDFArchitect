---
title: Diagram Layout Triples
sidebar_position: 11
---

# Diagram Layout Triples

RDFArchitect stores the layout of its diagrams as RDF triples that follow the Diagram Layout profile of CGMES 3.0. This page documents which layout resources RDFArchitect writes for each element of a diagram, which triples they consist of, and where they deviate from the profile. The profile itself, its classes and the parts RDFArchitect does not use are introduced in the Reference under [Diagram Layout (DL) Profile](/reference/diagram-layout).

## Namespaces

The triples of the layout use three namespaces:

| Prefix | Namespace                                     | Contains                                                                 |
|--------|-----------------------------------------------|--------------------------------------------------------------------------|
| `cim`  | `http://iec.ch/TC57/CIM100#`                  | the classes and properties of the profile, in their CGMES 3.0 version    |
| `rdfa` | `https://rdf-architect.soptim.de#`            | `rdfa:DiagramObject.otherClass`, the one property RDFArchitect adds (see [Deviations from the profile](#rdfadiagramobjectotherclass)) |
| `rdf`  | `http://www.w3.org/1999/02/22-rdf-syntax-ns#` | `rdf:type`                                                               |

## Identifiers

Every resource of the layout is identified by its **mRID** (master resource identifier), which RDFArchitect sets to a UUID, as the profile recommends. The IRI of the resource is that UUID with a leading underscore, for example `_0c5f2b8e-3c1d-4b8a-9f43-2d6e1a7b9c10`.

References from the layout to a schema take the same form. `DiagramObject.IdentifiedObject` points to `_<uuid>`, where `<uuid>` is the UUID RDFArchitect gives every resource of a schema (`http://example.org#uuid`), and not the IRI of the class. That way a class keeps its layout when its IRI changes, for example when it is renamed. Workspace diagrams and the cross-profile diagram are the exception, see [Diagram types](#diagram-types).

:::note Abbreviations in the code
The code and the placeholders under [Triples per element](#triples-per-element) abbreviate the classes of the profile:

| Abbreviation | Class                | Example in the code |
|--------------|----------------------|---------------------|
| DO           | `DiagramObject`      | `edgeDO`            |
| DOP          | `DiagramObjectPoint` | `fetchDOPsForDO`    |
| IO           | `IdentifiedObject`   | `ioMRID`            |
:::

## Elements of a diagram

Not everything a diagram shows has layout data. Classes, the edges between them and the labels at the ends of an association can be placed by hand, so each of them gets a `DiagramObject` of its own. Attributes and enum entries are drawn inside the box of their class and have no layout of their own. Texts, such as the label of a class or the multiplicity of an association end, are never stored in the layout, because they always come from the schema.

### Overview

The table lists the elements of a diagram that have layout data, the layout resources RDFArchitect writes for them and the style their `DiagramObject` gets. The style is what tells whether a `DiagramObject` is a class, an edge or a label.

| Element in RDFArchitect | Layout resources                                                         | Style              |
|-------------------------|--------------------------------------------------------------------------|--------------------|
| Diagram                 | `Diagram`                                                                | –                  |
| Class                   | `DiagramObject`, one `DiagramObjectPoint`, one `DiagramObjectGluePoint` | `class`            |
| Inheritance edge        | `DiagramObject`, its edge points                                         | `inheritance`      |
| Association edge        | `DiagramObject`, its edge points                                         | `association`      |
| Association label       | `DiagramObject`, one `DiagramObjectPoint`                                | `associationLabel` |
| Multiplicity label      | `DiagramObject`, one `DiagramObjectPoint`                                | `multiplicity`     |

### Diagram types

Every diagram RDFArchitect shows has a `Diagram` of its own. A class that is shown in several diagrams has a `DiagramObject` in each of them, so its layout is kept separately for every diagram.

- **Package diagrams** show the classes of one package of a schema. The mRID of the `Diagram` is the UUID of the package. Classes without a package are shown in the diagram of the default package, whose `Diagram` has a UUID of its own. The layout is stored with the schema.
- **Schema diagrams** show the classes of one schema that the user picked. The mRID of the `Diagram` is the UUID of the schema diagram, and the layout is stored with the schema.
- **Workspace diagrams** and the **cross-profile diagram** show classes of several schemas, and their layout is stored with the workspace. Since one class can be defined in several schemas, these diagrams cannot reference classes and association ends by the UUID they have in one schema. They use a UUID derived from the IRI instead (a name-based UUID of the IRI), which is the same in every schema. The `Diagram` of the cross-profile diagram is named `CrossProfileDiagram`.

### Bend points and docking points

The [User Guide](/user-guide/workspace-and-importing) calls the points of an edge its **edge points**. **Bend points** are the corners of the edge between its two ends, and **docking points** pin an end of the edge to the border of its class. In the layout, every edge point is a `DiagramObjectPoint` of the `DiagramObject` of the edge. A docking point references the glue point of its class, while a bend point references no glue point at all.

## Triples per element

The templates below show the triples RDFArchitect writes for each element. Values in braces are placeholders: `{…MRID}` stands for the mRID of a resource of the layout, and `{…UUID}` for the UUID of a resource of the schema. Triples marked `# required` are always written, triples marked `# optional` only in some cases.

### Diagram

```turtle
<_{diagramMRID}>  rdf:type                   cim:Diagram ;                    # required
                  cim:IdentifiedObject.name  "{diagramName}" ;                # required
                  cim:Diagram.orientation    cim:OrientationKind.negative .   # required
```

- `{diagramMRID}` depends on the type of the diagram, see [Diagram types](#diagram-types).
- `{diagramName}` is the label of the package, or `CrossProfileDiagram` for the cross-profile diagram.
- The orientation is always `negative`, so x grows to the right and y downwards, just like the coordinates of the editor.

### Diagram object styles

```turtle
<_{styleMRID}>  rdf:type                   cim:DiagramObjectStyle ;   # required
                cim:IdentifiedObject.name  "{styleName}" .            # required
```

- There are currently five styles: `class`, `inheritance`, `association`, `associationLabel` and `multiplicity`.
- The mRID of a style is derived from its name (a name-based UUID of the name), so it is the same in every layout. Each style is written once per layout and shared by all `DiagramObject`s of its kind.

### Class

```turtle
<_{classDOMRID}>     rdf:type                                      cim:DiagramObject ;            # required
                     cim:IdentifiedObject.name                     "{classLabel}" ;               # required
                     cim:DiagramObject.Diagram                     <_{diagramMRID}> ;             # required
                     cim:DiagramObject.IdentifiedObject            <_{classUUID}> ;               # required
                     cim:DiagramObject.DiagramObjectStyle          <_{classStyleMRID}> .          # required

<_{classDOPMRID}>    rdf:type                                      cim:DiagramObjectPoint ;       # required
                     cim:DiagramObjectPoint.DiagramObject          <_{classDOMRID}> ;             # required
                     cim:DiagramObjectPoint.xPosition              "{x}" ;                        # required
                     cim:DiagramObjectPoint.yPosition              "{y}" ;                        # required
                     cim:DiagramObjectPoint.zPosition              "{z}" ;                        # required
                     cim:DiagramObjectPoint.DiagramObjectGluePoint <_{gluePointMRID}> .           # required

<_{gluePointMRID}>   rdf:type                                      cim:DiagramObjectGluePoint .   # required
```

- A class has exactly one `DiagramObject` per diagram, and that `DiagramObject` has exactly one point, which marks the top left corner of the box of the class.
- `zPosition` is the layer of the class: a class with a higher value is drawn above a class with a lower one. A class that is added to a diagram gets the highest value of the diagram plus one, and a point without `zPosition` counts as 0.
- Every class has a glue point of its own, and the docking points of its edges reference the same glue point, see [Edge points](#edge-points).

### Inheritance edge

```turtle
<_{edgeDOMRID}>  rdf:type                              cim:DiagramObject ;                     # required
                 cim:IdentifiedObject.name             "{subClassLabel} {superClassLabel}" ;  # required
                 cim:DiagramObject.Diagram             <_{diagramMRID}> ;                      # required
                 cim:DiagramObject.IdentifiedObject    <_{subClassUUID}> ;                     # required
                 rdfa:DiagramObject.otherClass         <_{superClassUUID}> ;                   # required
                 cim:DiagramObject.DiagramObjectStyle  <_{inheritanceStyleMRID}> .             # required
```

- Every edge between two classes of a diagram has one `DiagramObject`, which exists as soon as both classes have layout data in the diagram.
- The identified object is the sub class, and `rdfa:DiagramObject.otherClass` is the super class. The cross-profile diagram can show a class with several super classes; such a class then has one edge per super class, and `otherClass` tells them apart (see [Deviations from the profile](#rdfadiagramobjectotherclass)).
- The name only makes the layout easier to read, since nothing looks an edge up by its name.

### Association edge

```turtle
<_{edgeDOMRID}>  rdf:type                              cim:DiagramObject ;                       # required
                 cim:IdentifiedObject.name             "{classLabel} {otherClassLabel}" ;       # required
                 cim:DiagramObject.Diagram             <_{diagramMRID}> ;                        # required
                 cim:DiagramObject.IdentifiedObject    <_{associationEndUUID}> ;                 # required
                 rdfa:DiagramObject.otherClass         <_{inverseAssociationEndUUID}> ;          # required
                 cim:DiagramObject.DiagramObjectStyle  <_{associationStyleMRID}> .               # required
```

- An association consists of two association ends, one `rdf:Property` for each direction. Both ends share one edge and therefore one `DiagramObject`.
- The identified object and `otherClass` are the two association ends, not the classes. Which of the two ends becomes the identified object is fixed: it is the end with the smaller UUID in the order of `java.util.UUID`. That way the edge is found from either end.
- The name holds the labels of the two classes the association connects. Their order is not defined: it depends on which of the two classes RDFArchitect happens to look at first, and says nothing about the identified end or the direction of the `sequenceNumber`.

### Edge points

```turtle
<_{edgeDOPMRID}>  rdf:type                                      cim:DiagramObjectPoint ;   # required
                  cim:DiagramObjectPoint.DiagramObject          <_{edgeDOMRID}> ;          # required
                  cim:DiagramObjectPoint.xPosition              "{x}" ;                    # required
                  cim:DiagramObjectPoint.yPosition              "{y}" ;                    # required
                  cim:DiagramObjectPoint.sequenceNumber         "{n}" ;                    # required
                  cim:DiagramObjectPoint.DiagramObjectGluePoint <_{gluePointMRID}> .       # optional
```

- An edge without points is drawn as a straight line between its two classes.
- The `sequenceNumber` of a point is its position along the edge. The numbering starts at the end of the edge where the identified object of the `DiagramObject` is: for an inheritance edge that is the sub class, and for an association edge the class the identified association end belongs to. The values are sorted as numbers and not as text, so a point with `10` comes after a point with `2`.
- Only the first and the last point of an edge can be docking points. A docking point references the glue point of the class at its end, while a bend point has no glue point.
- The two docking points of a self-association reference the same glue point, because both ends of the edge belong to the same class.
- Edge points have no `zPosition`.

### Labels

```turtle
<_{labelDOMRID}>   rdf:type                              cim:DiagramObject ;          # required
                   cim:IdentifiedObject.name             "{styleName}" ;              # required
                   cim:DiagramObject.Diagram             <_{diagramMRID}> ;           # required
                   cim:DiagramObject.IdentifiedObject    <_{associationEndUUID}> ;    # required
                   cim:DiagramObject.DiagramObjectStyle  <_{labelStyleMRID}> .        # required

<_{labelDOPMRID}>  rdf:type                              cim:DiagramObjectPoint ;     # required
                   cim:DiagramObjectPoint.DiagramObject  <_{labelDOMRID}> ;           # required
                   cim:DiagramObjectPoint.xPosition      "{x}" ;                      # required
                   cim:DiagramObjectPoint.yPosition      "{y}" ;                      # required
                   cim:DiagramObjectPoint.zPosition      "{z}" .                      # required
```

- Every association end has two labels: its name, with the style `associationLabel`, and its multiplicity, with the style `multiplicity`. Both labels are drawn at the class the association end points to.
- Within a diagram, a label is identified by its association end together with its style.
- A label only gets a `DiagramObject` once it has been moved by hand. Without one, the label is drawn at its default place, and resetting a label deletes its `DiagramObject` again.
- The name of the `DiagramObject` is the name of its style. The `zPosition` is written, but it is not used for labels.

## Deviations from the profile

This chapter lists every place where RDFArchitect deviates from the Diagram Layout profile. Whenever the way RDFArchitect uses the profile changes, this list has to be updated as well.

### `rdfa:DiagramObject.otherClass`

RDFArchitect adds a property of its own to `DiagramObject`, which references the second object an edge connects. The profile has no such property, because a `DiagramObject` can reference only one identified object.

An edge connects two objects, though, and one reference is not enough to identify it. With only the sub class, the edges of a class with several super classes could not be told apart, and with only one association end, an association edge could not be found from both of its ends. Since no construct of the profile can express a second reference, RDFArchitect added `rdfa:DiagramObject.otherClass`.

### `sequenceNumber` starts at 0

RDFArchitect numbers the edge points of an edge starting at 0. The profile, however, requires `sequenceNumber` to be a positive value, and the official SHACL constraints of the profile reject 0.

### Untyped literals

RDFArchitect writes `xPosition`, `yPosition`, `zPosition` and `sequenceNumber` as plain literals without a datatype. The profile types the coordinates as `Float` and `sequenceNumber` as `Integer`, and its SHACL constraints check for `xsd:float` and `xsd:integer`.

### No `IdentifiedObject.mRID` triple

RDFArchitect writes the mRID of a resource only as its IRI and writes no `cim:IdentifiedObject.mRID` triple. The profile requires exactly one `IdentifiedObject.mRID` for every `Diagram`, `DiagramObject` and `DiagramObjectStyle`, and its SHACL constraints check for this triple.

### Glue points with a single point

RDFArchitect gives every class a glue point, even when no docking point references it yet. In that case the glue point groups only the point of the class, whereas the profile expects a glue point to group two or more points.

### The point of a class is its top left corner

The point of a class marks the top left corner of its box, and RDFArchitect writes no `offsetX` or `offsetY`. The profile, however, takes a single point as the centre of the icon, unless `offsetX` and `offsetY` say otherwise.

## Further reading

- [Diagram Layout (DL) Profile](/reference/diagram-layout) introduces the profile and the parts of it RDFArchitect does not use.
- [RDF, SHACL and SPARQL](./rdf-shacl-sparql#diagram-layout) describes the code that reads and writes the layout.
- `backend/src/test/java/org/rdfarchitect/dl/testdata/diagramLayout.ttl` is a complete layout used by the tests. Jena cannot read relative IRIs from Turtle, so the resources in this file are written as `urn:mrid:<uuid>` and renamed when the file is loaded.
- The official SHACL constraints of the profile are in `external/entsoe-application-profiles-library/CGMES/CurrentRelease/SHACL/RDF/`, in the files `61970-600-2_DiagramLayout-AP-Con-Simple-SHACL.rdf` and `61970-301_DiagramLayout-AP-Con-Complex-SHACL.rdf`.
