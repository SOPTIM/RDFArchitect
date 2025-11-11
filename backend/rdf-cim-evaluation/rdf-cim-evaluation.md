# RDFS - CIM - evaluation

## RDFS version 1.1

- `rdfs:Class`
    - Defines that a resource is a class.
- `rdfs:subClassOf`
    - Defines that the subject is a `subClassOf` the object
- `rdfs:Literal`,  `rdfs:Datatype`, `rdf:langString`, `rdf:HTML`, `rdf:XMLLiteral`
    - Can be used to define data format of Literal. (e.g. comments)
- `rdf:type`
    - Defines the type of a ressource, so whether it's a class, enumEntry or property (attribute/association).
- ~~rdfs:subPropertyOf~~
    - subPropertyOf serves no purpose in the context of UML.
- `rdfs:domain`
    - Defines the affiliation of a property (attribute/association) to a class.
- `rdfs:range`
    - Defines, the datatype of an attribute or the target of an association.
- `rdfs:label`
    - Defines a label for ressource.
- `rdfs:comment`
    - Defines a comment for a ressource.
- ~~`rdfs:member`~~
    - Component of container classes/properties.
- ~~`rdf:first`~~, ~~`rdf:rest`~~
    - Component of collections.
- ~~`rdfs:seeAlso`~~
    - no use case
- ~~`rdfs:isDefinedBy`~~
    - no use case
- ~~`rdf:value`~~
    - Describes the value that is assigned to a subject.
    - Not used since we are only modeling uml and have no values
        - may be useful for default values, where a “cims:isDefault” exists in cims

- ~~`rdf:subject`~~, ~~`rdf:predicate`~~, ~~`rdf:object`~~
    - Describes a statement(rdf triple) which is irrelevant in the UML context.

## Object structure

### RDFS

- #### class:<br>
  ``` 
  pre:{className}            rdf:type               rdfs:Class                                     #required
                             rdfs:label             "{className}"@en                               #required
                             rdfs:subClassOf        {superClassURI}                                #optional
  ```

- #### attribute:
  ``` 
  {classURI}.{attributeName} rdf:type               rdf:Property                                  #required
                             rdfs:label             "{attributeName}"@en                           #required
                             rdfs:domain            {classURI}                                     #required
                             rdfs:range             {xsdDatatypeURI}                               #required
  ```
- #### association:
  ``` 
  {classURI}.{targetName}    rdf:type               rdf:Property                                  #required
                             rdfs:label             "{targetName}"@en                              #required
                             rdfs:domain            {classURI}                                     #required
                             rdfs:range             {targetURI}                                    #required
  ``` 
- #### enumEntry:
  ``` 
  {classURI}.{attributeName} rdf:type              {enumClassURI}                                  #required
                             rdfs:label            "{attributeName}"@en                            #required
                             rdfs:comment          "{comment}"^^{format}                           #optional
  ```

### CIM

- package:<br>
  ``` 
  pre:Package_{packageName}  rdf:type               cims:ClassCategory                             #required
                             rdfs:label             "{packageName}"@en                             #required
                             rdfs:comment           "{comment}"^^{format}                          #optional
                             cims:belongsToCategory {packageURI}                                   #suggestion (This would allow us to create a package structure)
  ```
- #### class:<br>
  ``` 
  pre:{className}            rdf:type               rdfs:Class                                     #required
                             rdfs:label             "{className}"@en                               #required
                             rdfs:subClassOf        {superClassURI}                                #optional
                             rdfs:comment           "{comment}"^^{format}                          #optional
                             cims:belongsToCategory {packageURI}                                   #optional
                             cims:stereotype        {class stereotype}                             #optional
  ```
    - possible `{class stereotypes}` :
      `<http://iec.ch/TC57/NonStandard/UML#concrete> | <http://iec.ch/TC57/NonStandard/UML#enumeration> | "Primitive" | "CIMDatatype" | "Entsoe"`
- #### attribute:
  ``` 
  {classURI}.{attributeName} rdf:type               rdf:Property                                   #required
                             rdfs:label             "{attributeName}"@en                           #required
                             rdfs:domain            {classURI}                                     #required
                             rdfs:range             {enumClassURI}  |  cims:dataType {dataTypeURI} #required
                             rdfs:comment           "{comment}"^^{format}                          #optional
                             cims:stereotype        <http://iec.ch/TC57/NonStandard/UML#attribute> #required
                             cims:multiplicity      cims:M:0..1 | cims:M:1..1                      #required
                             cims:isFixed           {value}                                        #optional
                             cims:isDefault         {value}                                        #optional
  ```
- #### association:
  ``` 
  {classURI}.{label}         rdf:type              rdf:Property                                   #required
                             rdfs:label            "{label}"@en                                    #required (is the {targetLabel} by default)
                             rdfs:domain           {classURI}                                      #required
                             rdfs:range            {targetURI}                                     #required
                             rdfs:comment          "{comment}"^^{format}                           #optional
                             cims:AssociationUsed  "Yes" | "No"                                    #required
                             cims:inverseRoleName  {targetURI}.{inverseLabel}                      #required
                             cims:multiplicity     cims:M:[0-9]+(..(n|[0-9]+))?                    #required (only describes syntax, validity is the users responsibility)
  ``` 
  ``` 
  {targetURI}.{inverseLabel} rdf:type              rdf:Property                                   #required
                             rdfs:label            "{inverselabel}"@en                             #required (is {classLabel} by default)
                             rdfs:domain           {targetURI}                                     #required
                             rdfs:range            {classURI}                                      #required
                             rdfs:comment          "{inverseComment}"^^{format}                    #optional
                             cims:AssociationUsed  "Yes" | "No"                                    #required
                             cims:inverseRoleName  {classURI}.{label}                              #required
                             cims:multiplicity     cims:M:[0-9]+(..(n|[0-9]+))?                    #required (only describes syntax, validity is the users responsibility)
  ``` 
- #### enumEntry:
  ``` 
  {classURI}.{attributeName} rdf:type              {enumClassURI}                                  #required
                             rdfs:label            "{attributeName}"@en                            #required
                             rdfs:comment          "{comment}"^^{format}                           #optional
                             cims:stereotype       "enum"                                          #optional (required with HSLN-export)
  ```

## Issues/Solutions

- Some stereotypes require a different ClassEditor.
    - e.g. `"enum"`
        - Right now we can display `enums`, but it's not possible to edit them or show their entries.

- `"enum"` is set as a `stereotype` for `enumEntries`, but only in the `"augmented 2019"` enterprise architect
  export.<br><br>

- What is the purpose of stereotype `"Entsoe"`?
    - It doesn't seem to make any difference.
    - `"Entsoe"` doesn't appear on the `"augmented 2019"` enterprise architect export, so we will ignore it for
      now.<br><br>

- isDefault/isFixed:
    - If the user wants to specify one of these fields, they must select an `XSDDataType`.
    - This is to ensure that the data is correctly stored in the backend.
        - It might be possible to do this automatically, but at first glance, it doesn’t seem so, as the primitive data
          types in the CIM schema are new/custom and created without a reference to an `XSDDataType`.
    - The format could be either: (based on personal preference)
        2. `“{value}”` and the option to select the type: “xsd/rdf/rdfs:type” (direct value).
        3. `[ xsd/rdf/rdfs:datatype “M” ]` (via blank nodes).

        - Simplest options:

        4. Write everything as a literal, e.g., `“{value}”`.
        1. Expect the use to only make valid inputs. Examples: allowed: [`1`, `""1"^^xsd:integer"`,`2`, `“foo”`, `true`,
           `0`, `“01-01-1999”`, `""01-01-1999"^^xsd:date"`], not allowed: [`foo`, `2-2`].