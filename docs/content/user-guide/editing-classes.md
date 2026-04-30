---
title: Editing Classes
sidebar_position: 4
---

import classEditorScreenshot from '@site/static/img/screenshots/class-editor.png';

# Editing Classes

The class editor on the right-hand side is the main surface for modelling work. It is laid out so that everything about a single class is reachable from one scroll, without navigating away.

<img src={classEditorScreenshot} alt="Class editor" className="screenshot--narrow" />

## What you can edit

- **Label and URI namespace.** The human-readable name and the namespace it lives under. The editor enforces label uniqueness and flags invalid characters inline.
- **Package.** Moves the class between packages in the current graph.
- **Super class.** Sets or clears inheritance. The picker shows all classes from the current graph and any external packages it references.
- **Stereotypes.** CIM uses stereotypes heavily (`«enumeration»`, `«CIMDatatype»`, `«Primitive»`, `«Compound»`, etc.). They are selected from the list of known stereotypes and shown in the diagram above the class name.
- **Comment.** Free-text description, rendered as AsciiDoc in the class editor so that lists, code snippets, and links are formatted sensibly when reading back a profile.
- **Attributes.** Data-typed properties. Each row defines a local name, URI namespace, datatype (from the CIM primitive datatypes or xsd types), cardinality, a fixed value if any, and a comment.
- **Associations.** Links to other classes. You set the target class, role name, multiplicity (`0..1`, `1`, `0..*`, `1..*`), the inverse role where applicable, and a comment.
- **Enum entries.** Present only when the class has the `«enumeration»` stereotype. Each entry has a label, URI, and comment and is ordered in the list.
- **SHACL on a property.** Every attribute and association row has a small icon that opens the property-specific SHACL dialog (see [SHACL](./shacl)).
- **UUID.** The internal resource UUID is shown read-only. It is stable across edits and is what RDFArchitect uses internally to refer to the class.

## Validation as you type

The editor does not wait for save to tell you something is wrong. Label collisions, empty required fields, duplicate attribute names, invalid URI components, and SHACL violations caused by the pending changes are all reported inline as you type. The **Save** button stays disabled while there are unresolved issues, and a list of violations is shown above the button.

## Discard or save unsaved changes

If you switch classes while there are unsaved edits, RDFArchitect asks whether to save the current class or discard the pending changes before opening another class. Saving commits the edits to the current class; discarding drops them.
