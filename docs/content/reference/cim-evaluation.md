---
title: CIM Evaluation
sidebar_position: 10
---

# CIM Evaluation

## Defined IRIs in use

### RDFS

Namespace: `http://www.w3.org/2000/01/rdf-schema#`

- `rdfs:Class`
    - Defines that a resource is a class.
- `rdfs:subClassOf`
    - Defines that the subject is a `subClassOf` the object
- `rdfs:Literal`,  `rdfs:Datatype`, `rdf:langString`, `rdf:HTML`, `rdf:XMLLiteral`
    - Can be used to define data format of Literal. (e.g. comments)
- `rdf:type`
    - Defines the type of a ressource, so whether it's a class, enumEntry or property (attribute/association).
- `rdfs:domain`
    - Defines the affiliation of a property (attribute/association) to a class.
- `rdfs:range`
    - Defines, the datatype of an attribute in case of an enum attribute or the target of an association.
- `rdfs:label`
    - Defines a label for ressource.
- `rdfs:comment`
    - Defines a comment for a ressource.
    - The format of comments gets normalized to `xsd:string` on import.

### CIMS

Namespace: `https://iec.ch/TC57/1999/rdf-schema-extensions-19990926#`

- `cims:belongsToCatgory`
    - Defines the optional package that a class belongs to.
- `cims:stereotype`
    - Defines Metadata for a ressource, e.g. the wether or not a class is abstract, an datatype etc.
- `cims:datatype`
    - Defines the datatype of an attribute, in case that datatype is not a enum class.
- `cims:mutlitplicity`
    - Defines how often an attribute or association should be instantiated. Can either define a range (`M:0..1` meaning a number from 0 to 1) or a specific amount (`M:1..1` or `M:1` meaning exactly one). Ranges can also be open ended, for example `M:1..n` being one or more and `M:n` being any amount.
- `cims:isDefault`
    - Defines a default value for an attribute.
    - can in some cases be specified in the form of a blank node.
- `cims:isFixed`
    - Defines one fixed value for an attribute.
    - can in some cases be specified in the form of a blank node.
- `cims:associationUsed`
    - Defines wether or not an association should be instantiated in the given direction.
- `cims:inverseRoleName`
    - References the IRI of the inverse association.

## Ontology Header

Standard CGMES 3.0 entries used in the Ontology Header

- dct:conformsTo
- dct:creator
- dct:description
- dct:identifier
- dct:issued
- dct:language
- dct:modified
- dct:publisher
- dct:rights
- dct:rightsHolder
- dct:title
- owl:backwardCompatibleWith
- owl:incompatibleWith
- owl:priorVersion
- owl:versionIRI
- owl:versionInfo
- dcat:keyword
- dcat:landingPage
- dcat:theme

##  CIM Object structure

- package:&lt;br&gt;
  ``` 
  pre:Package_{packageName}         rdf:type                cims:ClassCategory                                #required
                                    rdfs:label              "{packageName}"@en                                #required
                                    rdfs:comment            "{comment}"^^{format}                             #optional     
  ```                           
- #### class:&lt;br&gt;
  ```                           
  pre:{className}                   rdf:type                rdfs:Class                                        #required
                                    rdfs:label              "{className}"@en                                  #required
                                    rdfs:subClassOf         {superClassIRI}                                   #optional
                                    rdfs:comment            "{comment}"^^{format}                             #optional
                                    cims:belongsToCategory  {packageIRI}                                      #optional
                                    cims:stereotype         {class stereotype}                                #optional
  ```                   
    - common `{class stereotypes}` :
      `<http://iec.ch/TC57/NonStandard/UML#concrete> | <http://iec.ch/TC57/NonStandard/UML#enumeration> | "Primitive" | "CIMDatatype" | "Entsoe"`
    - classes can have more than one stereotype
- #### attribute:
  ``` 
  pre:{classLabel}.{attributeName}  rdf:type                rdf:Property                                      #required
                                    rdfs:label              "{attributeName}"@en                              #required
                                    rdfs:domain             {classIRI}                                        #required
                                    (rdfs:range              {enumClassIRI}  |  cims:dataType {dataTypeIRI})  #required
                                    rdfs:comment            "{comment}"^^{format}                             #optional
                                    cims:stereotype         <http://iec.ch/TC57/NonStandard/UML#attribute>    #required
                                    cims:multiplicity       cims:M:[0-9]+(..(n|[0-9]))                        #required
                                    cims:isFixed            {value}^^{datatype} 
                                                            | [ rdfs:Literal {value}^^{datatype}]             #optional
                                    cims:isDefault          {value}^^                                      
                                                            | [ rdfs:Literal {value}^^{datatype}]             #optional
  ```
    - prefix of the attribute does not have to be the same as class prefix
    - multiplicity of attributes is most commonly either `M:0..1` or `M:1..1` to indicate optional or required attributes
- #### association:
  ``` 
  pre:{classLabel}.{label}          rdf:type                rdf:Property                                      #required
                                    rdfs:label              "{label}"@en                                      #required (is the {targetLabel} by default)          
                                    rdfs:domain             {classIRI}                                        #required
                                    rdfs:range              {targetIRI}                                       #required
                                    rdfs:comment            "{comment}"^^{format}                             #optional
                                    cims:AssociationUsed    "Yes" | "No"                                      #required
                                    cims:inverseRoleName    {targetIRI}.{inverseLabel}                        #required
                                    cims:multiplicity       cims:M:[0-9]+(..(n|[0-9]+))?                      #required
  ```                   
  ```                   
  pre:{targetLabel}.{inverseLabel}  rdf:type                rdf:Property                                      #required
                                    rdfs:label              "{inverselabel}"@en                               #required (is {classLabel} by default)               
                                    rdfs:domain             {targetIRI}                                       #required
                                    rdfs:range              {classIRI}                                        #required
                                    rdfs:comment            "{inverseComment}"^^{format}                      #optional
                                    cims:AssociationUsed    "Yes" | "No"                                      #required
                                    cims:inverseRoleName    {classIRI}.{label}                                #required
                                    cims:multiplicity       cims:M:[0-9]+(..(n|[0-9]+))?                      #required
  ```   
    - prefix of the association or inverse association does not have to be the same as domain or range prefix
- #### enumEntry:
  ``` 
  pre:{classLabel}.{enumEntryName}  rdf:type                {enumClassIRI}                                    #required
                                    rdfs:label              "{enumEntryName}"@en                              #required
                                    rdfs:comment            "{comment}"^^{format}                             #optional
                                    cims:stereotype         "enum"                                            #optional
  ```