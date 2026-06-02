# Feature Checklist

Reference document for manual testing. Verify the areas relevant to your change before marking a PR as tested.

## General Behavior

- Components reload automatically when data changes
- Editing features are disabled in readonly datasets
- Dialogs pre-select current dataset/graph
- Required fields are validated in dialogs
- Discarding unsaved changes opens a confirm dialog

## Global MenuBar

- Navigate to home page
- File menu: Import / Export Schema & Constraints, Settings, Share Snapshot, Delete Dataset/Schema
- Edit menu: New Class/Package, Copy/Paste class, Edit/View Profile header & Package, Undo/Redo (Ctrl+Z / Ctrl+Y), Enable/Disable editing, Manage namespaces, Delete Profile header/Package
- View menu: Changelog, Compare Schemas, Schema Migration, Full Constraints (SHACL)
- Help menu: Help link, Submit Feedback link, About

## Welcome Page

- Navigation to Editor
- Tips displayed
- Security and data information displayed
- Copyright and version information displayed

## Editor – MenuBar

- Search with all filters (All Datasets, Current Dataset, Current Graph, Current Package)
- Search finds classes, attributes, associations, packages
- "Enable Editing" button appears for readonly datasets

## Editor – Navigation

- Hierarchical display (Datasets → Schemas → Packages)
- Selection highlighting
- Selecting a class does not change dataset/schema/package selection
- Class selection stays highlighted when switching dataset/schema/package
- Datasets and schemas are collapsible
- Single click selects; double click or chevron toggles expand/collapse
- State persists on reload (non-browser)
- Context menus act on the correct node (dataset/schema/package/class)
- Hover labels show prefixes when configured
- Dataset context menu: Add schema, Import schema, New Dataset Diagram, Share Snapshot, Enable/Disable editing, Manage namespaces, Delete dataset
- Schema context menu: New package, New Profile Diagram, Undo/Redo, Create/Edit/Delete profile header, Changelog, Compare, Migrate schema, Constraints import/export/full view, Export schema, Delete schema
- Package context menu: Create class, Add to Profile/Dataset Diagram, Paste class, View/Edit package, Copy URL, Delete package
- Class context menu: Show in diagram, Copy class, Constraints, Add to Profile/Dataset Diagram, Delete class
- Diagram context menu: Edit diagram, Delete diagram

## Editor – Package View

- Class diagram renders correctly
- Moving nodes persists after reload
- Loading animation shown while loading
- Info cards shown when no package or no classes available
- Drag and zoom
- Reset View, Filter View, Reset Layout buttons
- Click on class opens class editor
- Class context menu: Copy class, Delete class, Move between layers
- Pane context menu: Add class, Paste class

## Editor – Class Editor

- Display and edit: Label, Namespace, Package, Derived from, Abstract, Stereotypes, Attributes, Associations, Comment
- Delete, Save, Discard, Close
- Attribute Editor
- Association Editor
- Attribute/association Constraints View
- Class Constraints View

## Editor – Delete Dialog

- Package, Classes, Attributes and Associations listed correctly
- Delete, Keep, Move to default actions
- Set all

## Editor – Custom Diagram View

- Class diagram renders correctly
- Moving nodes persists after reload
- Loading animation shown while loading
- Info cards shown when no diagram or no classes available
- Drag and zoom
- Reset View, Filter View, Reset Layout buttons
- Click on class opens class editor
- Class context menu: Delete class, Remove from diagram, Move between layers
- Pane context menu: Add class

## Prefixes Page

- View, add, remove and edit namespaces

## Changelog Page

- Select graph and display write operations
- Operations in reverse chronological order
- Detailed view of changed triples
- Restore graph to a version

## Compare Page

- Compare two graphs

## Migrate Schema Page

- Schema migration
