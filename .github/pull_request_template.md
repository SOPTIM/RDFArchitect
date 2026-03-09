## Description

<!-- Describe your changes  here -->

## Test Checklist
### General Behavior
- [ ] Components reload automatically when data changes
- [ ] Editing features are disabled in readonly datasets
- [ ] Dialogs pre-select current dataset/graph
- [ ] Required fields are validated in dialogs
- [ ] Discarding unsaved changes opens a discard cancel confirm dialog

### Global MenuBar
- [ ] Navigate to home page works
- File menu:
    - [ ] Import → Graph/SHACL works
    - [ ] Export → Graph/SHACL works
    - [ ] Share Snapshot works
    - [ ] Delete → Dataset/Graph works
- Edit menu:
    - [ ] New → Class works
    - [ ] New → Package works
    - [ ] Edit/View → Create/Edit/View Ontology works
    - [ ] Edit/View → Package works
    - [ ] Undo/Redo (Ctrl+Z / Ctrl+Y) works
    - [ ] Enable/Disable editing works
    - [ ] Manage/View namespaces works
    - [ ] Delete → Ontology/Package works
- View menu:
    - [ ] Changelog opens and shows current graph
    - [ ] Compare Graphs opens
    - [ ] Full SHACL works
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
- [ ] Hierarchical display (Datasets → Graphs → Packages) works
- [ ] Selection is highlighted
- [ ] Selecting a class does not change dataset/graph/package selection
- [ ] Class selection stays open/highlighted when switching dataset/graph/package
- [ ] Datasets and graphs are collapsible
- [ ] Single click selects; double click or chevron toggles expand/collapse
- [ ] State persists on reload (non-browser)
- [ ] Context menus act on the dataset/graph/package they were opened on
- [ ] Hover labels show prefixes when configured
- Dataset context menu:
    - [ ] Import graph works (disabled in readonly datasets)
    - [ ] Share Snapshot works
    - [ ] Enable/Disable editing works
    - [ ] Manage/View namespaces works
    - [ ] Delete dataset works
- Graph context menu:
    - [ ] New package works (disabled in readonly datasets)
    - [ ] Undo/Redo works (only enabled when available)
    - [ ] Create Ontology
    - [ ] Edit Ontology (View Ontology in readonly)
    - [ ] Delete Ontology
    - [ ] Changelog navigation works
    - [ ] Compare dialog works
    - [ ] SHACL import/export/full view works (import disabled in readonly datasets)
    - [ ] Export graph works
    - [ ] Delete graph works (disabled in readonly datasets)
- Package context menu:
    - [ ] Create new class works (disabled in readonly datasets)
    - [ ] View/Edit package works
    - [ ] Copy URL works
    - [ ] Delete package works (disabled for external/default packages and readonly datasets)
- Class context menu:
    - [ ] Open class (editor) works
    - [ ] SHACL works
    - [ ] Delete class works (disabled in readonly datasets)

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

### Editor - Class Editor
- [ ] Display and edit class properties: UUID (readonly), Label, Namespace, Package, Derived from, Abstract, Stereotypes, Attributes, Associations, Comment
- [ ] Delete class works
- [ ] Save changes works
- [ ] Discard changes works
- [ ] Attribute Editor works
- [ ] Association Editor works
- [ ] attribute/association SHACL View works
- [ ] Class SHACL View works 

### Prefixes Page
- [ ] View, add, remove and edit namespaces works

### Changelog Page
- [ ] Select graph and display write operations works
- [ ] Operations shown in reverse chronological order
- [ ] Detailed view of changed triples works
- [ ] Restoring graph to a version works

### Compare Page
- [ ] Compare two graphs works
