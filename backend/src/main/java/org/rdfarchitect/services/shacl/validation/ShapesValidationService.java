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

package org.rdfarchitect.services.shacl.validation;

import de.soptim.opencgmes.cimvocabcheck.core.SparqlValidationAnnotation;
import de.soptim.opencgmes.cimvocabcheck.core.SparqlValidationApi;
import de.soptim.opencgmes.cimvocabcheck.core.SparqlValidationSeverity;
import de.soptim.opencgmes.cimvocabcheck.core.VersionIri;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.shacl.dto.ShapesDocumentValidationResult;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;
import org.rdfarchitect.shacl.dto.ShapesValidationReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Validates SHACL shapes against the live CIM schema using CIMVocabCheck.
 *
 * <p>Every document is checked on its own so a finding can name the file and line it belongs to,
 * and then the documents are compared with each other for contradictions — see {@link
 * ShapesConflictAnalyzer} for why those are reported and never resolved.
 *
 * <p>Reading is kept short: each document's triples and text are copied out under a read
 * transaction and the analysis then runs outside it. Validating a full constraints file takes on
 * the order of a hundred milliseconds, which is far too long to hold a lock that blocks every
 * writer on the graph.
 */
@RequiredArgsConstructor
public class ShapesValidationService implements ShapesValidationUseCase {

    private final DatabasePort databasePort;

    private final SchemaIndexCache schemaIndexCache;

    /** A document's content as validation needs it, detached from the transaction it came from. */
    private record Snapshot(UUID id, String name, Graph graph, String rawText) {}

    @Override
    public ShapesValidationReport validateShapes(GraphIdentifier graphIdentifier, UUID documentId) {
        var snapshots = readDocuments(graphIdentifier, documentId);
        var api = schemaIndexCache.apiFor(graphIdentifier.datasetName());

        var conflicts =
                ShapesConflictAnalyzer.analyze(
                        snapshots.stream()
                                .map(
                                        snapshot ->
                                                new ShapesConflictAnalyzer.Document(
                                                        snapshot.id(),
                                                        snapshot.name(),
                                                        snapshot.graph(),
                                                        snapshot.rawText()))
                                .toList());

        var results =
                snapshots.stream()
                        .map(
                                snapshot -> {
                                    var findings =
                                            new ArrayList<>(
                                                    validate(
                                                            api,
                                                            snapshot.graph(),
                                                            snapshot.rawText()));
                                    findings.addAll(conflicts.get(snapshot.id()));
                                    return result(snapshot.id(), snapshot.name(), findings);
                                })
                        .toList();
        return report(api, results);
    }

    @Override
    public ShapesValidationReport validateTurtle(
            GraphIdentifier graphIdentifier, String name, String turtle) {
        var parsed = ShapesTurtleParser.parse(turtle);
        var api = schemaIndexCache.apiFor(graphIdentifier.datasetName());
        var findings = new ArrayList<>(parsed.findings());
        // A document that stopped parsing has no complete graph to check the shapes against, and
        // reporting the syntax error alone beats reporting whatever the fragment happens to imply.
        if (!parsed.failed()) {
            findings.addAll(validate(api, parsed.graph(), turtle));
        }
        return report(api, List.of(result(null, name, findings)));
    }

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    private List<Snapshot> readDocuments(GraphIdentifier graphIdentifier, UUID documentId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            if (documentId != null) {
                var document = ctx.getShapesDocuments().get(documentId);
                if (document == null) {
                    throw new ResourceNotFoundException(
                            "No constraints document with id " + documentId + " in this graph.");
                }
                return List.of(snapshot(document));
            }
            return ctx.getShapesDocuments().values().stream()
                    .filter(ShapesDocument::isEnabled)
                    .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                    .map(ShapesValidationService::snapshot)
                    .toList();
        }
    }

    private static Snapshot snapshot(ShapesDocument document) {
        var copy = GraphFactory.createDefaultGraph();
        GraphUtil.addInto(copy, document.getGraph());
        copy.getPrefixMapping().setNsPrefixes(document.getGraph().getPrefixMapping());
        return new Snapshot(document.getId(), document.getName(), copy, rawTextOf(document, copy));
    }

    /**
     * The text positions are resolved against. A document normally keeps the source it was written
     * from; one restored from a pre-{@code rawText} snapshot, or rewound by an undo, has none, and
     * serialising its triples at least gives the findings somewhere to point.
     */
    private static String rawTextOf(ShapesDocument document, Graph parsed) {
        if (document.getRawText() != null) {
            return document.getRawText();
        }
        return RDFWriter.source(ModelFactory.createModelForGraph(parsed))
                .format(RDFFormat.TURTLE_PRETTY)
                .lang(Lang.TURTLE)
                .asString();
    }

    // -------------------------------------------------------------------------
    // Validating
    // -------------------------------------------------------------------------

    private static List<ShapesValidationFinding> validate(
            SparqlValidationApi api, Graph shapes, String rawText) {
        var scope = api.schemaIndex().getAllProfiles();
        var result = api.validateShacl(shapes, scope);

        var findings = new LinkedHashSet<ShapesValidationFinding>();
        result.shapeAnnotations()
                .forEach(
                        annotation ->
                                findings.add(
                                        toFinding(
                                                annotation,
                                                ShapesValidationFinding.Source.SHAPE,
                                                SourcePositions.locate(
                                                        rawText,
                                                        shapes,
                                                        annotation.term(),
                                                        annotation.locationHint()))));
        result.embeddedResults()
                .forEach(
                        embedded ->
                                embedded.result()
                                        .annotations()
                                        .forEach(
                                                annotation ->
                                                        findings.add(
                                                                toFinding(
                                                                        annotation,
                                                                        ShapesValidationFinding
                                                                                .Source.SPARQL,
                                                                        SourcePositions
                                                                                .locateEmbedded(
                                                                                        rawText,
                                                                                        shapes,
                                                                                        annotation,
                                                                                        embedded
                                                                                                .embedded())))));
        return List.copyOf(findings);
    }

    private static ShapesValidationFinding toFinding(
            SparqlValidationAnnotation annotation,
            ShapesValidationFinding.Source source,
            SourcePositions.Position position) {
        return ShapesValidationFinding.builder()
                .severity(severityOf(annotation.severity()))
                .source(source)
                .code(annotation.code().name())
                .message(annotation.message())
                .line(position.line())
                .column(position.column())
                .term(
                        annotation.term() != null && annotation.term().isURI()
                                ? annotation.term().getURI()
                                : null)
                .foundInProfiles(
                        annotation.foundInOtherProfiles().stream().map(VersionIri::iri).toList())
                .build();
    }

    private static ShapesValidationFinding.Severity severityOf(SparqlValidationSeverity severity) {
        return switch (severity) {
            case ERROR -> ShapesValidationFinding.Severity.ERROR;
            case WARN -> ShapesValidationFinding.Severity.WARNING;
            case INFO -> ShapesValidationFinding.Severity.INFO;
        };
    }

    // -------------------------------------------------------------------------
    // Reporting
    // -------------------------------------------------------------------------

    /** Errors first, then in reading order, with findings that have no position last. */
    private static final Comparator<ShapesValidationFinding> BY_SEVERITY_THEN_POSITION =
            Comparator.comparing(ShapesValidationFinding::getSeverity)
                    .thenComparing(
                            ShapesValidationFinding::getLine,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            ShapesValidationFinding::getColumn,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    private static ShapesDocumentValidationResult result(
            UUID documentId, String name, List<ShapesValidationFinding> findings) {
        var sorted = findings.stream().sorted(BY_SEVERITY_THEN_POSITION).toList();
        int errors = count(sorted, ShapesValidationFinding.Severity.ERROR);
        return ShapesDocumentValidationResult.builder()
                .documentId(documentId)
                .documentName(name)
                .valid(errors == 0)
                .errorCount(errors)
                .warningCount(count(sorted, ShapesValidationFinding.Severity.WARNING))
                .infoCount(count(sorted, ShapesValidationFinding.Severity.INFO))
                .findings(sorted)
                .build();
    }

    private static int count(
            List<ShapesValidationFinding> findings, ShapesValidationFinding.Severity severity) {
        return (int) findings.stream().filter(finding -> finding.getSeverity() == severity).count();
    }

    private static ShapesValidationReport report(
            SparqlValidationApi api, List<ShapesDocumentValidationResult> results) {
        int errors = results.stream().mapToInt(ShapesDocumentValidationResult::getErrorCount).sum();
        return ShapesValidationReport.builder()
                .valid(errors == 0)
                .errorCount(errors)
                .warningCount(
                        results.stream()
                                .mapToInt(ShapesDocumentValidationResult::getWarningCount)
                                .sum())
                .infoCount(
                        results.stream()
                                .mapToInt(ShapesDocumentValidationResult::getInfoCount)
                                .sum())
                .profiles(api.schemaIndex().getAllProfiles().stream().map(VersionIri::iri).toList())
                .documents(results)
                .build();
    }
}
