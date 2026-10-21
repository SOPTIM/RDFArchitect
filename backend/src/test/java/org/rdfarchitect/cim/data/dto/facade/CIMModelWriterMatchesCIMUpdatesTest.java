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

package org.rdfarchitect.cim.data.dto.facade;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.queries.update.cimupdates.CIMUpdatesTestBase;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelWriter;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.util.List;
import java.util.UUID;

/**
 * Holds {@link CIMModelWriter} and {@link CIMUpdates} to the same triples.
 *
 * <p>The two write a profile graph by different means — a model against SPARQL — and the rules
 * about what a triple looks like are therefore stated twice. Where those rules drift, an imported
 * graph and an edited one differ without anything failing, which is the kind of defect that is
 * found a year later. This test is the reason the duplication is acceptable until the editing
 * services move onto the writer.
 *
 * <p>One difference is deliberate and asserted as such: the writer writes a package's parent and
 * {@code CIMUpdates} does not.
 */
class CIMModelWriterMatchesCIMUpdatesTest extends CIMUpdatesTestBase {

    private static final String SECOND_SUPER_CLASS_URI = "http://example.com#secondSuper";
    private static final String ENUM_URI = URI_PREFIX + "unitSymbol";

    @Test
    @DisplayName("Writes a class exactly as CIMUpdates does, superclasses and stereotypes included")
    void writeClass_matchesCIMUpdates() {
        var cimClass =
                CIMClass.builder()
                        .uuid(MY_UUID)
                        .uri(new URI(CLASS_URI))
                        .label(new RDFSLabel(CLASS_LABEL, "en"))
                        .superClasses(
                                List.of(
                                        new RDFSSubClassOf(
                                                new URI(SUPER_CLASS_URI),
                                                new RDFSLabel(SUPER_CLASS_LABEL, "en")),
                                        new RDFSSubClassOf(
                                                new URI(SECOND_SUPER_CLASS_URI),
                                                new RDFSLabel("secondSuper", "en"))))
                        .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                        .belongsToCategory(
                                new CIMSBelongsToCategory(
                                        new URI(PACKAGE_URI),
                                        new RDFSLabel(PACKAGE_LABEL),
                                        UUID.randomUUID()))
                        // One IRI-valued and one literal-valued stereotype, because the choice
                        // between the two is the rule most easily copied wrong.
                        .stereotypes(
                                List.of(
                                        new CIMSStereotype(CIMStereotypes.concrete.toString()),
                                        new CIMSStereotype("Entsoe")))
                        .build();

        assertSameTriples(
                writtenByWriter(writer -> writer.writeClass(cimClass)),
                writtenByUpdates(
                        graph ->
                                CIMUpdates.insertClass(
                                        graph,
                                        databasePort.getPrefixMapping(DATASET_NAME),
                                        cimClass)));
    }

    @Test
    @DisplayName("Writes an enum entry exactly as CIMUpdates does")
    void writeEnumEntry_matchesCIMUpdates() {
        var enumEntry =
                CIMEnumEntry.builder()
                        .uuid(MY_UUID)
                        .uri(new URI(ENUM_URI + "." + ENUM_ENTRY_LABEL))
                        .type(new RDFType(new URI(ENUM_URI), new RDFSLabel("unitSymbol", "en")))
                        .label(new RDFSLabel(ENUM_ENTRY_LABEL, "en"))
                        .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                        .stereotype(new CIMSStereotype("enum"))
                        .build();

        assertSameTriples(
                writtenByWriter(writer -> writer.writeEnumEntry(enumEntry)),
                writtenByUpdates(
                        graph ->
                                CIMUpdates.insertEnumEntry(
                                        graph,
                                        databasePort.getPrefixMapping(DATASET_NAME),
                                        enumEntry)));
    }

    @Test
    @DisplayName("Writes a package as CIMUpdates does, and its parent on top")
    void writePackage_matchesCIMUpdatesAndAddsTheParent() {
        var parentUri = URI_PREFIX + OTHER_PACKAGE_LABEL;
        var cimPackage =
                CIMPackage.builder()
                        .uuid(MY_UUID)
                        .uri(new URI(PACKAGE_URI))
                        .label(new RDFSLabel(PACKAGE_LABEL, "en"))
                        .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                        .belongsToCategory(
                                new CIMSBelongsToCategory(
                                        new URI(parentUri),
                                        new RDFSLabel(OTHER_PACKAGE_LABEL),
                                        UUID.randomUUID()))
                        .build();

        var written = writtenByWriter(writer -> writer.writePackage(cimPackage));
        var byUpdates =
                writtenByUpdates(
                        graph ->
                                CIMUpdates.insertPackage(
                                        graph,
                                        databasePort.getPrefixMapping(DATASET_NAME),
                                        cimPackage));

        // The parent is the one triple the editing path does not keep.
        assertThat(
                        written.contains(
                                written.createResource(PACKAGE_URI),
                                CIMS.belongsToCategory,
                                written.createResource(parentUri)))
                .isTrue();
        assertThat(
                        byUpdates.contains(
                                null,
                                CIMS.belongsToCategory,
                                (org.apache.jena.rdf.model.RDFNode) null))
                .isFalse();

        written.remove(
                written.createResource(PACKAGE_URI),
                CIMS.belongsToCategory,
                written.createResource(parentUri));
        assertSameTriples(written, byUpdates);
    }

    @Test
    @DisplayName("Writes the mandatory attribute stereotype on an attribute")
    void writeAttribute_writesTheStereotypeThatMakesItVisible() {
        var attribute = attributeFixture();

        var written = writtenByWriter(writer -> writer.writeAttribute(attribute));

        // findUndisplayableProperties reports a domain-bound property without this as stored but
        // invisible, so its absence would be silent.
        assertThat(
                        written.contains(
                                written.createResource(attribute.getUri().toString()),
                                CIMS.stereotype,
                                CIMStereotypes.attribute))
                .isTrue();
        // The import mints uuids itself and needs them back; CIMUpdates mints its own instead.
        assertThat(
                        written.contains(
                                written.createResource(attribute.getUri().toString()),
                                RDFA.uuid,
                                MY_UUID.toString()))
                .isTrue();
        assertThat(
                        written.contains(
                                written.createResource(attribute.getUri().toString()),
                                RDFS.domain,
                                written.createResource(CLASS_URI)))
                .isTrue();
    }

    @Test
    @DisplayName(
            "Writes an attribute exactly as CIMUpdates does, datatype and fixed value included")
    void writeAttribute_matchesCIMUpdates() {
        var attribute = attributeFixture();

        // insertAttribute resolves the value shape against the graph, so it has to be built inside
        // the transaction that executes it.
        executeUpdateBuiltAgainstTestGraph(
                graph ->
                        new UpdateRequest()
                                .add(
                                        CIMUpdates.insertAttribute(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        GRAPH_URI,
                                                        attribute,
                                                        false)
                                                .build()));

        assertSameTriples(
                writtenByWriter(writer -> writer.writeAttribute(attribute)), currentTestGraph());
    }

    @Test
    @DisplayName("Writes both halves of an association exactly as CIMUpdates does")
    void writeAssociationPair_matchesCIMUpdates() {
        var pair = associationPairFixture();

        executeUpdateOnTestGraph(
                CIMUpdates.insertAssociation(
                                databasePort.getPrefixMapping(DATASET_NAME), GRAPH_URI, pair)
                        .build());

        var written = writtenByWriter(writer -> writer.writeAssociationPair(pair));
        var byUpdates = currentTestGraph();

        // Both write the stereotypes: the field is new and CIMUpdates was taught to write it in the
        // same breath. What is still missing is the read side — the select query does not bind them
        // and the API has no field — so replaceAssociations rewrites a half without them and
        // ofAggregate is gone on the first save regardless of who wrote it.
        assertThat(
                        written.contains(
                                written.createResource(pair.getFrom().getUri().toString()),
                                CIMS.stereotype,
                                written.createLiteral("ofAggregate")))
                .isTrue();
        assertSameTriples(written, byUpdates);
    }

    // -------------------------------------------------------------------------

    /**
     * Compared as triple sets rather than by isomorphism, so a mismatch names itself.
     *
     * <p>The uuids are left out, and not because they do not matter. Two things assign them that
     * this writer does not: {@code CIMUpdates.insertClass} mints a fresh one and overwrites
     * whatever the DTO carried, and the store runs {@link
     * org.rdfarchitect.rdf.graph.GraphUtils#enhanceWithUUIDs} on commit, which gives one to every
     * typed and merely referenced resource — the superclasses, the package, even a stereotype IRI.
     * Neither is this writer's business; that it writes the uuid the DTO carries is asserted on its
     * own, because the import mints them itself and has to be able to find them again.
     */
    private void assertSameTriples(
            org.apache.jena.rdf.model.Model written, org.apache.jena.rdf.model.Model byUpdates) {
        assertEveryTypedResourceCarriesAUuid(written);
        assertThat(withoutUuids(written))
                .containsExactlyInAnyOrderElementsOf(withoutUuids(byUpdates));
    }

    /**
     * What dropping the uuids from the comparison would otherwise stop anyone from noticing: that
     * the writer wrote one at all. {@code GraphUtils.enhanceWithUUIDs} only assigns them to typed
     * resources and the readers filter on them, so a resource without one is stored and never
     * shown. Asserted here rather than per test, so that a writer method added later is covered
     * whether or not whoever adds it thinks of this.
     */
    private void assertEveryTypedResourceCarriesAUuid(org.apache.jena.rdf.model.Model written) {
        var typed =
                written.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type)
                        .filterKeep(org.apache.jena.rdf.model.Resource::isURIResource)
                        .toList();
        assertThat(typed).isNotEmpty();
        assertThat(typed)
                .allSatisfy(resource -> assertThat(resource.hasProperty(RDFA.uuid)).isTrue());
    }

    private java.util.Set<org.apache.jena.rdf.model.Statement> withoutUuids(
            org.apache.jena.rdf.model.Model model) {
        return model.listStatements()
                .filterDrop(statement -> statement.getPredicate().equals(RDFA.uuid))
                .toSet();
    }

    private org.apache.jena.rdf.model.Model writtenByWriter(
            java.util.function.Consumer<CIMModelWriter> write) {
        var model = ModelFactory.createDefaultModel();
        write.accept(new CIMModelWriter(GRAPH_URI, model));
        return model;
    }

    private org.apache.jena.rdf.model.Model writtenByUpdates(
            java.util.function.Consumer<org.apache.jena.graph.Graph> write) {
        executeWriteTransaction(write::accept);
        return currentTestGraph();
    }

    /** What the test graph holds now, detached so that it survives the transaction. */
    private org.apache.jena.rdf.model.Model currentTestGraph() {
        try (var ctx = testGraph.begin(ReadWrite.READ)) {
            return ModelFactory.createDefaultModel()
                    .add(ModelFactory.createModelForGraph(ctx.getRdfGraph()));
        }
    }

    private org.rdfarchitect.models.cim.data.dto.CIMAttribute attributeFixture() {
        return org.rdfarchitect.models.cim.data.dto.CIMAttribute.builder()
                .uuid(MY_UUID)
                .uri(new URI(CLASS_URI + "." + ATTRIBUTE_LABEL))
                .label(new RDFSLabel(ATTRIBUTE_LABEL, "en"))
                .domain(
                        new org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain(
                                new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en")))
                .multiplicity(
                        new org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity(
                                new URI(MULTIPLICITY_URI)))
                .dataType(
                        new org.rdfarchitect.models.cim.data.dto.relations.datatype
                                .CIMSPrimitiveDataType(
                                new URI(URI_PREFIX + "Float"), new RDFSLabel("Float", "en")))
                .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                .stereotypes(List.of(new CIMSStereotype("Operation")))
                .build();
    }

    private org.rdfarchitect.models.cim.data.dto.CIMAssociationPair associationPairFixture() {
        var from =
                org.rdfarchitect.models.cim.data.dto.CIMAssociation.builder()
                        .uuid(MY_UUID)
                        .uri(new URI(CLASS_URI + ".other"))
                        .label(new RDFSLabel("other", "en"))
                        .domain(
                                new org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain(
                                        new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en")))
                        .range(
                                new org.rdfarchitect.models.cim.data.dto.relations.datatype
                                        .RDFSRange(
                                        new URI(OTHER_CLASS_URI),
                                        new RDFSLabel(OTHER_CLASS_LABEL, "en")))
                        .multiplicity(
                                new org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity(
                                        new URI(MULTIPLICITY_URI)))
                        .associationUsed(
                                new org.rdfarchitect.models.cim.data.dto.relations
                                        .CIMSAssociationUsed("Yes"))
                        .inverseRoleName(
                                new org.rdfarchitect.models.cim.data.dto.relations
                                        .CIMSInverseRoleName(new URI(OTHER_CLASS_URI + ".class")))
                        .stereotypes(List.of(new CIMSStereotype("ofAggregate")))
                        .build();
        var to =
                from.toBuilder()
                        .uuid(UUID.randomUUID())
                        .uri(new URI(OTHER_CLASS_URI + ".class"))
                        .label(new RDFSLabel(CLASS_LABEL, "en"))
                        .domain(
                                new org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain(
                                        new URI(OTHER_CLASS_URI),
                                        new RDFSLabel(OTHER_CLASS_LABEL, "en")))
                        .range(
                                new org.rdfarchitect.models.cim.data.dto.relations.datatype
                                        .RDFSRange(
                                        new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en")))
                        .inverseRoleName(
                                new org.rdfarchitect.models.cim.data.dto.relations
                                        .CIMSInverseRoleName(new URI(CLASS_URI + ".other")))
                        .stereotypes(List.of())
                        .build();
        return new org.rdfarchitect.models.cim.data.dto.CIMAssociationPair(from, to);
    }
}
