# RDF-Core SHACL-Generierung:

Shapes werden generiert für alle konkreten Klassen sowie abstrakte Klassen, die keine weiteren Unterklassen haben.<br>
Für jede Klasse wird eine Node Shape erzeugt. Diese enthält:

- Die Klasse als TargetClass
- die Bedingung, dass die Shape closed ist
- eventuell eine Liste von ignoredProperties, falls diese an den Konstruktor des ShaclShapeService übergeben wurde
- eine Property Shape für jedes Attribut der Klasse (außer denen in ignoredProperties)
  Die Property Shapes enthalten:
- Die URI des Attributs als Path
- Eine Constraint zur Validierung des Datentyps
    - Bei Assoziationen: nodeKind muss IRI sein
        - Anscheinend keine sh:class Constraint?
    - Bei Enums: nodeKind muss IRI sein und Wert muss in der Liste der gültigen Werte für das enum sein
    - Bei anderen Fällen (primitive Datentypen): datatype ist korrekter XsdDatatype
- Eine Constraint zur Validierung der Multiplizität
    - minCount und/oder maxCount auf 1
    - Unterstützt nur Multiplizitäten 1..1, 1, 0..1, 1..n, nicht z.B. 2..n!

# CimPal-Generierung:

Kein sh:closed in generierten Regeln<br>
Eine Node Shape pro Klasse, umfasst mehrere Property Shapes<br>
Teilweise zusätzliche Node Shapes (für InverseAssociations sowie AllowedClasses)<br>
Property Shapes sind mit sh:group gruppiert<br>
Generierte Groups:<br>

- ProfileClassesGroup
    - Eine Shape, die validiert, dass nur Klassen im Graph enthalten sind, die auch im Schema enthalten sind (mit sh:
      Info)
- AssociationsGroup
    - Shapes, die den Value Type von Assoziationen überprüfen, mit sh:class-Regel
        - Laut Optionen mehrere Möglichkeiten
        - Standardmäßig mit sh:or und sh:class
        - Alternativ mit sh:in, dabei Zusatzoption für sh:class, wenn es nur eine Möglichkeit gibt
- DatatypesGroup
    - Shapes, die den dataType von primitiven Datentypen validieren, mit sh:datatype
- CardinalityGroup
    - Shapes, die Kardinalität validieren
    - Sowohl für Attribute als auch Assoziationen
    - In Test mit TopologyProfile nur maxCount 1 oder minCount und maxCount beide 1, unklar, was alles erlaubt ist
- InverseAssociationsGroup
    - Validiert, dass inverse Assoziationen nicht verwendet werden (mit sh:maxCount 0)

Bei Testversuch mit EquipmentProfile immer Exception aufgetreten, eventuell wegen falscher Einstellungen<br>
Option, Klassenhierarchie als RDF-Datei mit zu exportieren<br>
Option, Datatypes Map zu exportieren<br>
Option zum Export als TTL oder RDFXML<br>
Option, ob auch für Attribute und Assoziationen der Superklasse Regeln generiert werden sollen<br>
Option, zusätzliche Profile für Value Type Shapes mit einzubeziehen<br>
Unklar, wie enums behandelt werden (keine in Topology-Profil vorhanden)<br>
Diverse Optionen zur Auswahl der Profilversion, RDFS-Format, freie Auswahl von Namespace und baseURI für Shacl-Regeln

RDF-Vergleich: Tabelle mit Subjekt, Type des Subjekts, Property und zwei Objektspalten für Wert in beiden Graphen

SHACL Shape Browser: Browser mit Baumstruktur von Shapes, wirft bei Versuch, einzelne Shape anzuklicken, Exception

Eigene Gruppen für Regeln, die sich auf IdentifiedObject beziehen (eventuell abhängig von Einstellungen)