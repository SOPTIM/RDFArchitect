---
title: Read-Only Mode
sidebar_position: 12
---

# Read-Only Mode

Imported workspaces and workspaces loaded from snapshot links are read-only by default. When a workspace is read-only, all editing actions are disabled in the UI, menu entries switch to their **View** variants, and the save buttons in every dialog are hidden.

To make changes, select the workspace and use **Enable Editing** in the top-right corner, **Edit → Enable Editing** in the menu bar, or **Enable Editing** from the workspace context menu on its tab or in the navigation tree.

If you want to lock the workspace again later, disable editing from the same places. Disabling editing does not revert existing changes; it only prevents further edits.

Enabling editing for a workspace loaded from a snapshot changes only the in-memory workspace in the backend. The original snapshot stored in Fuseki remains unchanged.
