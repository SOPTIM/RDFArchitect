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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

    private static java.util.List<String> classNames() {
        return index.getClasses().values().stream().map(XmiClass::getName).toList();
    }

    private static java.util.List<String> sliceOfTopology() {
        return XmiProfileSlice.classIdsOf(index, "EAPK_TOPOLOGY").stream()
                .map(id -> index.getClasses().get(id).getName())
                .toList();
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
