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

import de.soptim.opencgmes.cimvocabcheck.core.SourceLocator;
import de.soptim.opencgmes.cimvocabcheck.core.SparqlValidationAnnotation;
import de.soptim.opencgmes.cimvocabcheck.core.shacl.EmbeddedSourceMapper;
import de.soptim.opencgmes.cimvocabcheck.core.shacl.EmbeddedSparql;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;

import java.util.regex.Pattern;

/**
 * Where in a document's Turtle source a finding belongs.
 *
 * <p>A shapes graph carries no positions, so they are recovered by searching the verbatim source
 * for the term the finding is about — which is the reason each document keeps its text rather than
 * only its triples. Positions produced here are 1-based, as an editor numbers lines; note that
 * CIMVocabCheck reports shape positions 1-based and embedded-SPARQL positions 0-based, so only the
 * latter are shifted.
 */
final class SourcePositions {

    /** A 1-based position, with {@code null} fields where the text gave no answer. */
    record Position(Integer line, Integer column) {}

    private static final Position UNKNOWN = new Position(null, null);

    private SourcePositions() {}

    /**
     * Locates {@code term} in {@code rawText}.
     *
     * @param hint a node from the same statement — typically the enclosing shape — used to pick
     *     between several occurrences of the same term; may be {@code null}
     */
    static Position locate(String rawText, Graph graph, Node term, Node hint) {
        if (rawText == null || term == null || !term.isURI()) {
            return UNKNOWN;
        }
        var located = SourceLocator.locateWithHint(rawText, term, graph.getPrefixMapping(), hint);
        return new Position(located.line(), located.column());
    }

    /**
     * Maps a finding about an embedded SPARQL query back to the Turtle that carries the query.
     *
     * <p>The query the validator saw has the shape's prefix declarations prepended, so its line
     * numbers do not match the Turtle source; {@link EmbeddedSourceMapper} undoes that shift and
     * finds the query text within the document.
     */
    static Position locateEmbedded(
            String rawText,
            Graph graph,
            SparqlValidationAnnotation annotation,
            EmbeddedSparql embedded) {
        if (rawText == null) {
            return UNKNOWN;
        }
        if (!hasQueryPosition(annotation)) {
            // Nothing to shift. Some checks report no position at all; the term itself is still
            // findable in the Turtle, since the query text is part of the document.
            return locate(rawText, graph, annotation.term(), annotation.locationHint());
        }
        var position = EmbeddedSourceMapper.toTurtlePosition(annotation, embedded, rawText);
        return new Position(position[0] + 1, position[1] + 1);
    }

    /**
     * Whether the annotation says where in the query it belongs, either as a line number or in the
     * {@code "line N, column C"} form Jena puts in a syntax-error message.
     */
    private static boolean hasQueryPosition(SparqlValidationAnnotation annotation) {
        return annotation.line() != null
                || (annotation.message() != null
                        && QUERY_POSITION.matcher(annotation.message()).find());
    }

    private static final Pattern QUERY_POSITION = Pattern.compile("line \\d+, column \\d+");
}
