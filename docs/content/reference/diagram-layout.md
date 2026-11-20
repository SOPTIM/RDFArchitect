---
title: Diagram Layout (DL) Profile
sidebar_position: 3
---

# Diagram Layout (DL) Profile

## Introduction

### About this page

RDFArchitect stores the layout of its diagrams as RDF triples that follow the Diagram Layout profile of CGMES 3.0. The layout records where the classes sit, which course the edges take and where labels were moved to. This page introduces the profile and explains the parts of it that RDFArchitect uses. It is meant for developers who are new to the profile. Which triples RDFArchitect writes for each element of a diagram is documented in the Developer Guide under [Diagram Layout Triples](/developer-guide/diagram-layout-triples).

### The Diagram Layout profile

The Diagram Layout profile (DL, IEC 61970-453) is the CGMES profile for exchanging diagrams. It describes where the objects of a model are placed in a diagram, but not what they look like. Every object that is drawn is a `DiagramObject` with one or more points, and it references the object of the model it represents, which the profile calls its domain object. The system that reads the layout draws the objects in its own way, but keeps their positions.

In CGMES, the domain objects are the parts of a power system network, such as lines and breakers. In RDFArchitect, they are the classes and association ends of a schema.

### Version

RDFArchitect follows the Diagram Layout profile of CGMES 3.0, whose classes and properties are defined in the namespace `http://iec.ch/TC57/CIM100#`. The vocabulary of the profile is the file `61970-600-2_DiagramLayout-AP-Voc-RDFS2020.rdf` in `external/entsoe-application-profiles-library/CGMES/CurrentRelease/RDFS/`.

## The profile

### Overview

The figure shows the whole profile, drawn with RDFArchitect. The sections below describe the classes RDFArchitect uses, in the words of the profile, and then list the parts it does not use.

![The Diagram Layout profile of CGMES 3.0](/img/screenshots/diagram-layout-profile.png)

### The classes RDFArchitect uses

The profile defines every association in both directions, but marks only one of the two directions as used in an exchange. Only that direction is written as a triple, so it is the only one listed below. Multiplicities and datatypes are those of the profile.

#### `Diagram`

The diagram being exchanged. Its coordinate system is a standard Cartesian coordinate system, whose orientation is defined by the orientation attribute.

- `Diagram.orientation` (`OrientationKind`, 1..1): the orientation of the coordinate system of the diagram.

#### `OrientationKind`

The orientation of the coordinate system with respect to top, left, and the coordinate number system. It is an enumeration with two values:

- `OrientationKind.positive`: x values increase from left to right and y values from bottom to top (right hand orientation).
- `OrientationKind.negative`: x values increase from left to right and y values from top to bottom (left hand orientation). RDFArchitect always writes this value.

#### `DiagramObject`

An object that defines one or more points in a given space. It can be associated with anything that specializes `IdentifiedObject`; in RDFArchitect, these are the classes and association ends of a schema.

- `DiagramObject.Diagram` (`Diagram`, 1..1): the diagram the diagram object is part of.
- `DiagramObject.IdentifiedObject` (`IdentifiedObject`, 0..1): the domain object the diagram object is associated with.
- `DiagramObject.DiagramObjectStyle` (`DiagramObjectStyle`, 0..1): the style the diagram object is drawn with in the originating system.

#### `DiagramObjectPoint`

A point in a given space, defined by three coordinates and associated to a diagram object. The coordinates may be positive or negative, as the origin does not have to be in the corner of a diagram.

- `DiagramObjectPoint.DiagramObject` (`DiagramObject`, 1..1): the diagram object the point belongs to.
- `DiagramObjectPoint.xPosition`, `yPosition` (`Float`, 1..1): the x and y coordinate of the point.
- `DiagramObjectPoint.zPosition` (`Float`, 0..1): the z coordinate of the point.
- `DiagramObjectPoint.sequenceNumber` (`Integer`, 0..1): the position of the point in the order of the points of a diagram object that acts as a polyline or polygon. It shall be a positive value.
- `DiagramObjectPoint.DiagramObjectGluePoint` (`DiagramObjectGluePoint`, 0..1): the glue point the point is associated with.

#### `DiagramObjectGluePoint`

Groups diagram object points of different diagram objects that are considered to be glued together in a diagram, even if they are not at the exact same coordinates. A glue point has no attributes of its own. It groups two or more points, and each of these points references it.

#### `DiagramObjectStyle`

A reference to a style the originating system uses for a diagram object. Such a style describes, for example, the thickness of lines, a shape such as a circle or a rectangle, and colours. Only the reference is exchanged; what the style looks like is up to the system that draws the diagram.

#### `IdentifiedObject`

The root class that gives identification and a name to all classes that need them. `Diagram`, `DiagramObject` and `DiagramObjectStyle` specialize it.

- `IdentifiedObject.mRID` (`String`, 1..1): the master resource identifier, see [Identifiers](/developer-guide/diagram-layout-triples#identifiers).
- `IdentifiedObject.name` (`String`, 1..1): any free, human readable and possibly non unique text naming the object.

### What RDFArchitect does not use

The remaining parts of the profile are not used by RDFArchitect, mostly because a class diagram has no need for them.

- **`DiagramStyle`**: the style of a whole diagram in the originating system, such as schematic or geographic. All diagrams of RDFArchitect are class diagrams, so they share one style.
- **`VisibilityLayer`**: groups diagram objects by themes and scales, so that they can be shown or hidden, for example depending on the zoom level. RDFArchitect currently has no use case for layers.
- **`TextDiagramObject`**: a diagram object for placing free text, or text derived from its domain object. The labels of RDFArchitect are plain `DiagramObject`s, and their text comes from the schema.
- **`Diagram.x1InitialView`, `x2InitialView`, `y1InitialView`, `y2InitialView`**: the corners of the part of the diagram that is shown first. The editor fits the view to the diagram instead.
- **`DiagramObject.drawingOrder`**: the order in which elements are drawn, so that overlapping elements are drawn correctly. RDFArchitect orders classes by the `zPosition` of their point instead.
- **`DiagramObject.isPolygon`**: whether the points of a diagram object are the boundary of a polygon or the route of a polyline. In RDFArchitect, the style of a `DiagramObject` already tells what it is.
- **`DiagramObject.offsetX`, `offsetY`**: the offset of an icon from the point that places it. RDFArchitect writes no offsets, see [Deviations from the profile](/developer-guide/diagram-layout-triples#the-point-of-a-class-is-its-top-left-corner).
- **`DiagramObject.rotation`**: the angle of rotation of a diagram object. Nothing in RDFArchitect is rotated.
- **`IdentifiedObject.description`**: a free text describing the object. RDFArchitect currently has no use case for it.
