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

package org.rdfarchitect.shacl.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One problem found in a set of SHACL shapes, at a position in that document's Turtle source.
 *
 * <p>Positions are 1-based, matching how an editor numbers lines, and are {@code null} when the
 * finding cannot be tied to a place in the text — a check that reasons about the shapes graph as a
 * whole has nowhere to point.
 */
@Data
@Builder
public class ShapesValidationFinding {

    /** How much the finding matters. Only {@link #ERROR} makes a document invalid. */
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    /** Which check produced the finding, so the UI can group or filter by kind. */
    public enum Source {
        /** Turtle that could not be parsed. */
        SYNTAX,
        /** The structure of the shapes: {@code sh:targetClass}, {@code sh:path}, cardinalities. */
        SHAPE,
        /** A SPARQL query embedded in {@code sh:select}, {@code sh:ask} or {@code sh:construct}. */
        SPARQL,
        /** Two shapes that cannot both be satisfied, or one shape IRI defined twice. */
        CONFLICT
    }

    private Severity severity;

    private Source source;

    /**
     * Machine-readable kind, either a CIMVocabCheck {@code SparqlValidationCode} or one of this
     * service's own codes. Stable enough for the UI to key off.
     */
    private String code;

    private String message;

    /** 1-based line in the document's Turtle source, or {@code null} when unknown. */
    private Integer line;

    /** 1-based column, or {@code null} when unknown. */
    private Integer column;

    /** The IRI the finding is about, or {@code null} for a finding with no single term. */
    private String term;

    /**
     * Profiles that do declare {@link #term}, when the workspace's schema has it somewhere other
     * than where the shapes look for it. Empty otherwise.
     */
    private List<String> foundInProfiles;
}
