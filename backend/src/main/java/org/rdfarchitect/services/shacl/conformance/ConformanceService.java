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

import java.util.List;
import java.util.UUID;

/**
 * Answers "does my schema still agree with the constraints that came with it?".
 *
 * <p>Both sides are turned into the same thing before they are compared: one statement per class
 * and property, merged from however many shapes said it. That is what makes the comparison possible
 * at all — generated shapes and official ENTSO-E ones share no naming convention, and both sides
 * spread one property's rules over separate cardinality, datatype and value-type shapes.
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
        Graph documentShapes;
        String documentName;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var document = ctx.getShapesDocuments().get(documentId);
            if (document == null) {
                throw new ResourceNotFoundException(
                        "No constraints document with id " + documentId + " in this graph.");
            }
            documentName = document.getName();
            documentShapes = copyOf(document.getGraph());

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

        var compared = implied.size() + count(findings, ConformanceFinding.Kind.NOT_IN_SCHEMA);
        return ConformanceReport.builder()
                .documentId(documentId)
                .documentName(documentName)
                .conforms(findings.isEmpty())
                .compared(compared)
                .agreeing(compared - findings.size())
                .contradictedCount(count(findings, ConformanceFinding.Kind.CONTRADICTED))
                .differentCount(count(findings, ConformanceFinding.Kind.DIFFERENT))
                .missingInDocumentCount(
                        count(findings, ConformanceFinding.Kind.MISSING_IN_DOCUMENT))
                .notInSchemaCount(count(findings, ConformanceFinding.Kind.NOT_IN_SCHEMA))
                .findings(findings)
                .build();
    }

    private static int count(List<ConformanceFinding> findings, ConformanceFinding.Kind kind) {
        return (int) findings.stream().filter(finding -> finding.getKind() == kind).count();
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
