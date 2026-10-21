/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.xmi.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

class XmiReaderTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    private static XmiIndex index;

    @BeforeAll
    static void readTheExport() throws IOException {
        try (var file = Files.newInputStream(EXPORT)) {
            index = XmiReader.read(file);
        }
    }

    // -------------------------------------------------------------------------
    // Structure
    // -------------------------------------------------------------------------

    @Test
    void read_keepsThePackageNesting() {
        assertThat(index.ancestryOf("EAPK_TOPOLOGY_INNER"))
                .extracting(XmiPackage::getName)
                .containsExactly("TopologyCore", "Topology", "ExampleStandard");
    }

    @Test
    void read_takesAClassForTheInnermostPackageItSitsIn() {
        assertThat(classNamed("TopologicalNode").getPackageId()).isEqualTo("EAPK_TOPOLOGY_INNER");
    }

    @Test
    void read_takesTheDocumentationOfAPackage() {
        // A package's extension entry carries no class, so a reader that only documents classes
        // leaves every package undocumented.
        assertThat(index.getPackages().get("EAPK_TOPOLOGY").getDocumentation())
                .isEqualTo("Everything a topology is made of.");
    }

    @Test
    void read_takesTheTaggedValuesOfAPackage() {
        // This is where a package states a namespace of its own.
        assertThat(index.getPackages().get("EAPK_TOPOLOGY").getTags())
                .containsEntry("nsuri", "http://example.com/ns/Topology#")
                .containsEntry("nsprefix", "tp");
    }

    @Test
    void read_takesThePrefixTableFromWhateverCarriesIt() {
        // CGMES 3.0 hangs the whole table on one ordinary class that belongs to no profile, so
        // reading tags off packages alone finds none of it.
        assertThat(index.getPrefixes()).containsEntry("cim", "http://example.com/ns/CIM#");
    }

    @Test
    void read_leavesAPrefixBindingThatIsMissingItsNamespaceOut() {
        // Both halves arrive separately and one without the other states nothing usable.
        assertThat(index.getPrefixes()).doesNotContainKey("lonely");
    }

    @Test
    void read_keepsThePrefixTableOutOfTheTagsOfItsCarrier() {
        // The table describes the export, not the class it happens to sit on.
        assertThat(index.getPackages().get("EAPK_TOPOLOGY").getTags())
                .doesNotContainKey("nsuri:cim");
    }

    @Test
    void read_dropsWhatTheExtensionKnowsIsNotAClass() {
        // TopologyOverview is a uml:Boundary the model part writes as a uml:Class.
        assertThat(classNames()).doesNotContain("TopologyOverview");
    }

    @Test
    void read_dropsAClassWhoseNameNoUriCanHold() {
        // The extension entry of this one carries no type at all, so the name is the backstop.
        assertThat(classNames()).doesNotContain("static power system model");
    }

    @Test
    void read_takesTheAbstractFlagFromTheExtension() {
        assertThat(classNamed("IdentifiedObject").isAbstract()).isTrue();
        assertThat(classNamed("TopologicalNode").isAbstract()).isFalse();
    }

    @Test
    void read_takesTheDocumentationFromTheExtension() {
        assertThat(classNamed("Terminal").getDocumentation())
                .isEqualTo("An electrical connection point.");
    }

    // -------------------------------------------------------------------------
    // Input a release does not produce
    // -------------------------------------------------------------------------

    @Test
    void read_doesNotLetAnUnknownAttributeSpeakForItsClass() {
        // The extension documents an attribute the model part never declared. Read as the class
        // around it, its properties would make an abstract class concrete.
        var read =
                readInline(
                        """
                        <packagedElement xmi:type="uml:Class" xmi:id="C1" name="Lonely"/>
                        </uml:Model>
                        <xmi:Extension extender="Enterprise Architect">
                          <elements>
                            <element xmi:idref="C1" xmi:type="uml:Class" name="Lonely">
                              <properties documentation="A class." isAbstract="true"/>
                              <attribute xmi:idref="NOTHING_DECLARED_THIS">
                                <properties isAbstract="false" documentation="Not the class's."/>
                              </attribute>
                            </element>
                          </elements>
                        </xmi:Extension>
                        """);

        var lonely = read.getClasses().get("C1");
        assertThat(lonely.isAbstract()).isTrue();
        assertThat(lonely.getDocumentation()).isEqualTo("A class.");
    }

    @Test
    void read_takesAPackagedElementWithoutATypeForNoneOfThem() {
        // A partial export may leave the type out. The scanner tolerates it, so this has to as
        // well, or the two passes disagree about what the same file holds.
        var read =
                readInline(
                        """
                        <packagedElement xmi:id="NO_TYPE" name="Untyped"/>
                        <packagedElement xmi:type="uml:Class" xmi:id="C1" name="Readable"/>
                        </uml:Model>
                        """);

        assertThat(read.getClasses()).containsOnlyKeys("C1");
        assertThat(read.getPackages()).isEmpty();
    }

    @Test
    void read_takesEveryGeneralizationAClassDeclares() {
        assertThat(classNamed("TopologicalNode").getSuperClassIds())
                .containsExactly("EAID_IDENTIFIEDOBJECT");
    }

    // -------------------------------------------------------------------------
    // Attributes
    // -------------------------------------------------------------------------

    @Test
    void read_leavesTheEndOfAnAssociationOutOfTheAttributes() {
        // TopologicalNode owns a third ownedAttribute, which carries an association.
        assertThat(classNamed("TopologicalNode").getAttributes())
                .extracting(XmiAttribute::getName)
                .containsExactly("pInjection", "fromEndIsoCode");
    }

    @Test
    void read_resolvesTheTypeOfAnAttribute() {
        assertThat(attributeNamed("TopologicalNode", "pInjection").getTypeId())
                .isEqualTo("EAID_ACTIVEPOWER");
    }

    @Test
    void read_takesBothSpellingsOfTheUnboundedUpperBound() {
        assertThat(attributeNamed("TopologicalNode", "pInjection").getUpperBound()).isEqualTo("n");
        assertThat(attributeNamed("TopologicalNode", "fromEndIsoCode").getUpperBound())
                .isEqualTo("n");
    }

    @Test
    void read_tellsAFixedValueFromADefaultOne() {
        // The value is in the model part and its changeability only in the extension, so reading
        // the model alone makes every fixed value a default one.
        assertThat(attributeNamed("TopologicalNode", "fromEndIsoCode").isFixed()).isTrue();
        assertThat(attributeNamed("ActivePower", "unit").isFixed()).isFalse();
    }

    @Test
    void read_takesTheDocumentationOfAnAttributeFromTheExtension() {
        assertThat(attributeNamed("TopologicalNode", "pInjection").getDocumentation())
                .isEqualTo("The active power injected into the network.");
    }

    // -------------------------------------------------------------------------
    // Stereotypes
    // -------------------------------------------------------------------------

    @Test
    void read_takesEveryCoOccurringStereotypeOfAClass() {
        // The stereotype attributes of the extension hold one value each and would lose one of
        // these two.
        assertThat(classNamed("TopologicalNode").getStereotypes())
                .containsExactlyInAnyOrder("Description", "Entsoe");
    }

    @Test
    void read_leavesTheUmlBookkeepingOut() {
        assertThat(classNamed("TopologicalNode").getStereotypes())
                .doesNotContain("GUIDBasedOn", "IsBasedOn");
    }

    @Test
    void read_takesTheStereotypeOfAnAttribute() {
        assertThat(attributeNamed("TopologicalNode", "pInjection").getStereotypes())
                .containsExactly("ShortCircuit");
    }

    @Test
    void read_takesTheStereotypeOfAnEnumeration() {
        assertThat(classNamed("UnitSymbol").getStereotypes()).containsExactly("enumeration");
    }

    // -------------------------------------------------------------------------
    // Enumerations
    // -------------------------------------------------------------------------

    @Test
    void read_takesTheLiteralsOfAnEnumeration() {
        var unitSymbol = classNamed("UnitSymbol");
        assertThat(unitSymbol.isEnumeration()).isTrue();
        assertThat(unitSymbol.getLiterals())
                .extracting(XmiEnumLiteral::getName)
                .containsExactly("W", "VA");
    }

    @Test
    void read_joinsTheDocumentationOfALiteralByItsId() {
        assertThat(classNamed("UnitSymbol").getLiterals().getFirst().getDocumentation())
                .isEqualTo("Watt.");
    }

    // -------------------------------------------------------------------------
    // Associations
    // -------------------------------------------------------------------------

    @Test
    void read_makesBothHalvesOfAPairFromTheOneConnector() {
        assertThat(index.getAssociationEnds())
                .extracting(end -> end.getOppositeClassName() + "." + end.getRoleName())
                .containsExactlyInAnyOrder(
                        "TopologicalNode.Terminals",
                        "Terminal.TopologicalNode",
                        "TopologicalNode.Readings",
                        "ActivePower.Node");
    }

    @Test
    void read_givesAHalfTheClassAtItsOwnEndAsTheRange() {
        var terminals = half("TopologicalNode.Terminals");
        assertThat(terminals.getClassName()).isEqualTo("Terminal");
        assertThat(terminals.getOppositeClassName()).isEqualTo("TopologicalNode");
    }

    @Test
    void read_attachesAHalfToTheClassItsEndSitsOn() {
        // The id is on the end itself; its model child names the class but repeats no id, so
        // reading the id from there leaves every half unattached to anything.
        assertThat(half("TopologicalNode.Terminals").getClassId()).isEqualTo("EAID_TERMINAL");
        assertThat(half("TopologicalNode.Terminals").getOppositeClassId())
                .isEqualTo("EAID_TOPOLOGICALNODE");
    }

    @Test
    void read_takesTheMultiplicityOfTheEndItself() {
        assertThat(half("TopologicalNode.Terminals").getMultiplicity()).isEqualTo("0..n");
        assertThat(half("ActivePower.Node").getMultiplicity()).isEqualTo("1");
    }

    @Test
    void read_keepsAMultiplicityThatStatesNoUpperBound() {
        // One connector end of each full model reads "1..", which states a lower bound and no
        // upper one.
        assertThat(half("Terminal.TopologicalNode").getMultiplicity()).isEqualTo("1");
    }

    @Test
    void read_readsNavigabilityFromTheStyleAndNotFromTheModifiers() {
        // Both ends of the first connector have the two disagree; only the style reproduces a
        // release.
        assertThat(half("TopologicalNode.Terminals").isNavigable()).isFalse();
        assertThat(half("Terminal.TopologicalNode").isNavigable()).isTrue();
    }

    @Test
    void read_countsAnAggregationAsAnAssociation() {
        assertThat(half("TopologicalNode.Readings").getAggregation()).isEqualTo("composite");
    }

    @Test
    void read_leavesAConnectorThatIsNotAnAssociationOut() {
        assertThat(index.getAssociationEnds())
                .extracting(XmiAssociationEnd::getConnectorId)
                .doesNotContain("EAID_NOTE01");
    }

    @Test
    void read_takesTheDocumentationOfTheEndItself() {
        assertThat(half("TopologicalNode.Terminals").getDocumentation())
                .isEqualTo("The terminals of the topological node.");
    }

    @Test
    void read_givesBothHalvesTheStereotypesOfTheirAssociation() {
        // An association is stereotyped once, and the applications arrive after the extension has
        // already made the two halves.
        assertThat(half("TopologicalNode.Terminals").getStereotypes())
                .containsExactly("NC", "deprecated");
        assertThat(half("Terminal.TopologicalNode").getStereotypes())
                .containsExactly("NC", "deprecated");
    }

    @Test
    void read_leavesAnAssociationNobodyStereotypedAlone() {
        assertThat(half("TopologicalNode.Readings").getStereotypes()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Diagrams
    // -------------------------------------------------------------------------

    @Test
    void read_takesTheClassDiagramsAndNothingElse() {
        // A package diagram has no counterpart, because a package here is a diagram rather than a
        // node on one.
        assertThat(index.getDiagrams()).containsOnlyKeys("EAID_DIAG_TP");
    }

    @Test
    void read_takesTheNameOfADiagramFromTheVendorExtension() {
        // UMLDI states no name anywhere.
        assertThat(diagram().getName()).isEqualTo("Topology overview");
    }

    @Test
    void read_attributesADiagramToThePackageItIsDeclaredIn() {
        assertThat(diagram().getOwningPackageId()).isEqualTo("EAPK_TOPOLOGY");
    }

    @Test
    void read_placesEveryClassDrawnOnADiagram() {
        assertThat(diagram().getShapes())
                .extracting(XmiShape::getClassId, XmiShape::getX, XmiShape::getY)
                .containsExactly(
                        tuple("EAID_TOPOLOGICALNODE", 100, 200), tuple("EAID_TERMINAL", 300, 400));
    }

    @Test
    void read_doesNotLetALabelInsideAShapePlaceTheShape() {
        // The keyword label of TopologicalNode carries bounds of its own, and ownedElement nests,
        // so whichever element is innermost is the one a bounds belongs to.
        assertThat(shapeOf("EAID_TOPOLOGICALNODE").getX()).isNotEqualTo(999);
    }

    @Test
    void read_leavesAShapeThatCarriesNoClassifierOut() {
        // UMLShape is used for notes, boundaries and text alike.
        assertThat(diagram().getShapes())
                .extracting(XmiShape::getClassId)
                .doesNotContain("EAID_BOUNDARY");
    }

    @Test
    void read_takesTheZOrderOfAShapeFromTheVendorExtension() {
        // UMLDI has no seqno anywhere, and the extension's list is in no particular order.
        assertThat(shapeOf("EAID_TOPOLOGICALNODE").getZPosition()).isEqualTo(2);
        assertThat(shapeOf("EAID_TERMINAL").getZPosition()).isEqualTo(1);
    }

    @Test
    void read_placesTheLabelsOfAnAssociationEnd() {
        assertThat(diagram().getLabels())
                .extracting(
                        XmiLabel::getConnectorId,
                        XmiLabel::isAtSourceEnd,
                        XmiLabel::getKind,
                        XmiLabel::getX,
                        XmiLabel::getY)
                .containsExactly(
                        tuple("EAID_ASSOC01", true, XmiLabel.Kind.ASSOCIATION_END, 10, 20),
                        tuple("EAID_ASSOC01", false, XmiLabel.Kind.MULTIPLICITY, 30, 40));
    }

    // -------------------------------------------------------------------------
    // The closure
    // -------------------------------------------------------------------------

    @Test
    void slice_takesTheSubPackagesOfAProfileAlong() {
        assertThat(sliceOfTopology())
                .contains("IdentifiedObject", "TopologicalNode", "Terminal", "String");
    }

    @Test
    void slice_copiesInTheClassifiersTheProfileRefersTo() {
        // ActivePower types an attribute of TopologicalNode, and Float and UnitSymbol in turn type
        // attributes of ActivePower. Without the closure a 2.4.15 profile has no datatypes at all.
        assertThat(sliceOfTopology()).contains("ActivePower", "Float", "UnitSymbol");
    }

    @Test
    void slice_leavesAProfileItDoesNotReferToAlone() {
        assertThat(sliceOfTopology()).doesNotContain("MesswerteVersion", "DomainVersion");
    }

    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // What the export refers to but does not declare
    // -------------------------------------------------------------------------

    @Test
    void read_stripsTheTrailingSpaceAnExportPutsOnAMemberName() {
        // Four attributes of the CGMES 3.0 model are named "lossPercentage " and mean the name
        // without it.
        assertThat(classNamed("Reservoir").getAttributes())
                .extracting(XmiAttribute::getName)
                .contains("spillwayCapacity");
    }

    @Test
    void read_dropsAMemberWhoseNameNoUriCanHold() {
        // Fourteen enum literals of the CGMES 3.0 model are named things like "TB 170". Trimming
        // cannot save those, and a name no URI accepts costs the whole schema it sits in.
        assertThat(classNamed("UnitSymbol").getLiterals())
                .extracting(XmiEnumLiteral::getName)
                .doesNotContainNull()
                .doesNotContain("TB 170");
    }

    @Test
    void read_keepsTheNameOfAnElementTheExportDoesNotContain() {
        // A partial export writes an EAStub per missing target. It is the only thing that can say
        // what an attribute wanted to be typed with.
        assertThat(index.getStubNames()).containsEntry("EAID_GONE", "Volume");
    }

    @Test
    void read_doesNotMakeAClassOutOfAStub() {
        // A stub carries no stereotypes and no package, so neither a namespace nor a datatype kind
        // follows from it. Written as a class it would be a guess under a real name.
        assertThat(classNames()).doesNotContain("Volume");
    }

    @Test
    void read_takesTheDatatypesEnterpriseArchitectKeepsOfItsOwn() {
        // They sit in the extension rather than in the model section and are referenced from it.
        assertThat(index.getPrimitiveTypeNames()).containsEntry("EAJava_DateTime", "DateTime");
    }

    @Test
    void read_doesNotMakeAClassOutOfAPrimitiveType() {
        // A model commonly declares a class of the same name, and two definitions under one uri
        // would write each other's triples.
        assertThat(classNames()).doesNotContain("DateTime");
    }

    // -------------------------------------------------------------------------

    private static java.util.List<String> classNames() {
        return index.getClasses().values().stream().map(XmiClass::getName).toList();
    }

    private static java.util.List<String> sliceOfTopology() {
        return XmiProfileSlice.classIdsOf(index, "EAPK_TOPOLOGY").stream()
                .map(id -> index.getClasses().get(id).getName())
                .toList();
    }

    /**
     * Reads an export made of {@code body}, which opens inside {@code uml:Model} and closes it
     * itself. For input a release does not produce and the shared fixture should not pretend to be.
     */
    private static XmiIndex readInline(String body) {
        var export =
                """
                <?xml version='1.0' encoding='windows-1252' ?>
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/20131001"\
                 xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <uml:Model xmi:type="uml:Model" name="EA_Model">
                """
                        + body
                        + "</xmi:XMI>";
        try (var stream =
                new ByteArrayInputStream(export.getBytes(Charset.forName("windows-1252")))) {
            return XmiReader.read(stream);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static XmiDiagram diagram() {
        return index.getDiagrams().get("EAID_DIAG_TP");
    }

    private static XmiShape shapeOf(String classId) {
        return diagram().getShapes().stream()
                .filter(shape -> classId.equals(shape.getClassId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nothing was drawn for " + classId));
    }

    private static XmiClass classNamed(String name) {
        return index.getClasses().values().stream()
                .filter(cimClass -> name.equals(cimClass.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No class named " + name + " was read."));
    }

    private static XmiAttribute attributeNamed(String className, String attributeName) {
        return classNamed(className).getAttributes().stream()
                .filter(attribute -> attributeName.equals(attribute.getName()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "No attribute " + className + "." + attributeName));
    }

    /** The half a {@code Domain.role} name stands for. */
    private static XmiAssociationEnd half(String name) {
        return index.getAssociationEnds().stream()
                .filter(end -> name.equals(end.getOppositeClassName() + "." + end.getRoleName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No association half named " + name));
    }
}
