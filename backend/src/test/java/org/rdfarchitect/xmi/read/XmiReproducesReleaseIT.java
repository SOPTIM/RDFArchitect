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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reads the CGMES 2.4.15 UML and checks that each profile comes out as the RDFS released for it.
 *
 * <p>This is the measurement the import rests on, and the only one that can tell whether a rule
 * derived from the export is the rule the release was made with. Compared are the names of the
 * classes, attributes and association halves a profile consists of — not every triple, so a mapping
 * rule can still be wrong where the element selection is right.
 *
 * <p>The exports are a 175 MB ENTSO-E download that the repository does not carry, so the test
 * skips itself when they are not there. The RDFS come from the {@code
 * entsoe-application-profiles-library} submodule.
 */
class XmiReproducesReleaseIT {

    private static final Path EXPORT =
            Path.of("../external/cgmes-xmi/cgmes-2.4.15-profiles-xmi2.5.1.xml");

    private static final Path RELEASED =
            Path.of(
                    "../external/entsoe-application-profiles-library/CGMES/PastReleases/v2-4/Original/RDFS");

    private static final String CIMS = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";

    private static XmiIndex index;

    @BeforeAll
    static void readTheExport() throws IOException {
        assumeTrue(Files.exists(EXPORT), "The Enterprise Architect exports are not available.");
        assumeTrue(Files.isDirectory(RELEASED), "The released RDFS are not available.");
        try (var file = Files.newInputStream(EXPORT)) {
            index = XmiReader.read(file);
        }
    }

    /**
     * The Equipment profile is left out on purpose: the release ships it as four combinations
     * formed from its sub-profile stereotypes, so there is no single file to compare it against.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "EquipmentBoundaryProfile, EquipmentBoundaryProfile",
        "TopologyBoundaryProfile, TopologyBoundaryProfile",
        "TopologyProfile, TopologyProfile",
        "SteadyStateHypothesisProfile, SteadyStateHypothesisProfile",
        "StateVariablesProfile, StateVariableProfile",
        "DynamicsProfile, DynamicsProfile",
        "GeographicalLocationProfile, GeographicalLocationProfile",
        "DiagramLayoutProfile, DiagramLayoutProfile"
    })
    void profileComesOutAsTheRdfsReleasedForIt(String profileName, String releasedName)
            throws IOException {
        var profile =
                index.getProfiles().stream()
                        .filter(candidate -> profileName.equals(candidate.name()))
                        .findFirst()
                        .orElseThrow();
        var slice = XmiProfileSlice.classIdsOf(index, profile.id());
        var released = releasedModel(releasedName);

        assertThat(classNames(slice)).isEqualTo(releasedClasses(released));
        assertThat(attributeNames(slice)).isEqualTo(releasedProperties(released, false));
        assertThat(associationNames(slice)).isEqualTo(releasedProperties(released, true));
    }

    // -------------------------------------------------------------------------
    // What the export says
    // -------------------------------------------------------------------------

    private static Set<String> classNames(Set<String> slice) {
        return slice.stream()
                .map(id -> index.getClasses().get(id).getName())
                .collect(TreeSet::new, Set::add, Set::addAll);
    }

    private static Set<String> attributeNames(Set<String> slice) {
        return slice.stream()
                .map(id -> index.getClasses().get(id))
                .flatMap(
                        cimClass ->
                                cimClass.getAttributes().stream()
                                        .map(
                                                attribute ->
                                                        cimClass.getName()
                                                                + "."
                                                                + attribute.getName()))
                .collect(TreeSet::new, Set::add, Set::addAll);
    }

    /** A half belongs to the profile when both of its classes do. */
    private static Set<String> associationNames(Set<String> slice) {
        return index.getAssociationEnds().stream()
                .filter(
                        end ->
                                slice.contains(end.getClassId())
                                        && slice.contains(end.getOppositeClassId()))
                .map(end -> end.getOppositeClassName() + "." + end.getRoleName())
                .collect(TreeSet::new, Set::add, Set::addAll);
    }

    // -------------------------------------------------------------------------
    // What the release says
    // -------------------------------------------------------------------------

    private static Model releasedModel(String releasedName) throws IOException {
        Path file;
        try (Stream<Path> files = Files.list(RELEASED)) {
            file =
                    files.filter(
                                    candidate ->
                                            candidate
                                                    .getFileName()
                                                    .toString()
                                                    .startsWith(releasedName + "RDFS"))
                            .findFirst()
                            .orElseThrow();
        }
        var model = ModelFactory.createDefaultModel();
        try (var content = Files.newInputStream(file)) {
            model.read(content, null, "RDF/XML");
        }
        return model;
    }

    private static Set<String> releasedClasses(Model released) {
        var names = new TreeSet<String>();
        released.listSubjectsWithProperty(RDF.type, RDFS.Class)
                .forEachRemaining(resource -> names.add(resource.getLocalName()));
        return names;
    }

    /** A released property is an association exactly when it states whether it is used. */
    private static Set<String> releasedProperties(Model released, boolean associations) {
        var associationUsed = released.createProperty(CIMS, "AssociationUsed");
        var names = new TreeSet<String>();
        released.listSubjectsWithProperty(RDF.type, RDF.Property)
                .filterKeep(resource -> resource.hasProperty(associationUsed) == associations)
                .forEachRemaining(resource -> names.add(resource.getLocalName()));
        return names;
    }
}
