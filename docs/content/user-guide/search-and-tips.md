---
title: Search & Tips
sidebar_position: 13
---

# Search and Tips

## Search

The search bar at the top of the editor searches across **classes, attributes, associations, enum entries, and packages**, scoped by default to the currently selected workspace. Results are shown as a ranked list with the matching URI highlighted; clicking a result jumps to that element in the editor (selecting the right workspace, graph, package, and class as needed).

Search is the fastest way to find, for example, every class with `Terminal` in its name across all profiles in a workspace, or every association whose role contains a given fragment.

## Tips & keyboard shortcuts

- **Ctrl+Z / Ctrl+Y** — Undo and redo, in every editing context.
- **?** — Opens an overview of all keyboard shortcuts and of the mouse controls in the diagram.
- **Opening editors from the diagram** — Click a class to open it; double-click an attribute, association, enum entry, multiplicity, or role name to open just that property. See [Editing classes](./editing-classes#opening-editors-from-the-diagram).
- **Selecting in the diagram** — **Shift+Click** adds a class to the selection and **Ctrl+Click** toggles it. Dragging on the background draws a selection box; hold **Shift** to add to the current selection or **Ctrl** to toggle.
- **Moving the view** — Drag with the right or middle mouse button to pan, and use the mouse wheel to zoom.
- **URL parameters** — The main editor URL accepts `?dataset=...&graph=...&package=...` to jump directly to a given location. The `dataset` parameter names the workspace; it keeps its original name for compatibility with existing links. This is how deep links from external tools or documents should point at RDFArchitect content.
- **Save snapshot before risky changes** — If you are about to try a large migration or a destructive delete, creating a snapshot first gives you a restore point that is independent of the undo history.
- **Filter view** — The view filter dialog can hide external packages or constrain the diagram to a specific stereotype, which keeps large CGMES releases navigable.
