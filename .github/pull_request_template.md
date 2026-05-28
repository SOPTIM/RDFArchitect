## Summary

<!-- Briefly describe what this PR does and why. -->

## Related Issues

<!-- Link every issue this PR closes or references.
     Use "Closes #NNN" to auto-close on merge, or "Refs #NNN" for partial work. -->

## Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactoring / code quality
- [ ] Documentation
- [ ] Tests
- [ ] Build / CI / dependencies

## Author Checklist

- [ ] Linked the relevant issue(s) above
- [ ] Tests added or updated (or not applicable)
- [ ] Documentation updated (or not applicable)
- [ ] No breaking changes introduced (or described in the summary above)
- [ ] Commits are signed off (`git commit -s`) for DCO

## Affected Areas

<!-- Which parts of the app does this change touch? e.g. Navigation, Class Editor, Package View -->

<details>
<summary>Detailed feature test checklist</summary>

### General Behavior
- [ ] Components reload automatically when data changes
- [ ] Editing features are disabled in readonly datasets
- [ ] Dialogs pre-select current dataset/graph
- [ ] Required fields are validated in dialogs
- [ ] Discarding unsaved changes opens a discard cancel confirm dialog

### Global MenuBar
- [ ] Navigate to home page works
- File menu:
    - [ ] Import → Schema/Constraints works
    - [ ] Export → Schema/Constraints works
    - [ ] Settings works
    - [ ] Share Snapshot works
    - [ ] Delete → Dataset/Schema works
- Edit menu:
    - [ ] New → Class works
    - [ ] New → Package works
    - [ ] Copy class works
    - [ ] Paste class works (disabled in readonly datasets)
    - [ ] Edit/View → Create/Edit/View Profile header works
    - [ ] Edit/View → Package works
    - [ ] Undo/Redo (Ctrl+Z / Ctrl+Y) works
    - [ ] Enable/Disable editing works
    - [ ] Manage/View namespaces works
    - [ ] Delete → Profile header/Package works
- View menu:
    - [ ] Changelog opens and shows current graph
    - [ ] Compare Schemas opens
    - [ ] Schema Migration opens
    - [ ] Full Constraints (SHACL) works
- Help menu:
    - [ ] Help link works
    - [ ] Submit Feedback link works
    - [ ] About navigation works

### Welcome Page
- [ ] Navigation to Editor works
- [ ] Tips are displayed
- [ ] Security and data information displayed
- [ ] Copyright and version information displayed

### Editor - MenuBar
- [ ] Search function works with all filters (All Datasets, Current Dataset, Current Graph, Current Package)
- [ ] Search finds classes, attributes, associations, packages
- [ ] "Enable Editing" button appears for readonly datasets

### Editor - Navigation
- [ ] Hierarchical display (Datasets → Schemas → Packages) works
- [ ] Selection is highlighted
- [ ] Selecting a class does not change dataset/schema/package selection
- [ ] Class selection stays open/highlighted when switching dataset/schema/package
- [ ] Datasets and schemas are collapsible
- [ ] Single click selects; double click or chevron toggles expand/collapse
- [ ] State persists on reload (non-browser)
- [ ] Context menus act on the dataset/schema/package/class they were opened on
- [ ] Hover labels show prefixes when configured
- Dataset context menu:
    - [ ] Add schema works (disabled in readonly datasets)
    - [ ] Import schema works (disabled in readonly datasets)
    - [ ] New Dataset Diagram works
    - [ ] Share Snapshot works
    - [ ] Enable/Disable editing works
    - [ ] Manage/View namespaces works
    - [ ] Delete dataset works
- Schema context menu:
    - [ ] New package works (disabled in readonly datasets)
    - [ ] New Profile Diagram works
    - [ ] Undo/Redo works (only enabled when available)
    - [ ] Create profile header works
    - [ ] Edit profile header (View profile header in readonly)
    - [ ] Delete profile header
    - [ ] Changelog navigation works
    - [ ] Compare dialog works
    - [ ] Migrate schema works
    - [ ] Constraints import/export/full view works (import disabled in readonly datasets)
    - [ ] Export schema works
    - [ ] Delete schema works (disabled in readonly datasets)
- Package context menu:
    - [ ] Create new class works (disabled in readonly datasets)
    - [ ] Add to Profile Diagram works
    - [ ] Add to Dataset Diagram works
    - [ ] Paste class works (disabled in readonly datasets)
    - [ ] View/Edit package works
    - [ ] Copy URL works
    - [ ] Delete package works (disabled for external/default packages and readonly datasets)
- Class context menu:
    - [ ] Show in diagram works and opens class editor
    - [ ] Copy class works
    - [ ] Constraints works
    - [ ] Add to Profile Diagram works
    - [ ] Add to Dataset Diagram works
    - [ ] Delete class works (disabled in readonly datasets)
- Diagram context menu:
    - [ ] Edit diagram works
    - [ ] Delete diagram works

### Editor - Package View
- [ ] Class diagram displays correctly
- [ ] Moving nodes works and layout changes persist after reload
- [ ] Loading animation shows while loading
- [ ] Info cards show when no package or no classes available
- [ ] Drag and zoom diagram works
- [ ] "Reset View" button works
- [ ] "Filter View" works
- [ ] "Reset Layout" button resets diagram to auto-generated layout
- [ ] Click on class opens class editor
- Class context menu:
    - [ ] Copy class works
    - [ ] Delete class works (disabled in readonly datasets)
    - [ ] Move classes between layers works (disabled in readonly datasets)
- Pane context menu:
    - [ ] Add class works (disabled in readonly datasets)
    - [ ] Paste class works (disabled in readonly datasets)

### Editor - Class Editor
- [ ] Display and edit class properties: Label, Namespace, Package, Derived from, Abstract, Stereotypes, Attributes, Associations, Comment
- [ ] Delete class works
- [ ] Save changes works
- [ ] Discard changes works
- [ ] Close editor works
- [ ] Attribute Editor works
- [ ] Association Editor works
- [ ] Attribute/association Constraints View works
- [ ] Class Constraints View works

### Editor - Delete Dialog
- [ ] Package, Classes, Attributes and Associations are correctly listed
- [ ] Delete, Keep, Move to default works
- [ ] Set all works

### Editor - Custom Diagram View
- [ ] Class diagram displays correctly
- [ ] Moving nodes works and layout changes persist after reload
- [ ] Loading animation shows while loading
- [ ] Info cards show when no diagram or no classes available
- [ ] Drag and zoom diagram works
- [ ] "Reset View" button works
- [ ] "Filter View" works
- [ ] "Reset Layout" button resets diagram to auto-generated layout
- [ ] Click on class opens class editor
- Class context menu:
    - [ ] Delete class works (disabled in readonly datasets)
    - [ ] Remove from diagram works
    - [ ] Move classes between layers works (disabled in readonly datasets)
- Pane context menu:
    - [ ] Add class works (disabled in readonly datasets)

### Prefixes Page
- [ ] View, add, remove and edit namespaces works

### Changelog Page
- [ ] Select graph and display write operations works
- [ ] Operations shown in reverse chronological order
- [ ] Detailed view of changed triples works
- [ ] Restoring graph to a version works

### Compare Page
- [ ] Compare two graphs works

### Migrate Schema Page
- [ ] Schema migration works

</details>
