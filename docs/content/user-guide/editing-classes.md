---
title: Editing Classes
sidebar_position: 4
---

import classEditorScreenshot from '@site/static/img/screenshots/class-editor.png';

# Editing Classes

The class editor on the right-hand side is the main surface for modelling work. It is laid out so that everything about a single class is reachable from one scroll, without navigating away.

<img src={classEditorScreenshot} alt="Class editor" className="screenshot--narrow" />

## What you can edit

- **Label and URI namespace.** The human-readable name and the namespace it lives under. The editor enforces uniqueness for the label and namespace combination and flags invalid characters inline.
- **Package.** Moves the class between packages in the current graph.
- **Super class.** Sets or clears inheritance. The picker shows all classes from the current graph and any external packages it references.
- **Stereotypes.** CIM uses stereotypes heavily (`«enumeration»`, `«CIMDatatype»`, `«Primitive»`, `«Compound»`, etc.). The editor offers stereotypes already used in the graph and shows selected stereotypes in the diagram above the class name.
- **Comment.** Free-text description, rendered as AsciiDoc in the class editor so that lists, code snippets, and links are formatted sensibly when reading back a profile.
- **Attributes.** Data-typed properties. Each row defines a local name, URI namespace, datatype or enum range (CIM primitives, CIM datatypes, enumerations, or XSD types), cardinality, a fixed value if any, and a comment.
- **Associations.** Links to other classes. You set the target class, role name, multiplicity, inverse role name, and a comment.
- **Enum entries.** Present only when the class has the `«enumeration»` stereotype. Each entry has a label, URI, and comment and is ordered in the list.
- **Constraints (SHACL) on a property.** Every attribute and association row has a small icon that opens the property-specific constraints dialog (see [SHACL](./shacl)).

## Opening editors from the diagram

Most of what the diagram shows opens its editor directly, so you rarely need to go through the class editor to change a single property.

- **Classes.** Clicking a class opens it in the class editor. The class context menu offers the same as **Edit Class** (**View Class** in a read-only workspace). You open that menu by right-clicking the class, or with the **⋯** button that appears in the top-right corner of a class while the pointer is over it. The context menu of a class in the navigation tree has the entry too.
- **Attributes, associations, and enum entries.** Properties listed inside a class are underlined while the pointer is over them. A single click only marks a property with a grey outline; it neither selects the class nor opens the class editor. A double click opens the editor of that property, with the cursor in its label field. The same editor is available from the property's context menu as **Edit Attribute**, **Edit Association**, or **Edit Enum Entry** (**View …** in a read-only workspace). Associations are listed inside classes only while the **Associations in Class** filter is enabled.
- **Inherited properties.** With the **Inherited Properties** filter enabled, a property a class inherits opens on the super class that defines it, and edits are saved there.
- **Association labels.** Double-clicking a multiplicity or a role name on an association edge opens that association, with the cursor in the multiplicity or role name you clicked. The association opens from the class that owns the clicked end, so what you clicked is always shown as the association itself, never as its inverse. Labels can still be dragged to another position.

Only the property editor opens; the class editor stays as it is. If the class editor already shows the class the property belongs to, both work on the same unsaved state: a change made in the property editor appears in the class editor right away, so the two cannot overwrite each other.

While **Ctrl**, **Shift**, or **Alt** is held, properties and labels are not clickable and a click goes to the class instead. Selecting several classes with **Ctrl+Click** or **Shift+Click** therefore works the same whether you click a class name or one of its properties, and **Alt+Click** on a property opens its class. **Esc** first removes the grey outline from a marked property and clears the class selection with the next press.

The pointer shows what a click or drag will do: a pointing hand over properties and other elements that open something on click, an open hand over classes and labels that can be dragged, and a crosshair while you drag a selection box.

## Validation as you type

The editor does not wait for save to tell you something is wrong. Duplicate labels within the same namespace, empty required fields, duplicate attribute names, and invalid URI components are reported directly at the affected fields as you type. The **Save** button stays disabled while there are unresolved issues.

## Discard or save unsaved changes

If you switch classes while there are unsaved edits, RDFArchitect asks whether to save the current class or discard the pending changes before opening another class. Saving commits the edits to the current class; discarding drops them.
