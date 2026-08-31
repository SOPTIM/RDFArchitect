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
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.snapshots.ShapesGraphNaming;

/**
 * Loading the shapes graphs a snapshot carries alongside its schema.
 *
 * <p>A snapshot is a plain RDF dataset whose content is discovered by listing its named graphs, so
 * the shapes graph has to be recognised and routed into the owning graph's context — otherwise it
 * would surface as a workspace graph of its own.
 */
class GraphWithContextCollectionSnapshotTest {

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

    private static Model shapesModel() {
        var model = ModelFactory.createDefaultModel();
        model.add(
                ResourceFactory.createResource(GRAPH_URI + "#ACLineSegmentShape"),
                ResourceFactory.createProperty(SHACL_NS + "targetClass"),
                ResourceFactory.createResource(GRAPH_URI + "#ACLineSegment"));
        return model;
    }

    private static Dataset snapshotWithShapes() {
        var dataset = DatasetFactory.createGeneral();
        dataset.addNamedModel(GRAPH_URI, schemaModel());
        dataset.addNamedModel(
                ShapesGraphNaming.encode(GRAPH_URI, ShapesGraphNaming.DEFAULT_DOCUMENT_ID),
                shapesModel());
        return dataset;
    }

    private static boolean customSHACLHasTargetClass(GraphWithContextTransactional context) {
        try (var ctx = context.begin(ReadWrite.READ)) {
            return ModelFactory.createModelForGraph(ctx.getCustomSHACL())
                    .contains(
                            ResourceFactory.createResource(GRAPH_URI + "#ACLineSegmentShape"),
                            ResourceFactory.createProperty(SHACL_NS + "targetClass"));
        }
    }

    @Test
    void shapesGraphIsNotExposedAsAGraphOfItsOwn() {
        var collection = new GraphWithContextCollection(snapshotWithShapes());

        assertThat(collection.listGraphUris()).containsExactly(GRAPH_URI);
    }

    @Test
    void shapesGraphIsLoadedIntoTheOwningGraphsCustomSHACL() {
        var collection = new GraphWithContextCollection(snapshotWithShapes());

        assertThat(customSHACLHasTargetClass(collection.getGraphWithContext(GRAPH_URI))).isTrue();
    }

    @Test
    void graphWithoutShapesLoadsWithAnEmptyCustomSHACL() {
        var dataset = DatasetFactory.createGeneral();
        dataset.addNamedModel(GRAPH_URI, schemaModel());

        var collection = new GraphWithContextCollection(dataset);

        try (var ctx = collection.getGraphWithContext(GRAPH_URI).begin(ReadWrite.READ)) {
            assertThat(ctx.getCustomSHACL().isEmpty()).isTrue();
        }
    }

    @Test
    void shapesWhoseOwnerIsMissingAreDropped() {
        // A snapshot that lost its schema graph must not resurrect it from the shapes alone.
        var dataset = DatasetFactory.createGeneral();
        dataset.addNamedModel(
                ShapesGraphNaming.encode(GRAPH_URI, ShapesGraphNaming.DEFAULT_DOCUMENT_ID),
                shapesModel());

        var collection = new GraphWithContextCollection(dataset);

        assertThat(collection.listGraphUris()).isEmpty();
    }

    @Test
    void loadingShapesLeavesNothingToUndo() {
        // The shapes arrive as part of the initial import commit: a user opening a shared snapshot
        // should not find an undo step for content they never authored.
        var collection = new GraphWithContextCollection(snapshotWithShapes());
        var context = collection.getGraphWithContext(GRAPH_URI);

        try (var ctx = context.begin(ReadWrite.READ)) {
            assertThat(ctx.canUndo()).isFalse();
        }
        assertThat(customSHACLHasTargetClass(context)).isTrue();
    }
}
