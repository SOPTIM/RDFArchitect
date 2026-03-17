# Welche SHACL Shapes können wir erzeugen?

Wir teilen in die einzelnen CIM Ressourcen auf ([Klassen](#Klassen), [Properties](#Properties), [packages](#Packages))

## Packages

#### Was wird für Packages erstellt?

- Wir erstellen keine SHACL Shapes für packages

## Klassen

#### Was wird für Klassen erstellt?

- für Klassen werden NodeShapes erstellt
- name der nodeShape:
    - der name ist egal # Kenntnisstand 14.04
    - ich entscheide mich dafür einfach den Namen der Klasse mir einem neuen Präfix zu nutzen (rdfash)

#### Welche Vorraussetung muss eine Klasse erfüllen, damit eine NodeShape für diese erstellt wird?

- eine Klasse muss eine Eigenschaft haben, für welche eine PropertyShape erstellt wird (heißt ein Attribute und/oder
  eine Assoziation)
- eine Klasse muss instanziierbar sein.
- eine Klasse ist instanziierbar:
    1. wenn Sie den stereotype `<http://iec.ch/TC57/NonStandard/UML#concrete>` hat (also nicht abstract ist)
    2. wenn Sie keinen der folgenden Stereotypes hat:
        - `CIMDatatype`
- Teilweise kann ich kein Muster erkennen, weshalb einige Klassen Validiert werden und andere nicht. (bsp. nc:
  BiddingZone in RemedialActions)

# Properties

- sh:order:
    - jede PropertyShape wird dieses Triple bekommen
    - Das order attribut wird so nummeriert, dass aufsteigend nach alphabetischer Reihenfolge sortiert wird
        - alle erstellten shapes zu einer property erhalten die selbe order
    - Ausnahme sind die Identified object attribute, welche alle mit 0.1 besetzt sind.

## Attribute

#### Was kann ich für Attribute erstellen?

- Typvalidierung
    - die Uri der PropertyShape ist `Klassenname.Attributename` mit einem neuen Präfix und dem suffix `-datatype`
    - Sonderregeln:
        - IdentifiedObject:

- Kardinalitätsvalidierung
    - die Uri der PropertyShape ist der `Klassenname.Attributename` mit einem neuen Präfix und dem suffix `-cardinality`

###### Attribute mit primitivem Datentyp

``` turtle
#type validation
    prefix:Klassenname.Attributename-datatype
            rdf:type        sh:PropertyShape;
            sh:datatype     aus Primitiver Klasse abzuleitende xsd-datatype uri
            sh:description  "This constraint validates the datatype of the property (attribute).";
            sh:group        prefix:DatatypesGroup; #ausnahme IdentifiedObject: ido:DatatypesGroupIO
            sh:message      "The datatype is not literal or it violates the xsd datatype.";
            sh:name         "Klassenname.Attributename-datatype";
            sh:nodeKind     sh:Literal;
            sh:order        {double};
            sh:path         uri der PropertyRessource (dem Attribut);
            sh:severity     sh:Violation .

#cardinality validation
    prefix:Klassenname.Attributename-cardinality
            rdf:type        sh:PropertyShape;
            sh:description  "This constraint validates the cardinality of the property (attribute).";
            sh:group        prefix:CardinalityGroup; #ausnahme IdentifiedObject: ido:CardinalityIO
            sh:minCount     0 oder 1 je nach cims:multiplicity der Property;
            sh:maxCount     1; 
            sh:message      "Cardinality violation. Upper bound shall be 1"; (wenn mincount 0 ist) oder "Missing required property (attribute)."; (wenn mincount 1 ist); TODO: diese Nachricht ergibt doch nur Sinn wenn es keinen maxcount gibt oder?
                #ich werde erstmal alle kardinalitäten unterstützten, eine andere kardinalität wird eine default message "cardinality violation {mincount}..{maxcount}." haben
            sh:name         "Klassenname.Attributename-cardinality";
            sh:order        {double};
            sh:path         uri der PropertyRessource (dem Attribut);
            sh:severity     sh:Violation .
```

###### Attribute mit enum Klasse als Datentyp

``` turtle
#type validation
    prefix:Klassenname.Attributename-datatype
            rdf:type        sh:PropertyShape;
            sh:description  "This constraint validates the datatype of the property (attribute).";
            sh:group        prefix:DatatypesGroup; #ausnahme IdentifiedObject: ido:DatatypesGroupIO
            sh:in           ( Liste von uris der möglichen Enumwerte );
            sh:message      "The datatype is not IRI (Internationalized Resource Identifier) or it is enumerated value not part of the profile.";
            sh:name         "Klassenname.Attributename-datatype";
            sh:nodeKind     sh:IRI;
            sh:order        {double};
            sh:path         uri der PropertyRessource (dem Attribut);
            sh:severity     sh:Violation .

#cardinality validation
    dl:Diagram.orientation-cardinality
            rdf:type        sh:PropertyShape;
            sh:description  "This constraint validates the cardinality of the property (attribute).";
            sh:group        prefix:CardinalityGroup; #ausnahme IdentifiedObject: ido:CardinalityIO
            sh:minCount     0 oder 1 je nach cims:multiplicity der Property;
            sh:maxCount     1; 
            sh:message      "Cardinality violation. Upper bound shall be 1"; oder "Missing required attribute.";
                #ggf ist es sinnvoll hier alle Kardinalitäten zu erlauben und dann einfach eine default message zu setzten
            sh:name         "Diagram.orientation-cardinality";
            sh:order        {double};
            sh:path         uri der PropertyRessource (dem Attribut);
            sh:severity     sh:Violation .
```

## Assoziationen

- Typvalidierung
    - die Uri der PropertyShape ist der `Klassenname.AssoziationsName` mit einem neuen Präfix und dem suffix
      `-valueType`
- Kardinalitätsvalidierung
    - die Uri der PropertyShape ist der `Klassenname.AssoziationsName` mit einem neuen Präfix und dem suffix
      `-cardinality`

```turtle
#type validation
prefix:Klassenname.ZielKlassenName-valueType
    rdf:type       sh:PropertyShape ;
    sh:description "This constraint validates the value type of the association at the used direction." ;
    sh:group       prefix:AssociationsGroup ;
    sh:in          ( Liste von uris of the rdfs:range of the association ) ; //hier müssen auch alle Klassen gelistet werden, welche von der rdfs:range klasse erben um veerbung korrekt zu unterstützen. 
            sh:message      "One of the following does not conform: 1) The value type shall be IRI; 2) The value type shall be an instance of the class: {rdfs:RangeUri}"; oder "One of the following occurs: 1) The value type is not IRI; 2) The value type is not the right class.", wenn sh:in mehrere einträge hat. //TODO: abklären, ob es hier sinnvoll wäre nur die erste Option zu nehmen und dann die höchste konkrete superklasse zu nehmen.
            sh:name         "Klassenname.ZielKlassenName-valueType";
            sh:nodeKind     sh:IRI;
            sh:order        {double};
            sh:path         ( associationsUri rdf:type );
        sh:severity     sh:Violation .

#cardinality validation
prefix:Klassenname.ZielKlassenName-cardinality
    rdf:type       sh:PropertyShape ;
    sh:description "This constraint validates the cardinality of the association at the used direction." ;
    sh:group       prefix:CardinalityGroup ;
    sh:minCount    0 .

.n ;
sh:maxCount
             1 .

.m ;
sh:message
              "Cardinality violation. Upper bound shall be 1"; oder "Missing required association.";
                #ggf ist es sinnvoll hier alle Kardinalitäten zu erlauben und dann einfach eine default message zu setzten
            sh:name         "Klassenname.ZielKlassenName-cardinality";
            sh:order        {double};
            sh:path         associationsUri;
            sh:severity     sh:Violation .
```

- inverse Kardinalität
    - wird nur erstellt, wenn beide associations used sind

```turtle

prefix:Klassenname.AssoziationsName-cardinalityInverse
    rdf:type       sh:PropertyShape ;
    sh:description "This constraint validates the cardinality of the association at the inverse direction." ;
    sh:group       prefix:InverseAssociationsCardinality ;
    sh:message     "Wrong number of associated instances." ;
    sh:minCount    0 .

.n ;
sh:maxCount
             1 .

.m ;
sh:name
                 "Klassenname.ZielKlassenName-cardinalityInverse";
        sh:order        {double};
        sh:path         [ sh:inversePath  prefix:ZielKlassenName.KlassenName ];
        sh:severity     sh:Violation .
```

## Was muss außerdem noch erzeugt werden?

#### PropertyGroups

```turtle
#cardinality group
prefix:CardinalityGroup
    rdf:type   sh:PropertyGroup ;
    rdfs:label "Cardinality" ;
    sh:order   0 .

#cardinality group
prefix:InverseAssociationsCardinality
    rdf:type   sh:PropertyGroup ;
    rdfs:label "InverseAssociationsCardinality" ;
    sh:order   0 .

#datatypes group
prefix:DatatypesGroup
    rdf:type   sh:PropertyGroup ;
    rdfs:label "Datatypes" ;
    sh:order   1 .

#associations group
prefix:AssociationsGroup
    rdf:type   sh:PropertyGroup ;
    rdfs:label "Associations" ;
    sh:order   2 .
```

#### Metainformationen zu der Datei

- Ich sehe nur, dass hier die Uhrzeit automatisch erzeugt werden kann. Langfristig ist es wahrschienlich sinnvoll einen
  dialog zu erstellen um beim export diese infos anzugeben, jedoch werde ich dies erst einmal ignorieren.

```turtle

bsp
eq:Ontology
    rdf:type         owl:Ontology ;
    dct:conformsTo   "urn:iso:std:iec:61970-600-2:ed-1", "urn:iso:std:iec:61970-301:ed-7:amd1",
                     "file://iec61970cim17v40_iec61968cim13v13a_iec62325cim03v17a.eap",
                     "urn:iso:std:iec:61970-501:draft:ed-2" ;
    dct:creator      "ENTSO-E CIM EG"@en ;
    dct:description  "The constraints for the core equipment profile from IEC 61970-600-2."@en ;
    dct:identifier   "urn:uuid:1fac0fd3-d607-42e5-931f-850d3361caab" ;
    dct:issued       "2021-10-18T12:09:21Z"^^xsd:dateTime ;
    dct:language     "en-GB" ;
    dct:modified     "2020-10-12"^^xsd:date ;
    dct:publisher    "ENTSO-E"@en ;
    dct:rights       "Copyright"@en ;
    dct:rightsHolder "ENTSO-E"@en ;
    dct:title        "Core Equipment Constraints"@en ;
    owl:versionIRI   <http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/Constraints/3.0> ;
    owl:versionInfo  "3.0.0"@en ;
    dcat:keyword     "EQ" ;
    dcat:landingPage "https://www.entsoe.eu/digital/cim/cim-for-grid-models-exchange/" ;
    dcat:theme       "constraints"@en .
```

# Ideen, welche noch evaluiert werden müssen

## wie werden CIMDatatypes behandelt?

## CIMDatatypes können in 2 Arten auftreten

### Möglichkeit 1: Primitive Wrapper

- Wrapt primitive Datentypen wie `Float`, `Boolean`, `String`, etc.
- hierfür muss der name der PropertyShape mit dem Namen des Datentyps übereinstimmen
  ### Möglichkeit 2: Wert mit Einheit und Multiplikator
- `CIMDatatype` hat 3 Properties:
    - `value` Numerischer Wert (z.B. `100`, `3.14`, etc.)
    - `unit` Physische Einheit (wie z.B. `Watt`, `Volt`, etc.)
    - `multiplier` Skalierungsfaktor (wie z.B. `kilo`, `mega`, etc.)

## datatype mapping

https://jena.apache.org/documentation/notes/typed-literals.html

## Assoziationen

- wir erzeugen bis jetzt keine Regeln, welche die Seite mit [cims:AssociationUsed "No"] validieren.
- hier sollte ja eigentlich validiert werden welcher typ etwas ist und auch die kardinalität
- hierfür gibt es inverse Regeln, Diese könnte man ggf auch erzeugen
  bsp:

```
class1 m:0..1 -> m:2..n class2
    für class1 wird bis jetzt erstellt, dass sie:
        1. auf class2 zeigt (typecheck)
        2. auf max eine class2 zeigt (kardinalität/maxcount)
    für class2 könnte man jetzt erstellen, dass gecheckt wird, dass:
        1. mindestens 2 class1 objekte auf sie zeigen (inverseCadinality/inverse path)
            Das würde gehen über sh:inversePath
```
