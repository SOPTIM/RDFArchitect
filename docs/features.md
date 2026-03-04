# RDF Architect - Features
## General Behavior
- Automatic reloading of components when data changes
- Editing features disabled in readonly datasets
- Dialogs pre-select current dataset/graph
- Validation of required fields in dialogs
## Global MenuBar
- Navigate to home page
- Undo/Redo for graphs
- Create new class
- Create new package
- Graph import/export/delete
- SHACL import/export
- View full SHACL
- Create and share snapshot
## Welcome Page (/)
- Navigate to Editor, Changelog, Prefix Editor, Compare
- Display tips
- Display security and data information
- Display copyright and version information
## Editor (/mainpage)
### MenuBar
- **Search Function**
  - Filter: All Datasets, Current Dataset, Current Graph, Current Package
  - Search for classes, attributes, associations, packages
- Button to enable editing mode (for readonly datasets)
### Navigation
- Hierarchical display: Datasets → Graphs → Packages
- Highlighting of selection
- Collapsible datasets and graphs
- State persists on (non browser) reload
- **Package Context Menu**
  - Create new class
  - Edit package
  - Copy URL
### Package View
- Class diagram of selected package
- Loading animation while loading
- Info cards when no package or no classes available
- Drag and zoom diagram
- "Reset View" button
- "Filter View" for properties and relations
  - Enum entries, attributes, associations, inheritance, relations to external packages
- Click on class opens class editor
### Class Editor
- Display and edit class properties
- Buttons:
  - display class shacl
  - Delete class
  - Save changes
  - discard changes

**Properties:** UUID (readonly), Name, Prefix, Package, Derived from, Is Abstract, Stereotypes, Attributes, Associations, Comment

**Dialogs:**
- Attribute Editor
  - Edit attribute properties: UUID (readonly), Label, Type, Multiplicity, Fixed Value, Default Value, Comment
- Association Editor
  - Edit association properties: 
    - from: UUID (readonly), Label, Target, Multiplicity, Use association?, Comment
    - to:  UUID (readonly), Label, Target, Multiplicity, Use association?, Comment
- Property SHACL View
  - Display generated and custom shacl related to a property (prefixes, property shapes)
- Class SHACL View
  - Display generated and custom shacl related to a class 
    - Prefixes, Nodeshapes, Propertyshapes, Derived Propertyshapes 
    - Edit custom Shacl
    - Go to related classes
## Prefixes (/prefixes)
- Lists all datasets
- View, Add, remove and edit known namespaces of a specific dataset
## Changelog (/changelog)
- select a graph and display all write operations (e.g. rename a class) made to a graph in chronological order
- each operation has a detailed view of what triples were changed
- allows restoring the graph to a version
## Compare (/compare)
- Compares a graph to another
- display the difference between two graphs