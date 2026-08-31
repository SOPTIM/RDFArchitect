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

package org.rdfarchitect.services.shacl.conformance;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.shacl.SHACLFromCIMGenerator;
import org.rdfarchitect.shacl.dto.ConformanceFinding;
import org.rdfarchitect.shacl.dto.ConformanceReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Answers "does my schema still agree with the constraints that came with it?".
 *
 * <p>Both sides are turned into the same thing before they are compared: one statement per class
 * and property, merged from however many shapes said it. That is what makes the comparison possible
 * at all — generated shapes and official ENTSO-E ones share no naming convention, and both sides
 * spread one property's rules over separate cardinality, datatype and value-type shapes.
 *
 * <p>The right-hand side is the graph's <em>enabled</em> documents together, not the one being
 * looked at. A graph's constraints are their conjunction, and official releases split their rules
 * across files on purpose — reading one alone reported its neighbours' coverage as missing. The
 * open document joins in even when it is disabled, because it is the one the question is about.
 *
 * <p>Inverse cardinality is left out. RDFArchitect states it as {@code sh:path [ sh:inversePath …
 * ]}, a path expression rather than a property, and there is nothing on the other side to line it
 * up with.
 */
@RequiredArgsConstructor
public class ConformanceService implements ConformanceUseCase {

    private final DatabasePort databasePort;

    @Override
    public ConformanceReport compare(GraphIdentifier graphIdentifier, UUID documentId) {
        var prefixes = databasePort.getPrefixMapping(graphIdentifier.datasetName());

        Graph schemaShapes;
        var documentShapes = new LinkedHashMap<String, Graph>();
        String documentName;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var documents = ctx.getShapesDocuments();
            var opened = documents.get(documentId);
            if (opened == null) {
                throw new ResourceNotFoundException(
                        "No constraints document with id " + documentId + " in this graph.");
            }
            documentName = opened.getName();
            documents.values().stream()
                    .filter(document -> document.isEnabled() || document.getId().equals(documentId))
                    .forEach(
                            document ->
                                    documentShapes.put(
                                            document.getName(), copyOf(document.getGraph())));

            var ontology = ModelFactory.createModelForGraph(copyOf(ctx.getRdfGraph()));
            ontology.setNsPrefixes(prefixes);
            schemaShapes =
                    new SHACLFromCIMGenerator(
                                    ontology,
                                    PrefixEntry.create(RDFA.NS_PREFIX_SHACL, RDFA.NS_URI_SHACL),
                                    true)
                            .generate()
                            .getGraph();
        }

        var implied = EffectiveConstraints.of(schemaShapes);
        var asserted = EffectiveConstraints.of(documentShapes);
        var findings = ConformanceComparator.compare(implied, asserted, prefixes);

        var contradicted = count(findings, ConformanceFinding.Kind.CONTRADICTED);
        var different = count(findings, ConformanceFinding.Kind.DIFFERENT);
        var notInSchema = count(findings, ConformanceFinding.Kind.NOT_IN_SCHEMA);
        // Only what both sides state is a question of agreement. Counting the schema's whole
        // surface here scored silence as disagreement, which is how a 55-line cross-profile file
        // came to read as "0 of 49 agree".
        var compared = overlap(implied.keySet(), asserted.constraints().keySet());

        return ConformanceReport.builder()
                .documentId(documentId)
                .documentName(documentName)
                .documents(List.copyOf(documentShapes.keySet()))
                .conforms(contradicted == 0 && different == 0 && notInSchema == 0)
                .compared(compared)
                .agreeing(compared - contradicted - different)
                .impliedBySchema(implied.size())
                .stated(asserted.constraints().size())
                .contradictedCount(contradicted)
                .differentCount(different)
                .missingInDocumentCount(
                        count(findings, ConformanceFinding.Kind.MISSING_IN_DOCUMENT))
                .notInSchemaCount(notInSchema)
                .findings(findings)
                .build();
    }

    private static int count(List<ConformanceFinding> findings, ConformanceFinding.Kind kind) {
        return (int) findings.stream().filter(finding -> finding.getKind() == kind).count();
    }

    private static int overlap(
            Set<EffectiveConstraints.Key> schema, Set<EffectiveConstraints.Key> documents) {
        return (int) schema.stream().filter(documents::contains).count();
    }

    /**
     * A detached copy taken while the read transaction is held.
     *
     * <p>Generating shapes walks the whole ontology and outlives the transaction, and the generator
     * writes its own prefixes onto the model it is given — neither is safe on a live versioned
     * graph.
     */
    private static Graph copyOf(Graph live) {
        var copy = GraphFactory.createDefaultGraph();
        GraphUtil.addInto(copy, live);
        copy.getPrefixMapping().setNsPrefixes(live.getPrefixMapping());
        return copy;
    }
}
