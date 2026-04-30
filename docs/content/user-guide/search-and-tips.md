---
title: Search & Tips
sidebar_position: 13
---

# Search and Tips

## Search

The search bar at the top of the editor searches across **classes, attributes, associations, enum entries, and packages**, scoped by default to the currently selected dataset. Results are shown as a ranked list with the matching URI highlighted; clicking a result jumps to that element in the editor (selecting the right dataset, graph, package, and class as needed).

Search is the fastest way to find, for example, every class with `Terminal` in its name across all profiles in a dataset, or every association whose role contains a given fragment.

## Tips & keyboard shortcuts

- **Ctrl+Z / Ctrl+Y** — Undo and redo, in every editing context.
- **URL parameters** — The main editor URL accepts `?dataset=...&graph=...&package=...` to jump directly to a given location. This is how deep links from external tools or documents should point at RDFArchitect content.
- **Save snapshot before risky changes** — If you are about to try a large migration or a destructive delete, creating a snapshot first gives you a restore point that is independent of the undo history.
- **Filter view** — The view filter dialog can hide external packages or constrain the diagram to a specific stereotype, which keeps large CGMES releases navigable.
