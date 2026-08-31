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

package org.rdfarchitect.services.shacl.form;

import de.soptim.opencgmes.cimvocabcheck.core.shacl.Shacl;

import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes one shape back as a Turtle statement.
 *
 * <p>Only the edited shape is written; the rest of the document is left as its author wrote it. The
 * output uses the document's own prefixes so an edited shape reads like its neighbours, and falls
 * back to an absolute IRI rather than adding a prefix the document does not declare.
 */
final class ShapeModelWriter {

    private static final String INDENT = "    ";

    private ShapeModelWriter() {}

    /** The shape as a statement, ending with its {@code .} and no trailing newline. */
    static String write(NodeShapeModel shape, PrefixMapping prefixes) {
        var lines = new ArrayList<String>();
        lines.add(term(shape.getIri(), prefixes));

        var clauses = new ArrayList<String>();
        clauses.add("a " + term(Shacl.NS + "NodeShape", prefixes));
        addIri(clauses, prefixes, Shacl.TARGET_CLASS.getURI(), shape.getTargetClass());
        addString(clauses, prefixes, ShapeModelReader.NAME.getURI(), shape.getName());
        addString(clauses, prefixes, ShapeModelReader.DESCRIPTION.getURI(), shape.getDescription());
        addBoolean(clauses, prefixes, ShapeModelReader.CLOSED.getURI(), shape.getClosed());
        addList(
                clauses,
                prefixes,
                Shacl.IGNORED_PROPERTIES.getURI(),
                shape.getIgnoredProperties(),
                true);
        addIri(clauses, prefixes, ShapeModelReader.SEVERITY.getURI(), shape.getSeverity());
        addString(clauses, prefixes, ShapeModelReader.MESSAGE.getURI(), shape.getMessage());
        addBoolean(clauses, prefixes, Shacl.DEACTIVATED.getURI(), shape.getDeactivated());

        for (String block : propertyBlocks(shape.getProperties(), prefixes)) {
            clauses.add(block);
        }

        for (int i = 0; i < clauses.size(); i++) {
            var separator = i == clauses.size() - 1 ? " ." : " ;";
            lines.add(INDENT + clauses.get(i) + separator);
        }
        return String.join("\n", lines);
    }

    private static List<String> propertyBlocks(
            List<PropertyShapeModel> properties, PrefixMapping prefixes) {
        var blocks = new ArrayList<String>();
        for (PropertyShapeModel property :
                properties == null ? List.<PropertyShapeModel>of() : properties) {
            // A property shape written as its own resource stays a reference. Inlining it would
            // orphan the statement that defines it and duplicate its constraints; the form shows
            // such a rule but does not offer to change it.
            if (property.getIri() != null) {
                blocks.add(
                        term(Shacl.PROPERTY.getURI(), prefixes)
                                + " "
                                + term(property.getIri(), prefixes));
                continue;
            }
            var clauses = new ArrayList<String>();
            addIri(clauses, prefixes, Shacl.PATH.getURI(), property.getPath());
            addString(clauses, prefixes, ShapeModelReader.NAME.getURI(), property.getName());
            addString(
                    clauses,
                    prefixes,
                    ShapeModelReader.DESCRIPTION.getURI(),
                    property.getDescription());
            addIri(clauses, prefixes, Shacl.DATATYPE.getURI(), property.getDataType());
            addIri(clauses, prefixes, Shacl.CLASS.getURI(), property.getClassIri());
            addIri(clauses, prefixes, Shacl.NODE_KIND.getURI(), property.getNodeKind());
            addInteger(clauses, prefixes, Shacl.MIN_COUNT.getURI(), property.getMinCount());
            addInteger(clauses, prefixes, Shacl.MAX_COUNT.getURI(), property.getMaxCount());
            addList(clauses, prefixes, Shacl.IN.getURI(), property.getAllowedValues(), false);
            addString(clauses, prefixes, ShapeModelReader.PATTERN.getURI(), property.getPattern());
            addIri(clauses, prefixes, ShapeModelReader.SEVERITY.getURI(), property.getSeverity());
            addString(clauses, prefixes, ShapeModelReader.MESSAGE.getURI(), property.getMessage());
            addInteger(clauses, prefixes, ShapeModelReader.ORDER.getURI(), property.getOrder());
            addIri(clauses, prefixes, ShapeModelReader.GROUP.getURI(), property.getGroup());
            addBoolean(clauses, prefixes, Shacl.DEACTIVATED.getURI(), property.getDeactivated());
            if (clauses.isEmpty()) {
                continue;
            }
            var block = new StringBuilder(term(Shacl.PROPERTY.getURI(), prefixes) + " [\n");
            for (String clause : clauses) {
                block.append(INDENT).append(INDENT).append(clause).append(" ;\n");
            }
            block.append(INDENT).append("]");
            blocks.add(block.toString());
        }
        return blocks;
    }

    // -------------------------------------------------------------------------
    // Clauses
    // -------------------------------------------------------------------------

    private static void addIri(
            List<String> clauses, PrefixMapping prefixes, String predicate, String value) {
        if (value != null && !value.isBlank()) {
            clauses.add(term(predicate, prefixes) + " " + term(value, prefixes));
        }
    }

    private static void addString(
            List<String> clauses, PrefixMapping prefixes, String predicate, String value) {
        if (value != null && !value.isEmpty()) {
            clauses.add(term(predicate, prefixes) + " " + quote(value));
        }
    }

    private static void addInteger(
            List<String> clauses, PrefixMapping prefixes, String predicate, Integer value) {
        if (value != null) {
            clauses.add(term(predicate, prefixes) + " " + value);
        }
    }

    private static void addBoolean(
            List<String> clauses, PrefixMapping prefixes, String predicate, Boolean value) {
        if (value != null) {
            clauses.add(term(predicate, prefixes) + " " + value);
        }
    }

    /**
     * An RDF collection. {@code sh:ignoredProperties} holds IRIs; {@code sh:in} may hold either, so
     * anything that does not look like an absolute IRI is written as a string.
     */
    private static void addList(
            List<String> clauses,
            PrefixMapping prefixes,
            String predicate,
            List<String> values,
            boolean iris) {
        if (values == null || values.isEmpty()) {
            return;
        }
        var written = new ArrayList<String>();
        for (String value : values) {
            written.add(iris || looksLikeIri(value) ? term(value, prefixes) : quote(value));
        }
        clauses.add(term(predicate, prefixes) + " ( " + String.join(" ", written) + " )");
    }

    private static boolean looksLikeIri(String value) {
        return value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("urn:");
    }

    /** The shortest form of an IRI this document can read: a prefixed name, else angle brackets. */
    static String term(String iri, PrefixMapping prefixes) {
        var shortened = prefixes.shortForm(iri);
        return shortened.equals(iri) ? "<" + iri + ">" : shortened;
    }

    private static String quote(String value) {
        var escaped =
                value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
