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

package org.rdfarchitect.services.shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Parses Turtle for validation, reporting what went wrong instead of throwing.
 *
 * <p>Validation is the one place that must cope with text that does not parse: it is what the
 * editor asks while the user is still typing. Letting Jena's {@link RiotException} escape would
 * answer "is this SHACL valid?" with a 500 and no position, when the honest answer is a finding at
 * the line the parser stopped on. Turtle parsing aborts at the first fatal error, so a broken
 * document reports that one problem plus any recoverable ones before it — not every problem in the
 * file.
 */
public final class ShapesTurtleParser {

    /** Code for a finding that came from the parser rather than from a shape check. */
    public static final String PARSE_ERROR_CODE = "TURTLE_PARSE_ERROR";

    /** Code for an IRI the parser read but no store can be sent, see {@link UnwritableIris}. */
    public static final String UNWRITABLE_IRI_CODE = "UNWRITABLE_IRI";

    /** What is reported for a document nested more deeply than it can be read. */
    public static final String TOO_DEEP =
            "The document nests brackets or lists too deeply to be read.";

    /**
     * The graph parsed from {@code turtle} together with whatever the parser complained about.
     *
     * @param graph the triples read, which after a failure is what came before it
     * @param findings parser complaints, in the order reported
     * @param failed whether parsing stopped early, leaving {@link #graph} incomplete
     */
    public record Result(Graph graph, List<ShapesValidationFinding> findings, boolean failed) {}

    private ShapesTurtleParser() {}

    public static Result parse(String turtle) {
        var findings = new ArrayList<ShapesValidationFinding>();
        var graph = GraphFactory.createDefaultGraph();
        boolean failed = false;
        try {
            RDFParser.fromString(turtle, Lang.TURTLE)
                    .base(ShapesDocument.BASE_IRI)
                    .errorHandler(collectingHandler(findings))
                    .parse(graph);
        } catch (RiotException e) {
            failed = true;
            if (findings.isEmpty()) {
                // The handler saw nothing, so the parser failed before reporting a position.
                findings.add(
                        finding(ShapesValidationFinding.Severity.ERROR, e.getMessage(), -1, -1));
            }
        } catch (StackOverflowError e) {
            // Jena parses brackets recursively, so a few thousand nested `[` or `(` exhaust the
            // stack. That is a property of the text, not a fault of the server, and it is answered
            // like any other text that cannot be read.
            failed = true;
            findings.add(finding(ShapesValidationFinding.Severity.ERROR, TOO_DEEP, -1, -1));
        }
        return new Result(graph, withUnwritableIris(findings, graph), failed);
    }

    /**
     * Reports an IRI that cannot be saved as an error, where the parser's first warning about it
     * stood, in place of those warnings: they say less, and only as a warning. Both of them go —
     * the one naming the whole IRI and the tokenizer's naming the character — so the problem is one
     * finding.
     */
    private static List<ShapesValidationFinding> withUnwritableIris(
            List<ShapesValidationFinding> findings, Graph graph) {
        var warnings =
                findings.stream()
                        .filter(f -> f.getSeverity() == ShapesValidationFinding.Severity.WARNING)
                        .map(
                                f ->
                                        new UnwritableIris.Warning(
                                                f.getMessage(),
                                                f.getLine() == null ? -1 : f.getLine(),
                                                f.getColumn() == null ? -1 : f.getColumn()))
                        .toList();
        var unwritable = UnwritableIris.in(graph, warnings);
        if (unwritable.isEmpty()) {
            return List.copyOf(findings);
        }
        var result = new ArrayList<ShapesValidationFinding>();
        var reported = new HashSet<String>();
        for (var finding : findings) {
            var named =
                    finding.getSeverity() == ShapesValidationFinding.Severity.WARNING
                            ? unwritable.stream().filter(found -> names(finding, found)).toList()
                            : List.<UnwritableIris.Found>of();
            if (named.isEmpty()) {
                result.add(finding);
            }
            named.stream()
                    .filter(found -> reported.add(found.iri()))
                    .forEach(found -> result.add(unwritable(found)));
        }
        unwritable.stream()
                .filter(found -> reported.add(found.iri()))
                .forEach(found -> result.add(unwritable(found)));
        return List.copyOf(result);
    }

    /**
     * Whether a warning is about {@code found}: it names the whole IRI, or it is the tokenizer's,
     * which shows the IRI up to a character it cannot hold, as in {@code <http://x/a[|]...>}.
     */
    private static boolean names(ShapesValidationFinding warning, UnwritableIris.Found found) {
        var message = warning.getMessage();
        if (message.contains(found.iri())) {
            return true;
        }
        var iri = found.iri();
        for (int at = 0; at < iri.length(); at = iri.offsetByCodePoints(at, 1)) {
            if (UnwritableIris.notInAnIri(iri.codePointAt(at))
                    && message.contains("<" + iri.substring(0, at) + "[")) {
                return true;
            }
        }
        return false;
    }

    private static ShapesValidationFinding unwritable(UnwritableIris.Found found) {
        return ShapesValidationFinding.builder()
                .severity(ShapesValidationFinding.Severity.ERROR)
                .source(ShapesValidationFinding.Source.SYNTAX)
                .code(UNWRITABLE_IRI_CODE)
                .message(found.describe())
                .line(found.line() > 0 ? (int) found.line() : null)
                .column(found.column() > 0 ? (int) found.column() : null)
                .foundInProfiles(List.of())
                .build();
    }

    private static ErrorHandler collectingHandler(List<ShapesValidationFinding> findings) {
        return new ErrorHandler() {
            @Override
            public void warning(String message, long line, long col) {
                findings.add(finding(ShapesValidationFinding.Severity.WARNING, message, line, col));
            }

            @Override
            public void error(String message, long line, long col) {
                findings.add(finding(ShapesValidationFinding.Severity.ERROR, message, line, col));
            }

            @Override
            public void fatal(String message, long line, long col) {
                findings.add(finding(ShapesValidationFinding.Severity.ERROR, message, line, col));
                // Jena's own strict handler throws here; carrying on past a fatal error would leave
                // the parser in an undefined state.
                throw new RiotException(message);
            }
        };
    }

    private static ShapesValidationFinding finding(
            ShapesValidationFinding.Severity severity, String message, long line, long col) {
        return ShapesValidationFinding.builder()
                .severity(severity)
                .source(ShapesValidationFinding.Source.SYNTAX)
                .code(PARSE_ERROR_CODE)
                .message(message)
                .line(line > 0 ? (int) line : null)
                .column(col > 0 ? (int) col : null)
                .foundInProfiles(List.of())
                .build();
    }
}
