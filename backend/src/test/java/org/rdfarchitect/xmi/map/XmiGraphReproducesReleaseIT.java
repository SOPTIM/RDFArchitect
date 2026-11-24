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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelWriter;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiProfile;
import org.rdfarchitect.xmi.read.XmiReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Builds a graph from the CGMES 2.4.15 UML and compares it with the RDFS released for the same
 * profile, triple for triple.
 *
 * <p>This is the measurement the import rests on. {@code XmiReproducesReleaseIT} already compares
 * the four sets of <em>names</em> a profile consists of, which says the right elements were
 * selected; this says the right triples were written about them. The difference is not academic:
 * every defect found while building the mapping put a resource under the right name in the wrong
 * namespace, which a comparison on names cannot see.
 *
 * <h2>What is compared, and what is not</h2>
 *
 * Every triple, by full URI, with three deliberate exceptions:
 *
 * <ul>
 *   <li>{@code rdfa:uuid} — RDFArchitect's own identity, which a release has no concept of.
 *   <li>{@code cims:belongsToCategory} between two packages — the nesting, which the importer
 *       writes and a release does not. It is the one thing written here on purpose that no release
 *       contains.
 *   <li>the <em>text</em> of {@code rdfs:comment}. That a comment is there is compared; what it
 *       says is not. The export and the release disagree about trailing whitespace, and in a
 *       handful of places the export is simply damaged — one documentation string in the 2.4.15
 *       model carries {@code href="{???????????????????????????}???')"} where the release has the
 *       URL. Neither is the mapping's doing and neither can it repair.
 * </ul>
 *
 * <p>The exports are a 175 MB ENTSO-E download the repository does not carry, so this skips itself
 * when they are not there.
 */
class XmiGraphReproducesReleaseIT {

    private static final Path EXPORT =
            Path.of("../external/cgmes-xmi/cgmes-2.4.15-fullmodel-xmi2.5.1.xml");

    private static final Path RELEASED =
            Path.of(
                    "../external/entsoe-application-profiles-library/CGMES/PastReleases/v2-4/Original/RDFS");

    /** What the release writes relative to the file, which is the model namespace of CGMES 16. */
    private static final String MODEL_NAMESPACE = "http://iec.ch/TC57/2013/CIM-schema-cim16#";

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
     * The Equipment profile is left out for the same reason the name comparison leaves it out: the
     * release ships it as four combinations formed from its sub-profile stereotypes, so there is no
     * single file to compare it against.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "EquipmentBoundaryProfile, EquipmentBoundaryProfile",
        "TopologyBoundaryProfile, TopologyBoundaryProfile",
        "TopologyProfile, TopologyProfile",
        "SteadyStateHypothesisProfile, SteadyStateHypothesisProfile",
        "StateVariablesProfile, StateVariableProfile",
        "GeographicalLocationProfile, GeographicalLocationProfile",
        "DiagramLayoutProfile, DiagramLayoutProfile"
    })
    void profileIsWrittenAsTheRdfsReleasedForIt(String profileName, String releasedName)
            throws IOException {
        var built = ModelFactory.createDefaultModel();
        XmiProfileMapper.write(index, profileNamed(profileName), new CIMModelWriter("g", built));
        var released = releasedModel(releasedName);

        assertThat(comparable(built)).containsExactlyInAnyOrderElementsOf(comparable(released));
    }

    // -------------------------------------------------------------------------

    /**
     * A model as the set of triples worth comparing, each written out so that a mismatch reads as
     * the triple it is rather than as an object identity.
     */
    private static Set<String> comparable(Model model) {
        var triples = new TreeSet<String>();
        model.listStatements()
                .filterKeep(statement -> statement.getSubject().isURIResource())
                .filterDrop(XmiGraphReproducesReleaseIT::isNotCompared)
                .forEachRemaining(statement -> triples.add(asText(statement)));
        return triples;
    }

    private static boolean isNotCompared(Statement statement) {
        return statement.getPredicate().equals(RDFA.uuid) || isPackageNesting(statement);
    }

    /** A package pointing at the package containing it, which only the importer writes. */
    private static boolean isPackageNesting(Statement statement) {
        return statement.getPredicate().equals(CIMS.belongsToCategory)
                && statement.getSubject().isURIResource()
                && statement.getSubject().getLocalName().startsWith("Package_");
    }

    private static String asText(Statement statement) {
        return resolved(statement.getSubject().getURI())
                + " "
                + statement.getPredicate().getURI()
                + " "
                + objectText(statement);
    }

    /**
     * What the object of a triple is compared as. A comment is compared by its presence alone; see
     * the class javadoc for why its text is not.
     */
    private static String objectText(Statement statement) {
        if (statement.getPredicate().equals(RDFS.comment)) {
            return "<a comment>";
        }
        var object = statement.getObject();
        return object.isURIResource()
                ? resolved(object.asResource().getURI())
                : "\"" + object.asLiteral().getLexicalForm() + "\"";
    }

    /** A release writes the model namespace as a bare {@code #}, relative to the file. */
    private static String resolved(String uri) {
        return uri.startsWith("#") ? MODEL_NAMESPACE + uri.substring(1) : uri;
    }

    private static XmiProfile profileNamed(String name) {
        return index.getProfiles().stream()
                .filter(profile -> name.equals(profile.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No profile named " + name));
    }

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
            // Read against an empty base so that the release's relative URIs stay relative and can
            // be resolved onto the model namespace, which is what they mean.
            model.read(content, "", "RDF/XML");
        }
        return model;
    }
}
