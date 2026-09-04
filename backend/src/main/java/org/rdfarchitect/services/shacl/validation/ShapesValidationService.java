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
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.ShapesDocumentValidationResult;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;
import org.rdfarchitect.shacl.dto.ShapesValidationReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    /** Identifies a graph's committed content, within the session whose database holds it. */
    private record ComparisonKey(String sessionId, GraphIdentifier graph, UUID version) {}

    private record Comparison(ComparisonKey key, List<ShapesConflictAnalyzer.Document> documents) {}

    /**
     * The stored documents an unsaved buffer is compared against, held until the graph changes.
     *
     * <p>The editor asks for this on every debounced keystroke, and answering it copies every
     * shapes document of the graph — tens of thousands of triples for a full ENTSO-E set — to
     * compare them against a buffer that is the only thing that moved. A commit, an undo or a redo
     * mints a new version id for the whole context, so keying on that id is enough to notice a
     * change without the write path having to know this exists.
     *
     * <p>One entry, because typing happens in one document of one graph at a time and a second
     * would only pin copies nothing is going to ask for again.
     */
    private final AtomicReference<Comparison> comparison = new AtomicReference<>();

    /**
     * A document's content as validation needs it, detached from the transaction it came from.
     *
     * <p>The text is a memoised supplier rather than a string. Resolving a finding to a line needs
     * it, but most documents produce no finding, and one restored from a snapshot has no stored
     * text so it would have to be serialised from its triples first — per document, on every
     * keystroke, to answer a question about one of them.
     */
    private record Snapshot(UUID id, String name, Graph graph, Supplier<String> rawText) {}

    /** Runs {@code source} at most once, on the first call. */
    private static Supplier<String> memoised(Supplier<String> source) {
        var value = new AtomicReference<String>();
        return () -> value.updateAndGet(existing -> existing != null ? existing : source.get());
    }

    @Override
    public ShapesValidationReport validateShapes(GraphIdentifier graphIdentifier, UUID documentId) {
        var snapshots = readDocuments(graphIdentifier, documentId);
        var api = schemaIndexCache.apiFor(graphIdentifier.datasetName());
        var conflicts = conflictsAmong(snapshots);

        // Conflicts are found across all of the documents even when only one is being reported on:
        // "this shape is also defined in EQ.ttl" is a fact about the pair, and a document list
        // refreshing one badge should not stop saying it.
        var results =
                snapshots.stream()
                        .filter(snapshot -> documentId == null || documentId.equals(snapshot.id()))
                        .map(
                                snapshot ->
                                        result(
                                                snapshot.id(),
                                                snapshot.name(),
                                                findingsOf(api, snapshot, conflicts)))
                        .toList();
        return report(api, results);
    }

    @Override
    public ShapesValidationReport validateTurtle(
            GraphIdentifier graphIdentifier, String name, String turtle, UUID documentId) {
        var parsed = ShapesTurtleParser.parse(turtle);
        var api = schemaIndexCache.apiFor(graphIdentifier.datasetName());
        var findings = new ArrayList<>(parsed.findings());
        // A document that stopped parsing has no complete graph to check the shapes against, and
        // reporting the syntax error alone beats reporting whatever the fragment happens to imply.
        if (!parsed.failed()) {
            findings.addAll(validate(api, parsed.graph(), turtle));
            findings.addAll(
                    bufferConflicts(graphIdentifier, documentId, name, parsed.graph(), turtle));
        }
        return report(api, List.of(result(documentId, name, findings)));
    }

    private List<ShapesValidationFinding> findingsOf(
            SparqlValidationApi api,
            Snapshot snapshot,
            Map<UUID, List<ShapesValidationFinding>> conflicts) {
        var findings = new ArrayList<>(validate(api, snapshot.graph(), snapshot.rawText().get()));
        findings.addAll(conflicts.getOrDefault(snapshot.id(), List.of()));
        return findings;
    }

    private static Map<UUID, List<ShapesValidationFinding>> conflictsAmong(
            List<Snapshot> snapshots) {
        return ShapesConflictAnalyzer.analyze(
                snapshots.stream().map(ShapesValidationService::asDocument).toList());
    }

    /**
     * Contradictions between unsaved text and the graph's other documents.
     *
     * <p>The stored copy of {@code documentId} is left out and the posted text takes its place:
     * comparing a buffer with its own saved version would report every shape it has not renamed as
     * defined twice. Without a {@code documentId} there is nothing to leave out and therefore
     * nothing that can be said safely, so the comparison is skipped rather than guessed at.
     *
     * <p>An id that no stored document has is fine — a document being drafted before its first save
     * is simply compared against everything that is stored.
     */
    private List<ShapesValidationFinding> bufferConflicts(
            GraphIdentifier graphIdentifier,
            UUID documentId,
            String name,
            Graph graph,
            String turtle) {
        if (documentId == null) {
            return List.of();
        }
        var documents =
                storedForComparison(graphIdentifier).stream()
                        .filter(document -> !documentId.equals(document.id()))
                        .collect(Collectors.toCollection(ArrayList::new));
        documents.add(new ShapesConflictAnalyzer.Document(documentId, name, graph, () -> turtle));
        return ShapesConflictAnalyzer.analyze(documents).getOrDefault(documentId, List.of());
    }

    /** The graph's enabled documents, from {@link #comparison} unless the graph has changed. */
    private List<ShapesConflictAnalyzer.Document> storedForComparison(
            GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var key =
                    new ComparisonKey(
                            SessionContext.getSessionId(),
                            graphIdentifier,
                            ctx.getRdfGraphVersion());
            var cached = comparison.get();
            if (cached != null && cached.key().equals(key)) {
                return cached.documents();
            }
            var documents =
                    ctx.getShapesDocuments().values().stream()
                            .filter(ShapesDocument::isEnabled)
                            .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                            .map(ShapesValidationService::snapshot)
                            .map(ShapesValidationService::asDocument)
                            .toList();
            comparison.set(new Comparison(key, documents));
            return documents;
        }
    }

    private static ShapesConflictAnalyzer.Document asDocument(Snapshot snapshot) {
        return new ShapesConflictAnalyzer.Document(
                snapshot.id(), snapshot.name(), snapshot.graph(), snapshot.rawText());
    }

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    /**
     * The documents to validate over: every enabled one, plus {@code required} even when it is
     * disabled, because naming a document is asking for it.
     */
    private List<Snapshot> readDocuments(GraphIdentifier graphIdentifier, UUID required) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var documents = ctx.getShapesDocuments();
            if (required != null && !documents.containsKey(required)) {
                throw new ResourceNotFoundException(
                        "No constraints document with id " + required + " in this graph.");
            }
            return documents.values().stream()
                    .filter(
                            document ->
                                    document.isEnabled()
                                            || Objects.equals(document.getId(), required))
                    .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                    .map(ShapesValidationService::snapshot)
                    .toList();
        }
    }

    private static Snapshot snapshot(ShapesDocument document) {
        var copy = GraphFactory.createDefaultGraph();
        GraphUtil.addInto(copy, document.getGraph());
        copy.getPrefixMapping().setNsPrefixes(document.getGraph().getPrefixMapping());
        // The text is read out here, inside the transaction, but serialising a document that has
        // none is deferred until something actually needs a position in it.
        var stored = document.getRawText();
        return new Snapshot(
                document.getId(),
                document.getName(),
                copy,
                stored != null ? () -> stored : memoised(() -> serialise(copy)));
    }

    /**
     * Text for a document that has none of its own.
     *
     * <p>A document normally keeps the source it was written from; one restored from a pre-{@code
     * rawText} snapshot, or rewound by an undo, has none, and serialising its triples at least
     * gives the findings somewhere to point.
     */
    private static String serialise(Graph parsed) {
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
