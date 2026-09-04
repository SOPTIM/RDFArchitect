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
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Spells the Turtle a form edit adds to a document.
 *
 * <p>Only what is genuinely new: a shape the document does not hold yet, a rule just added to one,
 * or the object of a single clause. Changing something the document already says is {@link
 * ShapeClauseWriter}'s job and goes through the clause it is written in, so nothing else about the
 * shape is respelled.
 *
 * <p>The output uses the document's own prefixes so added text reads like its neighbours, and falls
 * back to an absolute IRI rather than binding a prefix the document does not declare.
 */
final class ShapeModelWriter {

    static final String INDENT = "    ";

    private ShapeModelWriter() {}

    /** A whole shape as a statement, ending with its {@code .} and no trailing newline. */
    static String write(NodeShapeModel shape, PrefixMapping prefixes) {
        var lines = new ArrayList<String>();
        lines.add(term(shape.getIri(), prefixes));

        var clauses = new ArrayList<String>();
        clauses.add("a " + term(Shacl.NS + "NodeShape", prefixes));
        add(
                clauses,
                Shacl.TARGET_CLASS.getURI(),
                terms(shape.getTargetClasses(), prefixes),
                prefixes);
        add(
                clauses,
                ShapeModelReader.TARGET_SUBJECTS_OF.getURI(),
                terms(shape.getTargetSubjectsOf(), prefixes),
                prefixes);
        add(
                clauses,
                ShapeModelReader.TARGET_OBJECTS_OF.getURI(),
                terms(shape.getTargetObjectsOf(), prefixes),
                prefixes);
        add(
                clauses,
                ShapeModelReader.TARGET_NODE.getURI(),
                terms(shape.getTargetNodes(), prefixes),
                prefixes);
        add(clauses, ShapeModelReader.NAME.getURI(), string(shape.getName()), prefixes);
        add(
                clauses,
                ShapeModelReader.DESCRIPTION.getURI(),
                string(shape.getDescription()),
                prefixes);
        add(clauses, ShapeModelReader.CLOSED.getURI(), flag(shape.getClosed()), prefixes);
        add(
                clauses,
                Shacl.IGNORED_PROPERTIES.getURI(),
                collection(shape.getIgnoredProperties(), true, prefixes),
                prefixes);
        add(
                clauses,
                ShapeModelReader.SEVERITY.getURI(),
                iri(shape.getSeverity(), prefixes),
                prefixes);
        add(clauses, ShapeModelReader.MESSAGE.getURI(), string(shape.getMessage()), prefixes);
        add(clauses, Shacl.DEACTIVATED.getURI(), flag(shape.getDeactivated()), prefixes);

        for (PropertyShapeModel property : rules(shape)) {
            clauses.add(propertyClause(property, prefixes, INDENT));
        }

        for (int i = 0; i < clauses.size(); i++) {
            lines.add(INDENT + clauses.get(i) + (i == clauses.size() - 1 ? " ." : " ;"));
        }
        return String.join("\n", lines);
    }

    private static List<PropertyShapeModel> rules(NodeShapeModel shape) {
        return shape.getProperties() == null ? List.of() : shape.getProperties();
    }

    /**
     * One {@code sh:property} clause, for a rule the document does not hold yet.
     *
     * <p>{@code indent} is the indentation of the clause itself, so its inner lines are laid out
     * one step further in and the block sits under the shape the way its neighbours do.
     *
     * <p>A rule written as its own resource stays a reference. Inlining it would orphan the
     * statement that defines it and duplicate its constraints.
     */
    static String propertyClause(
            PropertyShapeModel property, PrefixMapping prefixes, String indent) {
        var predicate = term(Shacl.PROPERTY.getURI(), prefixes);
        if (property.getIri() != null) {
            return predicate + " " + term(property.getIri(), prefixes);
        }
        var clauses = new ArrayList<String>();
        if (Boolean.TRUE.equals(property.getTyped())) {
            clauses.add("a " + term(Shacl.NS + "PropertyShape", prefixes));
        }
        add(clauses, Shacl.PATH.getURI(), iri(property.getPath(), prefixes), prefixes);
        add(clauses, ShapeModelReader.NAME.getURI(), string(property.getName()), prefixes);
        add(
                clauses,
                ShapeModelReader.DESCRIPTION.getURI(),
                string(property.getDescription()),
                prefixes);
        add(clauses, Shacl.DATATYPE.getURI(), iri(property.getDataType(), prefixes), prefixes);
        add(clauses, Shacl.CLASS.getURI(), iri(property.getClassIri(), prefixes), prefixes);
        add(clauses, Shacl.NODE_KIND.getURI(), iri(property.getNodeKind(), prefixes), prefixes);
        add(clauses, Shacl.MIN_COUNT.getURI(), number(property.getMinCount()), prefixes);
        add(clauses, Shacl.MAX_COUNT.getURI(), number(property.getMaxCount()), prefixes);
        add(
                clauses,
                Shacl.IN.getURI(),
                collection(property.getAllowedValues(), false, prefixes),
                prefixes);
        add(
                clauses,
                ShapeModelReader.HAS_VALUE.getURI(),
                member(property.getHasValue(), prefixes),
                prefixes);
        add(
                clauses,
                ShapeModelReader.MIN_INCLUSIVE.getURI(),
                number(property.getMinInclusive()),
                prefixes);
        add(
                clauses,
                ShapeModelReader.MAX_INCLUSIVE.getURI(),
                number(property.getMaxInclusive()),
                prefixes);
        add(
                clauses,
                ShapeModelReader.MIN_EXCLUSIVE.getURI(),
                number(property.getMinExclusive()),
                prefixes);
        add(
                clauses,
                ShapeModelReader.MAX_EXCLUSIVE.getURI(),
                number(property.getMaxExclusive()),
                prefixes);
        add(
                clauses,
                ShapeModelReader.MIN_LENGTH.getURI(),
                number(property.getMinLength()),
                prefixes);
        add(
                clauses,
                ShapeModelReader.MAX_LENGTH.getURI(),
                number(property.getMaxLength()),
                prefixes);
        add(clauses, ShapeModelReader.PATTERN.getURI(), string(property.getPattern()), prefixes);
        add(clauses, ShapeModelReader.FLAGS.getURI(), string(property.getFlags()), prefixes);
        add(
                clauses,
                ShapeModelReader.SEVERITY.getURI(),
                iri(property.getSeverity(), prefixes),
                prefixes);
        add(clauses, ShapeModelReader.MESSAGE.getURI(), string(property.getMessage()), prefixes);
        add(clauses, ShapeModelReader.ORDER.getURI(), number(property.getOrder()), prefixes);
        add(clauses, ShapeModelReader.GROUP.getURI(), iri(property.getGroup(), prefixes), prefixes);
        add(clauses, Shacl.DEACTIVATED.getURI(), flag(property.getDeactivated()), prefixes);

        var block = new StringBuilder(predicate + " [\n");
        for (String clause : clauses) {
            block.append(indent).append(INDENT).append(clause).append(" ;\n");
        }
        return block.append(indent).append("]").toString();
    }

    private static void add(
            List<String> clauses, String predicate, String object, PrefixMapping prefixes) {
        if (object != null) {
            clauses.add(term(predicate, prefixes) + " " + object);
        }
    }

    // -------------------------------------------------------------------------
    // Objects
    // -------------------------------------------------------------------------

    /** One term, or {@code null} when the field says nothing. */
    static String iri(String value, PrefixMapping prefixes) {
        return value == null || value.isBlank() ? null : term(value, prefixes);
    }

    /** A comma-separated object list, the way SHACL writes several target classes. */
    static String terms(List<String> values, PrefixMapping prefixes) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        var written = values.stream().filter(value -> value != null && !value.isBlank()).toList();
        return written.isEmpty()
                ? null
                : written.stream()
                        .map(value -> term(value, prefixes))
                        .reduce((a, b) -> a + " , " + b)
                        .orElseThrow();
    }

    /** A plain string literal, or {@code null} for one nobody wrote. */
    static String string(String value) {
        return value == null || value.isEmpty() ? null : quote(value);
    }

    static String number(Integer value) {
        return value == null ? null : value.toString();
    }

    /**
     * A number written bare, for a clause the document does not have yet.
     *
     * <p>Where the clause already exists the digits are replaced inside the literal the document
     * wrote, datatype and all — see {@link ShapeClauseWriter}. Only a brand new clause has no
     * spelling to keep, and a bare number is the plainest thing to give it.
     */
    static String number(String lexical) {
        if (lexical == null || lexical.isBlank()) {
            return null;
        }
        assertIsANumber(lexical);
        return lexical;
    }

    /** Refuses anything a bare number's place in Turtle cannot hold. */
    static void assertIsANumber(String lexical) {
        if (!ShapeModelReader.isBareNumber(lexical)) {
            throw new ResourceConflictException(
                    "\"" + lexical + "\" is not a number, so the form will not write it.");
        }
    }

    /** One value of {@code sh:in} or {@code sh:hasValue}: a term, or a plain string. */
    static String member(String value, PrefixMapping prefixes) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return looksLikeIri(value) ? term(value, prefixes) : quote(value);
    }

    static String flag(Boolean value) {
        return value == null ? null : value.toString();
    }

    /**
     * An RDF collection. {@code sh:ignoredProperties} holds IRIs; {@code sh:in} may hold either, so
     * anything that does not look like an absolute IRI is written as a string.
     */
    static String collection(List<String> values, boolean iris, PrefixMapping prefixes) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        var written = new ArrayList<String>();
        for (String value : values) {
            written.add(iris ? term(value, prefixes) : member(value, prefixes));
        }
        return "( " + String.join(" ", written) + " )";
    }

    private static boolean looksLikeIri(String value) {
        return value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("urn:");
    }

    /**
     * The shortest form of an IRI this document can read: a prefixed name, else angle brackets.
     *
     * <p>{@code qnameFor} rather than {@code shortForm}, because only the former checks that the
     * local part is a legal name. {@code shortForm} matches on the namespace alone, so with {@code
     * ex: <http://example.org/>} bound it happily shortens {@code http://example.org/a/b} to {@code
     * ex:a/b} — which is not Turtle, and turns a form edit into a document that no longer parses.
     *
     * <p>The check is stricter than Turtle needs: it demands an XML NCName, so a local part
     * starting with a digit falls back to angle brackets even though {@code ex:9lives} would have
     * parsed. Writing a valid absolute IRI where a shorter valid one existed is the harmless half
     * of that trade.
     */
    static String term(String iri, PrefixMapping prefixes) {
        var qname = prefixes.qnameFor(iri);
        return qname == null ? "<" + iri + ">" : qname;
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
