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

package org.rdfarchitect.xmi.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.xmi.map.XmiNamespaces.Supplied;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiReader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Where the three namespaces of a profile come from, and which of them a caller can overrule.
 *
 * <p>Read from exports written out here rather than from the shared fixture: what is being tested
 * is what an export does and does not state, and one file cannot state both.
 */
class XmiNamespacesTest {

    private static final String MODEL = "http://example.com/ns/CIM#";
    private static final String STATED_EXTENSION = "http://example.com/ns/EU#";
    private static final String SUPPLIED_EXTENSION = "http://example.com/ns/Supplied#";
    private static final String SUPPLIED_MODEL = "http://example.com/ns/SuppliedCIM#";

    @Test
    void of_takesTheExtensionNamespaceFromThePrefixTable() {
        var namespaces = withExtension(exportStating(STATED_EXTENSION), null);

        assertThat(namespaces.getExtension()).isEqualTo(STATED_EXTENSION);
    }

    @Test
    void of_leavesTheExtensionNamespaceAbsentWhereTheExportStatesNone() {
        var namespaces = withExtension(exportStating(null), null);

        assertThat(namespaces.hasExtension()).isFalse();
    }

    @Test
    void of_takesASuppliedExtensionNamespaceWhereTheExportStatesNone() {
        var namespaces = withExtension(exportStating(null), SUPPLIED_EXTENSION);

        assertThat(namespaces.getExtension()).isEqualTo(SUPPLIED_EXTENSION);
    }

    @Test
    void of_letsASuppliedExtensionNamespaceOverruleTheStatedOne() {
        // An export carries the namespace of the generation it was made in, which is not always
        // the one the schema is to be written in.
        var namespaces = withExtension(exportStating(STATED_EXTENSION), SUPPLIED_EXTENSION);

        assertThat(namespaces.getExtension()).isEqualTo(SUPPLIED_EXTENSION);
    }

    @Test
    void of_passesOverASuppliedExtensionNamespaceOfNothingButSpace() {
        var namespaces = withExtension(exportStating(STATED_EXTENSION), "   ");

        assertThat(namespaces.getExtension()).isEqualTo(STATED_EXTENSION);
    }

    @Test
    void extensionNamespaceOf_answersWithoutBeingGivenAProfile() {
        // What a caller shows before anything has been chosen.
        assertThat(XmiNamespaces.extensionNamespaceOf(exportStating(STATED_EXTENSION)))
                .isEqualTo(STATED_EXTENSION);
    }

    @Test
    void of_takesTheModelNamespaceFromThePrefixTable() {
        assertThat(withExtension(exportStating(null), null).getModel()).isEqualTo(MODEL);
    }

    @Test
    void of_letsASuppliedModelNamespaceOverruleTheStatedOne() {
        // Nearly every resource of a profile hangs on this one, so it is the one worth changing
        // when a schema is to be written in another generation's namespaces.
        var namespaces =
                namespacesOf(exportStating(STATED_EXTENSION), new Supplied(SUPPLIED_MODEL, null));

        assertThat(namespaces.getModel()).isEqualTo(SUPPLIED_MODEL);
        assertThat(namespaces.getExtension()).isEqualTo(STATED_EXTENSION);
    }

    @Test
    void of_takesBothWhereBothAreSupplied() {
        var namespaces =
                namespacesOf(
                        exportStating(STATED_EXTENSION),
                        new Supplied(SUPPLIED_MODEL, SUPPLIED_EXTENSION));

        assertThat(namespaces.getModel()).isEqualTo(SUPPLIED_MODEL);
        assertThat(namespaces.getExtension()).isEqualTo(SUPPLIED_EXTENSION);
    }

    @Test
    void hasExtensionElements_whenAClassCarriesTheStereotype() {
        assertThat(XmiNamespaces.hasExtensionElements(withStereotypeOn("CLASS"))).isTrue();
    }

    @Test
    void hasExtensionElements_whenAnAttributeCarriesIt() {
        // An attribute of an ordinary class, which needs the namespace all the same.
        assertThat(XmiNamespaces.hasExtensionElements(withStereotypeOn("ATTRIBUTE"))).isTrue();
    }

    @Test
    void hasExtensionElements_notForAnExportThatCarriesItNowhere() {
        // Then there is nothing the extension namespace would name, and nothing to ask for.
        assertThat(XmiNamespaces.hasExtensionElements(withStereotypeOn(null))).isFalse();
    }

    @Test
    void modelNamespaceOf_answersWithoutBeingGivenAProfile() {
        assertThat(XmiNamespaces.modelNamespaceOf(exportStating(null))).isEqualTo(MODEL);
    }

    // -------------------------------------------------------------------------

    private static XmiNamespaces withExtension(XmiIndex index, String suppliedExtension) {
        return namespacesOf(index, new Supplied(null, suppliedExtension));
    }

    private static XmiNamespaces namespacesOf(XmiIndex index, Supplied supplied) {
        return XmiNamespaces.of(index, index.getProfiles().getFirst(), supplied);
    }

    /**
     * One profile whose class, or whose attribute, carries the extension stereotype.
     *
     * <p>A stereotype is applied at the root of the file, one empty element per application, which
     * is the only place an element's complete set of them is written.
     *
     * @param on {@code "CLASS"}, {@code "ATTRIBUTE"} or {@code null} for neither
     */
    private static XmiIndex withStereotypeOn(String on) {
        var applied =
                switch (on == null ? "" : on) {
                    case "CLASS" -> "<thecustomprofile:European base_Class=\"CLASS\"/>";
                    case "ATTRIBUTE" -> "<thecustomprofile:Entsoe base_Property=\"A2\"/>";
                    default -> "";
                };
        var export =
                """
                <?xml version='1.0' encoding='UTF-8' ?>
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/20131001"
                         xmlns:uml="http://www.omg.org/spec/UML/20161101"
                         xmlns:thecustomprofile="http://www.sparxsystems.com/profiles/thecustomprofile/1.0">
                  <uml:Model xmi:type="uml:Model" name="EA_Model">
                    <packagedElement xmi:type="uml:Package" xmi:id="ROOT" name="ExampleStandard">
                      <packagedElement xmi:type="uml:Package" xmi:id="PROFILE" name="TopologyProfile">
                        <packagedElement xmi:type="uml:Class" xmi:id="VERSION" name="TopologyVersion">
                          <ownedAttribute xmi:type="uml:Property" xmi:id="A1" name="entsoeURI">
                            <defaultValue xmi:type="uml:LiteralString" xmi:id="V1"
                                          value="http://example.com/ns/Topology/1"/>
                          </ownedAttribute>
                        </packagedElement>
                        <packagedElement xmi:type="uml:Class" xmi:id="CLASS" name="TopologicalNode">
                          <ownedAttribute xmi:type="uml:Property" xmi:id="A2" name="shortName"/>
                        </packagedElement>
                      </packagedElement>
                    </packagedElement>
                  </uml:Model>
                  %s
                </xmi:XMI>
                """
                        .formatted(applied);
        return XmiReader.read(new ByteArrayInputStream(export.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * One profile and the class the prefix table hangs on, which is where CGMES 3.0 puts it.
     *
     * @param extensionNamespace what the table binds {@code eu} to, or {@code null} for an export
     *     that states no extension namespace at all
     */
    private static XmiIndex exportStating(String extensionNamespace) {
        var extension =
                extensionNamespace == null
                        ? ""
                        : """
                        <tag xmi:id="T3" name="nsprefix:eu" value="eu" modelElement="CARRIER"/>
                        <tag xmi:id="T4" name="nsuri:eu" value="%s" modelElement="CARRIER"/>
                        """
                                .formatted(extensionNamespace);
        var export =
                """
                <?xml version='1.0' encoding='UTF-8' ?>
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/20131001"
                         xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <uml:Model xmi:type="uml:Model" name="EA_Model">
                    <packagedElement xmi:type="uml:Package" xmi:id="ROOT" name="ExampleStandard">
                      <packagedElement xmi:type="uml:Package" xmi:id="PROFILE" name="TopologyProfile">
                        <packagedElement xmi:type="uml:Class" xmi:id="VERSION" name="TopologyVersion">
                          <ownedAttribute xmi:type="uml:Property" xmi:id="A1" name="entsoeURI">
                            <defaultValue xmi:type="uml:LiteralString" xmi:id="V1"
                                          value="http://example.com/ns/Topology/1"/>
                          </ownedAttribute>
                        </packagedElement>
                        <packagedElement xmi:type="uml:Class" xmi:id="CARRIER" name="Carrier"/>
                      </packagedElement>
                    </packagedElement>
                  </uml:Model>
                  <xmi:Extension extender="Enterprise Architect" extenderID="6.5">
                    <elements>
                      <element xmi:idref="CARRIER" xmi:type="uml:Class" name="Carrier" scope="public">
                        <tags>
                          <tag xmi:id="T1" name="nsprefix:cim" value="cim" modelElement="CARRIER"/>
                          <tag xmi:id="T2" name="nsuri:cim" value="%s" modelElement="CARRIER"/>
                          %s
                        </tags>
                      </element>
                    </elements>
                  </xmi:Extension>
                </xmi:XMI>
                """
                        .formatted(MODEL, extension);
        return XmiReader.read(new ByteArrayInputStream(export.getBytes(StandardCharsets.UTF_8)));
    }
}
