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

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.util.ArrayList;
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
final class ShapesTurtleParser {

    /** Code for a finding that came from the parser rather than from a shape check. */
    static final String PARSE_ERROR_CODE = "TURTLE_PARSE_ERROR";

    /**
     * The graph parsed from {@code turtle} together with whatever the parser complained about.
     *
     * @param graph the triples read; empty when parsing failed
     * @param findings parser complaints, in the order reported
     * @param failed whether parsing stopped early, leaving {@link #graph} incomplete
     */
    record Result(Graph graph, List<ShapesValidationFinding> findings, boolean failed) {}

    private ShapesTurtleParser() {}

    static Result parse(String turtle) {
        var findings = new ArrayList<ShapesValidationFinding>();
        var graph = GraphFactory.createDefaultGraph();
        boolean failed = false;
        try {
            RDFParser.fromString(turtle, Lang.TURTLE)
                    .errorHandler(collectingHandler(findings))
                    .parse(graph);
        } catch (RiotException e) {
            failed = true;
            if (findings.isEmpty()) {
                // The handler saw nothing, so the parser failed before reporting a position.
                findings.add(
                        finding(ShapesValidationFinding.Severity.ERROR, e.getMessage(), -1, -1));
            }
        }
        return new Result(graph, List.copyOf(findings), failed);
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
