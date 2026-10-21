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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelWriter;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiProfile;
import org.rdfarchitect.xmi.read.XmiReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What one profile of an export becomes.
 *
 * <p>Measured against the released RDFS by {@code XmiReproducesReleaseIT}, which is the test that
 * can say whether these rules are the rules the releases were made with. This one pins the shapes
 * that comparison cannot see — which namespace a resource lands in, which predicate a datatype
 * becomes, whether a stereotype is an IRI or a literal — against a fixture small enough to read.
 */
class XmiProfileMapperTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    private static final String MODEL_NS = "http://example.com/ns/CIM#";
    private static final String EXTENSION_NS = "http://example.com/ns/Extension#";

    private static XmiIndex index;

    @BeforeAll
    static void readTheExport() throws IOException {
        try (var file = Files.newInputStream(EXPORT)) {
            index = XmiReader.read(file);
        }
    }

    // -------------------------------------------------------------------------
    // Namespaces
    // -------------------------------------------------------------------------

    @Test
    void write_putsAClassInTheModelNamespace() {
        // The prefix table of the export states it; the package it sits in does not decide it.
        assertThat(
                        written("Topology")
                                .contains(resource(MODEL_NS + "Terminal"), RDF.type, RDFS.Class))
                .isTrue();
    }

    @Test
    void write_putsThePackageInTheProfileNamespace() {
        // A 3.0 profile states a versionIRI, and the namespace is what is left once the version
        // segment is replaced — the release writes Package_<Name> under exactly that.
        assertThat(
                        written("CoreEquipment-EU")
                                .contains(
                                        resource(
                                                "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU#Package_CoreEquipment-EU"),
                                        RDF.type,
                                        CIMS.classCategory))
                .isTrue();
    }

    @Test
    void write_putsAPackageWithoutAProfileNamespaceInTheModelOne() {
        // CGMES 2.4.15 states no profile namespace at all, and its release does the same.
        assertThat(
                        written("Topology")
                                .contains(
                                        resource(MODEL_NS + "Package_Topology"),
                                        RDF.type,
                                        CIMS.classCategory))
                .isTrue();
    }

    @Test
    void write_refusesAProfileWhoseExtensionNamespaceTheExportDoesNotState() {
        // Every CGMES 2.4.15 profile keeps its <Profile>Version class among the extension-
        // stereotyped ones, so degrading would drop the profile's own metadata without saying so.
        assertThatThrownBy(() -> write("Topology", null))
                .isInstanceOf(XmiImportRefusal.class)
                .hasMessageContaining("extension namespace")
                .hasMessageContaining("TopologicalNode");
    }

    @Test
    void write_putsAnExtensionStereotypedClassInTheExtensionNamespace() {
        assertThat(
                        written("Topology")
                                .contains(
                                        resource(EXTENSION_NS + "TopologicalNode"),
                                        RDF.type,
                                        RDFS.Class))
                .isTrue();
    }

    @Test
    void write_takesAMemberOfAnExtensionClassIntoTheExtensionNamespaceToo() {
        // The attribute carries no stereotype of its own; it follows the class owning it, which is
        // what the release does for every <Profile>Version and its ten attributes.
        assertThat(
                        written("Topology")
                                .contains(
                                        resource(EXTENSION_NS + "TopologicalNode.fromEndIsoCode"),
                                        RDF.type,
                                        RDF.Property))
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Resources
    // -------------------------------------------------------------------------

    @Test
    void write_givesAnAttributeTheStereotypeThatMakesItVisible() {
        var attribute = resource(EXTENSION_NS + "TopologicalNode.pInjection");
        var model = written("Topology");

        assertThat(model.contains(attribute, RDF.type, RDF.Property)).isTrue();
        assertThat(model.contains(attribute, CIMS.stereotype, CIMStereotypes.attribute)).isTrue();
        assertThat(
                        model.contains(
                                attribute, RDFS.domain, resource(EXTENSION_NS + "TopologicalNode")))
                .isTrue();
    }

    @Test
    void write_choosesTheDatatypePredicateByTheStereotypeOfTheType() {
        var model = written("Topology");

        // ActivePower is stereotyped Compound, which is a datatype and not a range.
        assertThat(
                        model.contains(
                                resource(EXTENSION_NS + "TopologicalNode.pInjection"),
                                CIMS.datatype,
                                resource(MODEL_NS + "ActivePower")))
                .isTrue();
        // UnitSymbol is an enumeration, so an attribute of that type takes one of its entries.
        assertThat(
                        model.contains(
                                resource(MODEL_NS + "ActivePower.unit"),
                                RDFS.range,
                                resource(MODEL_NS + "UnitSymbol")))
                .isTrue();
    }

    @Test
    void write_tellsAFixedValueFromADefaultOne() {
        var model = written("Topology");

        assertThat(model.listObjectsOfProperty(CIMS.isFixed).toList()).isNotEmpty();
        assertThat(
                        model.contains(
                                resource(EXTENSION_NS + "TopologicalNode.fromEndIsoCode"),
                                CIMS.isFixed,
                                "DE"))
                .isTrue();
        assertThat(model.contains(resource(MODEL_NS + "ActivePower.unit"), CIMS.isDefault, "W"))
                .isTrue();
    }

    @Test
    void write_statesAnEnumerationAsAnIriAndTypesItsEntriesByIt() {
        var model = written("Topology");

        // The export spells the stereotype as a bare word; stored as one it would mean nothing.
        assertThat(
                        model.contains(
                                resource(MODEL_NS + "UnitSymbol"),
                                CIMS.stereotype,
                                CIMStereotypes.enumeration))
                .isTrue();
        // A release writes no stereotype on an entry at all: being typed by its enumeration is
        // what makes it one.
        assertThat(model.contains(resource(MODEL_NS + "UnitSymbol.W"), CIMS.stereotype, "enum"))
                .isFalse();
        assertThat(
                        model.contains(
                                resource(MODEL_NS + "UnitSymbol.W"),
                                RDF.type,
                                resource(MODEL_NS + "UnitSymbol")))
                .isTrue();
    }

    @Test
    void write_invertsTheAbstractFlagAndLeavesADatatypeOutOfIt() {
        var model = written("Topology");

        assertThat(
                        model.contains(
                                resource(EXTENSION_NS + "TopologicalNode"),
                                CIMS.stereotype,
                                CIMStereotypes.concrete))
                .isTrue();
        assertThat(
                        model.contains(
                                resource(MODEL_NS + "IdentifiedObject"),
                                CIMS.stereotype,
                                CIMStereotypes.concrete))
                .isFalse();
        // A datatype never carries the flag, however concrete the export says it is.
        assertThat(
                        model.contains(
                                resource(MODEL_NS + "ActivePower"),
                                CIMS.stereotype,
                                CIMStereotypes.concrete))
                .isFalse();
    }

    @Test
    void write_makesBothHalvesOfAnAssociationOwnedByTheClassAtTheOtherEnd() {
        var model = written("Topology");
        var half = resource(EXTENSION_NS + "TopologicalNode.Terminals");

        // The end naming role "Terminals" sits on Terminal, so the half belongs to TopologicalNode
        // and ranges at Terminal. Backwards, every association in the graph points the wrong way.
        assertThat(model.contains(half, RDFS.domain, resource(EXTENSION_NS + "TopologicalNode")))
                .isTrue();
        assertThat(model.contains(half, RDFS.range, resource(MODEL_NS + "Terminal"))).isTrue();
        assertThat(
                        model.contains(
                                half,
                                CIMS.inverseRoleName,
                                resource(MODEL_NS + "Terminal.TopologicalNode")))
                .isTrue();
    }

    @Test
    void write_takesAssociationUsedFromTheNavigabilityOfTheEnd() {
        var model = written("Topology");

        assertThat(
                        model.contains(
                                resource(EXTENSION_NS + "TopologicalNode.Terminals"),
                                CIMS.associationUsed,
                                "No"))
                .isTrue();
        assertThat(
                        model.contains(
                                resource(MODEL_NS + "Terminal.TopologicalNode"),
                                CIMS.associationUsed,
                                "Yes"))
                .isTrue();
    }

    @Test
    void write_keepsAMultiplicityAsTheExportSpellsIt() {
        var model = written("Topology");

        // A release keeps M:1 and M:1..1 apart, so an end's multiplicity is not expanded.
        assertThat(
                        model.contains(
                                resource(EXTENSION_NS + "TopologicalNode.Terminals"),
                                CIMS.multiplicity,
                                resource(CIMS.namespace + "M:0..n")))
                .isTrue();
        assertThat(
                        model.contains(
                                resource(EXTENSION_NS + "TopologicalNode.pInjection"),
                                CIMS.multiplicity,
                                resource(CIMS.namespace + "M:0..n")))
                .isTrue();
    }

    @Test
    void write_bindsThePrefixesTheExportStates() {
        // Profile recognition depends on a bound cim prefix without saying so.
        assertThat(written("Topology").getNsPrefixURI("cim")).isEqualTo(MODEL_NS);
    }

    // -------------------------------------------------------------------------

    private static Model written(String profileName) {
        return write(profileName, EXTENSION_NS);
    }

    private static Model write(String profileName, String extensionNamespace) {
        var model = ModelFactory.createDefaultModel();
        XmiProfileMapper.write(
                index,
                profileNamed(profileName),
                new CIMModelWriter("g", model),
                extensionNamespace);
        return model;
    }

    private static XmiProfile profileNamed(String name) {
        return index.getProfiles().stream()
                .filter(profile -> name.equals(profile.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No profile named " + name));
    }

    private static Resource resource(String uri) {
        return ModelFactory.createDefaultModel().createResource(uri);
    }
}
