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

package org.rdfarchitect.database.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.ShapesDocumentSeed;
import org.rdfarchitect.database.snapshots.ShapesDocumentGraphs;
import org.rdfarchitect.database.snapshots.ShapesDocumentMetadata;
import org.rdfarchitect.database.snapshots.ShapesGraphNaming;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loading a dataset that carries shapes documents alongside its schemas.
 *
 * <p>A stored dataset is plain RDF whose content is discovered by listing its named graphs, so a
 * document's graph has to be recognised and routed to the graph owning it — otherwise it would
 * surface as a workspace graph of its own.
 */
class WorkspaceLoadsShapesDocumentsTest {

    private static final String GRAPH_URI = "http://example.org/EQ";
    private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";

    private static Model schemaModel() {
        var model = ModelFactory.createDefaultModel();
        model.add(
                ResourceFactory.createResource(GRAPH_URI + "#ACLineSegment"),
                ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"),
                "ACLineSegment");
        return model;
    }

    private static Model shapeModel(String localName) {
        var model = ModelFactory.createDefaultModel();
        model.add(
                ResourceFactory.createResource(GRAPH_URI + "#" + localName),
                ResourceFactory.createProperty(SHACL_NS + "targetClass"),
                ResourceFactory.createResource(GRAPH_URI + "#ACLineSegment"));
        return model;
    }

    /** The text a document holding {@link #shapeModel} would have been saved with. */
    private static String shapeText(String localName) {
        return """
                @prefix sh: <%s> .
                @prefix eq: <%s#> .

                # the %s
                eq:%s sh:targetClass eq:ACLineSegment .
                """
                .formatted(SHACL_NS, GRAPH_URI, localName, localName);
    }

    private static Dataset datasetWithSchema() {
        var dataset = DatasetFactory.createGeneral();
        dataset.addNamedModel(GRAPH_URI, schemaModel());
        return dataset;
    }

    private static void addShapes(Dataset dataset, UUID id, String localName) {
        dataset.addNamedModel(ShapesGraphNaming.encode(id), shapeModel(localName));
    }

    private static Map<UUID, ShapesDocument> documentsOf(Workspace workspace) {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            return transaction.shapesDocuments();
        }
    }

    /** Builds the metadata a dataset records for one document, the way a real write produces it. */
    private static Model metadata(
            UUID id, String name, String sourceFile, int order, boolean enabled, String rawText) {
        var scratch = new Workspace("scratch");
        try (var transaction = scratch.begin(ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH_URI, GraphFactory.createDefaultGraph());
            transaction.restoreShapesDocument(
                    new ShapesDocumentSeed(
                            id,
                            name,
                            sourceFile,
                            ShapesDocument.Origin.IMPORTED,
                            enabled,
                            order,
                            Set.of(GRAPH_URI),
                            rawText,
                            GraphFactory.createDefaultGraph()));
            var model = ShapesDocumentMetadata.emptyModel();
            ShapesDocumentMetadata.write(model, transaction.shapesDocuments().get(id));
            return model;
        }
    }

    private static void addMetadata(Dataset dataset, Model... documents) {
        var all = ShapesDocumentMetadata.emptyModel();
        for (var document : documents) {
            all.add(document);
        }
        dataset.addNamedModel(ShapesGraphNaming.metadataGraph(), all);
    }

    @Test
    void aDatasetWithoutDocumentsLoadsAsBefore() {
        var workspace = new Workspace("loaded", datasetWithSchema());

        assertThat(workspace.listGraphUris()).containsExactly(GRAPH_URI);
        assertThat(documentsOf(workspace)).isEmpty();
    }

    @Test
    void writingNoDocumentsAddsNothingToADataset() {
        assertThat(ShapesDocumentGraphs.of(List.of())).isEmpty();
    }

    /** A store keeps no empty graphs, so a schema with no triples isn't in the dataset at all. */
    @Test
    void aSchemaWithNoTriplesStaysGoneAndItsDocumentsStillLoad() {
        var id = UUID.randomUUID();
        var dataset = DatasetFactory.createGeneral();
        addShapes(dataset, id, "Shape");
        addMetadata(dataset, metadata(id, "eq.ttl", null, 0, true, null));

        var workspace = new Workspace("loaded", dataset);

        assertThat(workspace.listGraphUris()).isEmpty();
        assertThat(documentsOf(workspace)).containsOnlyKeys(id);
        assertThat(documentsOf(workspace).get(id).getSchemas()).containsExactly(GRAPH_URI);
    }

    @Test
    void aDocumentsGraphsAreNotGraphsOfTheWorkspace() {
        var id = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, id, "Shape");
        addMetadata(dataset, metadata(id, "eq.ttl", null, 0, true, null));

        var workspace = new Workspace("loaded", dataset);

        assertThat(workspace.listGraphUris()).containsExactly(GRAPH_URI);
    }

    @Test
    void everyDocumentIsRestoredWithItsMetadataInOrder() {
        var eqId = UUID.randomUUID();
        var tpId = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, eqId, "EqShape");
        addShapes(dataset, tpId, "TpShape");
        addMetadata(
                dataset,
                metadata(tpId, "tp.ttl", null, 2, false, null),
                metadata(eqId, "eq.ttl", "EQ.ttl", 1, true, shapeText("EqShape")));

        var documents = documentsOf(new Workspace("loaded", dataset));

        assertThat(documents.keySet()).containsExactly(eqId, tpId);
        var eq = documents.get(eqId);
        assertThat(eq.getName()).isEqualTo("eq.ttl");
        assertThat(eq.getSourceFileName()).isEqualTo("EQ.ttl");
        assertThat(eq.getOrder()).isEqualTo(1);
        assertThat(eq.isEnabled()).isTrue();
        assertThat(eq.getRawText()).isEqualTo(shapeText("EqShape"));
        var tp = documents.get(tpId);
        assertThat(tp.getName()).isEqualTo("tp.ttl");
        assertThat(tp.getSourceFileName()).isNull();
        assertThat(tp.isEnabled()).isFalse();
    }

    @Test
    void loadingDocumentsLeavesNothingToUndo() {
        var id = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, id, "Shape");

        var workspace = new Workspace("loaded", dataset);

        assertThat(workspace.canUndo()).isFalse();
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThat(transaction.shapesDocuments().get(id).getGraph().size()).isEqualTo(1);
        }
    }

    @Test
    void aLoadedDocumentCanBeEditedAndUndone() {
        var id = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, id, "Shape");
        addMetadata(dataset, metadata(id, "eq.ttl", null, 0, true, shapeText("Shape")));
        var workspace = new Workspace("loaded", dataset);
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.shapesDocuments().get(id).setRawText("edited");
            transaction.commit("Edited");
        }

        workspace.undo();

        assertThat(documentsOf(workspace).get(id).getRawText()).isEqualTo(shapeText("Shape"));
    }

    @Test
    void aDocumentWithoutRecordedMetadataStillLoads() {
        var id = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, id, "Shape");

        var document = documentsOf(new Workspace("loaded", dataset)).get(id);

        assertThat(document.getName()).contains(id.toString());
        assertThat(document.isEnabled()).isTrue();
    }

    @Test
    void aGraphInTheReservedNamespaceWithoutADocumentIdIsIgnored() {
        var dataset = datasetWithSchema();
        dataset.addNamedModel("urn:rdfa:shacl:not-a-uuid", shapeModel("Shape"));

        var workspace = new Workspace("loaded", dataset);

        assertThat(workspace.listGraphUris()).containsExactly(GRAPH_URI);
        assertThat(documentsOf(workspace)).isEmpty();
    }

    @Test
    void graphsFromTheFormerPerSchemaLayoutAreIgnoredAndNeverBecomeSchemas() {
        var dataset = datasetWithSchema();
        var owner = "http%3A%2F%2Fexample.org%2FEQ";
        dataset.addNamedModel(
                "urn:rdfa:shacl:" + owner + ":" + UUID.randomUUID(), shapeModel("Shape"));
        dataset.addNamedModel("urn:rdfa:shacl-meta:" + owner, shapeModel("Metadata"));

        var workspace = new Workspace("loaded", dataset);

        assertThat(workspace.listGraphUris()).containsExactly(GRAPH_URI);
        assertThat(documentsOf(workspace)).isEmpty();
    }

    @Test
    void aDocumentWithNoTriplesIsStillRestored() {
        var id = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addMetadata(dataset, metadata(id, "notes.ttl", null, 1, true, "# nothing yet"));

        var document = documentsOf(new Workspace("loaded", dataset)).get(id);

        assertThat(document.getName()).isEqualTo("notes.ttl");
        assertThat(document.getRawText()).isEqualTo("# nothing yet");
    }

    @Test
    void anEmptyDocumentDoesNotDisplaceOneThatHasShapes() {
        var emptyId = UUID.randomUUID();
        var shapesId = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, shapesId, "Shape");
        addMetadata(
                dataset,
                metadata(emptyId, "empty.ttl", null, 1, true, null),
                metadata(shapesId, "eq.ttl", "EQ.ttl", 2, true, shapeText("Shape")));

        var documents = documentsOf(new Workspace("loaded", dataset));

        assertThat(documents).containsOnlyKeys(emptyId, shapesId);
    }

    @Test
    void aDocumentWithRecordedTextGetsThePrefixesTheTextDeclares() {
        // A store keeps prefixes per dataset, so the stored shapes come back with none of their
        // own; without the text's, nothing could map a prefixed subject in the text to its shape.
        var id = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, id, "Shape");
        addMetadata(dataset, metadata(id, "eq.ttl", null, 1, true, shapeText("Shape")));
        var workspace = new Workspace("loaded", dataset);

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var graph = transaction.shapesDocuments().get(id).getGraph();
            assertThat(graph.getPrefixMapping().getNsPrefixURI("eq")).isEqualTo(GRAPH_URI + "#");
            assertThat(graph.isIsomorphicWith(shapeModel("Shape").getGraph())).isTrue();
        }
    }

    @Test
    void recordedTextThatNoLongerParsesKeepsTheStoredShapes() {
        var id = UUID.randomUUID();
        var dataset = datasetWithSchema();
        addShapes(dataset, id, "Shape");
        addMetadata(dataset, metadata(id, "eq.ttl", null, 1, true, "eq:Broken sh:targetClass"));
        var workspace = new Workspace("loaded", dataset);

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThat(transaction.shapesDocuments().get(id).getGraph().size()).isEqualTo(1);
        }
    }
}
