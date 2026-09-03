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

import org.apache.jena.graph.Node;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;
import org.rdfarchitect.shacl.dto.RetainedClause;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes a form edit into the clause it was made on, and touches nothing else.
 *
 * <p>This is the piece that makes the form usable on a document somebody else wrote. The shape is
 * not respelled from the model — it is compared against what the document already says, and only
 * the clauses that genuinely differ are changed: the object of one clause replaced, a clause added
 * for a field that was empty, a clause removed for one that was cleared. Everything else keeps the
 * characters its author typed, comments and clause order included, whether or not the form has a
 * field for it.
 *
 * <p>Each change is applied to the text the previous one produced and the statement is rescanned in
 * between, rather than collecting spans up front and hoping they still line up. Documents are small
 * and edits are few; a stale offset would corrupt a file.
 */
final class ShapeClauseWriter {

    /** The edited document, and what the edit cost beyond the change itself. */
    record Result(String turtle, List<String> warnings) {}

    /**
     * One clause to change: {@code object} of {@code null} means the field was cleared.
     *
     * <p>{@code keepsSpelling} is for a number: the new digits go inside the literal the document
     * already wrote, so {@code "0.0"^^xsd:float} becomes {@code "1.5"^^xsd:float} rather than a
     * bare decimal. It says "replace the value, not the way it is written".
     */
    private record ClauseChange(
            Integer ordinal,
            String predicate,
            String field,
            String object,
            boolean keepsSpelling) {}

    /** Where a new clause goes, and how the surrounding text lays its clauses out. */
    private record Insertion(int at, String indent, String before, String after) {}

    /** A predicate-object list and the span it is written in: a statement, or one {@code [ … ]}. */
    private record Region(List<ClauseLocator.Clause> clauses, int start, int end) {}

    private ShapeClauseWriter() {}

    /**
     * The document with the difference between what it says about a shape and {@code incoming}
     * written into it.
     *
     * @param stored the shape as the document holds it, read by {@link ShapeModelReader}
     * @param incoming the shape as the form sends it back
     */
    static Result rewrite(
            String turtle, NodeShapeModel stored, NodeShapeModel incoming, PrefixMapping prefixes) {
        var diff = new Diff(prefixes);
        var removed = new ArrayList<Integer>();
        var added = new ArrayList<PropertyShapeModel>();
        diffShape(diff, stored, incoming);
        diffRules(diff, removed, added, stored, incoming);

        var text = turtle;
        var warnings = new ArrayList<String>();
        for (ClauseChange change : diff.changes()) {
            text = applyClause(text, stored.getIri(), change, prefixes, warnings);
        }
        // Back to front, so removing one rule does not move the rules an earlier ordinal names.
        removed.sort(Comparator.reverseOrder());
        for (int ordinal : removed) {
            text = applyRemoval(text, stored.getIri(), ordinal, prefixes, warnings);
        }
        for (PropertyShapeModel rule : added) {
            text = applyAddition(text, stored.getIri(), rule, prefixes);
        }
        return new Result(text, List.copyOf(warnings));
    }

    /**
     * The same, for a rule the document writes as a shape of its own.
     *
     * <p>Reached instead of {@link #rewrite} when the edit was made on a shared rule: its clauses
     * live in its own statement, so that is the statement the change goes into, and every node
     * shape referencing it sees the change at once. Which is the point of the split below.
     *
     * @param stored the rule as the document holds it, read by {@link ShapeModelReader}
     * @param incoming the rule as the form sends it back
     */
    static Result rewriteRule(
            String turtle,
            PropertyShapeModel stored,
            PropertyShapeModel incoming,
            PrefixMapping prefixes) {
        var diff = new Diff(prefixes, "rule");
        diff.about(null, stored.getRetained());
        diffRuleFields(diff, stored, incoming);

        var text = turtle;
        var warnings = new ArrayList<String>();
        for (ClauseChange change : diff.changes()) {
            text = applyClause(text, stored.getIri(), change, prefixes, warnings);
        }
        return new Result(text, List.copyOf(warnings));
    }

    /**
     * Changes a shared rule for one shape only, by giving that shape a copy of it.
     *
     * <p>The copy is taken from the rule's own text and only its subject is respelled, so it starts
     * out saying exactly what the original says, comments and layout included — writing it out from
     * the model would quietly drop whatever the form has no field for. Then the one reference is
     * moved to the copy, and the edit is applied there. The original, and every other shape using
     * it, is left as it was.
     *
     * @param at which of the node shape's rules is repointed, as the reader numbered them
     */
    static Result splitRule(
            String turtle,
            PropertyShapeModel stored,
            PropertyShapeModel incoming,
            String newIri,
            String nodeShapeIri,
            int at,
            PrefixMapping prefixes) {
        var statement = onlyStatement(turtle, stored.getIri(), prefixes, "rule");
        var copy =
                ShapeModelWriter.term(newIri, prefixes)
                        + turtle.substring(
                                statement.start() + statement.subjectToken().length(),
                                statement.end());
        var separator = turtle.endsWith("\n\n") ? "" : turtle.endsWith("\n") ? "\n" : "\n\n";
        var text =
                repoint(
                        turtle + separator + copy + "\n",
                        nodeShapeIri,
                        at,
                        stored,
                        newIri,
                        prefixes);

        var copied = copyOf(stored);
        copied.setIri(newIri);
        var edited = copyOf(incoming);
        edited.setIri(newIri);
        return rewriteRule(text, copied, edited, prefixes);
    }

    /** Points one {@code sh:property} reference of a node shape at the copy instead. */
    private static String repoint(
            String text,
            String nodeShapeIri,
            int at,
            PropertyShapeModel rule,
            String newIri,
            PrefixMapping prefixes) {
        var statement = onlyStatement(text, nodeShapeIri, prefixes, "shape");
        var slots = propertyObjects(ClauseLocator.of(text, statement), prefixes);
        if (at < 0 || at >= slots.size()) {
            throw new ResourceConflictException(
                    "The form is showing a rule the document no longer has. Reload the document"
                            + " and make the change again.");
        }
        var object = slots.get(at).object();
        var token = text.substring(object.start(), object.end());
        if (!rule.getIri().equals(ShapeBlockLocator.expand(token, prefixes))) {
            throw new ResourceConflictException(
                    "This shape no longer uses that rule where the form thought it did. Reload"
                            + " the document and make the change again.");
        }
        return text.substring(0, object.start())
                + ShapeModelWriter.term(newIri, prefixes)
                + text.substring(object.end());
    }

    /** The one statement a subject is written as, or a refusal naming what is wrong with it. */
    private static ShapeBlockLocator.Statement onlyStatement(
            String text, String iri, PrefixMapping prefixes, String what) {
        var statements = ShapeBlockLocator.locateAll(text, iri, prefixes);
        if (statements.size() != 1) {
            throw new ResourceConflictException(
                    "This "
                            + what
                            + " is no longer written as one statement of its own, so the form"
                            + " cannot change it. Edit it in the Turtle view.");
        }
        return statements.get(0);
    }

    /** A rule with the same fields, so the original the caller holds is not renamed under it. */
    private static PropertyShapeModel copyOf(PropertyShapeModel rule) {
        var copy = new PropertyShapeModel();
        copy.setIri(rule.getIri());
        copy.setSourceIndex(rule.getSourceIndex());
        copy.setPath(rule.getPath());
        copy.setName(rule.getName());
        copy.setDescription(rule.getDescription());
        copy.setDataType(rule.getDataType());
        copy.setClassIri(rule.getClassIri());
        copy.setNodeKind(rule.getNodeKind());
        copy.setMinCount(rule.getMinCount());
        copy.setMaxCount(rule.getMaxCount());
        copy.setAllowedValues(rule.getAllowedValues());
        copy.setPattern(rule.getPattern());
        copy.setSeverity(rule.getSeverity());
        copy.setMessage(rule.getMessage());
        copy.setOrder(rule.getOrder());
        copy.setGroup(rule.getGroup());
        copy.setDeactivated(rule.getDeactivated());
        copy.setTyped(rule.getTyped());
        copy.setRetained(rule.getRetained());
        copy.setUsedBy(rule.getUsedBy());
        copy.setEditable(rule.getEditable());
        copy.setReadOnlyReason(rule.getReadOnlyReason());
        return copy;
    }

    // -------------------------------------------------------------------------
    // What changed
    // -------------------------------------------------------------------------

    private static void diffShape(Diff diff, NodeShapeModel stored, NodeShapeModel incoming) {
        diff.about(null, stored.getRetained());
        diff.iris(
                Shacl.TARGET_CLASS,
                "targetClasses",
                stored.getTargetClasses(),
                incoming.getTargetClasses());
        diff.iris(
                ShapeModelReader.TARGET_SUBJECTS_OF,
                "targetSubjectsOf",
                stored.getTargetSubjectsOf(),
                incoming.getTargetSubjectsOf());
        diff.iris(
                ShapeModelReader.TARGET_OBJECTS_OF,
                "targetObjectsOf",
                stored.getTargetObjectsOf(),
                incoming.getTargetObjectsOf());
        diff.iris(
                ShapeModelReader.TARGET_NODE,
                "targetNodes",
                stored.getTargetNodes(),
                incoming.getTargetNodes());
        diff.text(ShapeModelReader.NAME, "name", stored.getName(), incoming.getName());
        diff.text(
                ShapeModelReader.DESCRIPTION,
                "description",
                stored.getDescription(),
                incoming.getDescription());
        diff.text(ShapeModelReader.MESSAGE, "message", stored.getMessage(), incoming.getMessage());
        diff.iri(
                ShapeModelReader.SEVERITY,
                "severity",
                stored.getSeverity(),
                incoming.getSeverity());
        diff.flag(ShapeModelReader.CLOSED, "closed", stored.getClosed(), incoming.getClosed());
        diff.flag(
                Shacl.DEACTIVATED,
                "deactivated",
                stored.getDeactivated(),
                incoming.getDeactivated());
        diff.collection(
                Shacl.IGNORED_PROPERTIES,
                "ignoredProperties",
                stored.getIgnoredProperties(),
                incoming.getIgnoredProperties(),
                true);
    }

    /**
     * Which rules were changed, which were added, and which are gone.
     *
     * <p>A rule is matched by the {@code sourceIndex} the reader gave it rather than by its path,
     * because the path is one of the things being edited. A shape sent back without a rule list at
     * all is an edit that says nothing about the rules, not one that asks for all of them to go.
     */
    private static void diffRules(
            Diff diff,
            List<Integer> removed,
            List<PropertyShapeModel> added,
            NodeShapeModel stored,
            NodeShapeModel incoming) {
        if (incoming.getProperties() == null) {
            return;
        }
        var byOrdinal = new HashMap<Integer, PropertyShapeModel>();
        for (PropertyShapeModel rule : stored.getProperties()) {
            byOrdinal.put(rule.getSourceIndex(), rule);
        }
        var kept = new HashSet<Integer>();
        for (PropertyShapeModel rule : incoming.getProperties()) {
            if (rule.getSourceIndex() == null) {
                added.add(rule);
                continue;
            }
            var was = byOrdinal.get(rule.getSourceIndex());
            if (was == null) {
                throw new ResourceConflictException(
                        "The form is showing a rule the document no longer has. Reload the"
                                + " document and make the change again.");
            }
            kept.add(rule.getSourceIndex());
            diffRule(diff, was, rule);
        }
        byOrdinal.forEach(
                (ordinal, rule) -> {
                    if (!kept.contains(ordinal)) {
                        assertRemovable(rule);
                        removed.add(ordinal);
                    }
                });
    }

    /**
     * Refuses to remove a rule the reader could not place.
     *
     * <p>Such a rule was given an ordinal so it could be named at all, but which of the shape's
     * {@code sh:property} clauses it is remains a guess — two rules written exactly alike get one
     * ordinal each and there is no telling which. Removing by ordinal would take one of them at
     * random. Its fields are refused one by one below; removal has to be refused as a whole.
     */
    private static void assertRemovable(PropertyShapeModel rule) {
        if (Boolean.FALSE.equals(rule.getEditable())) {
            throw new ResourceConflictException(reasonNotToWrite(rule));
        }
    }

    private static String reasonNotToWrite(PropertyShapeModel rule) {
        return rule.getReadOnlyReason() != null
                ? rule.getReadOnlyReason()
                : "The form cannot tell which part of the document this rule is written in, so it"
                        + " will not change it.";
    }

    private static void diffRule(Diff diff, PropertyShapeModel was, PropertyShapeModel now) {
        if (Boolean.FALSE.equals(was.getEditable())) {
            // A rule the reader could not place in the text, or one of two written exactly alike.
            diff.about(now.getSourceIndex(), everyFieldLocked(reasonNotToWrite(was)));
        } else if (was.getIri() != null) {
            // A rule written as its own shape, referenced from here. It is edited on itself, so
            // that the form can first say how many other shapes the change would reach.
            diff.about(now.getSourceIndex(), everyFieldLocked(SHARED_RULE));
        } else {
            diff.about(now.getSourceIndex(), was.getRetained());
        }
        diffRuleFields(diff, was, now);
    }

    /** The fields of one rule, whichever statement it is written in. */
    private static void diffRuleFields(Diff diff, PropertyShapeModel was, PropertyShapeModel now) {
        diff.iri(Shacl.PATH, "path", was.getPath(), now.getPath());
        diff.text(ShapeModelReader.NAME, "name", was.getName(), now.getName());
        diff.text(
                ShapeModelReader.DESCRIPTION,
                "description",
                was.getDescription(),
                now.getDescription());
        diff.iri(Shacl.DATATYPE, "dataType", was.getDataType(), now.getDataType());
        diff.iri(Shacl.CLASS, "classIri", was.getClassIri(), now.getClassIri());
        diff.iri(Shacl.NODE_KIND, "nodeKind", was.getNodeKind(), now.getNodeKind());
        diff.number(Shacl.MIN_COUNT, "minCount", was.getMinCount(), now.getMinCount());
        diff.number(Shacl.MAX_COUNT, "maxCount", was.getMaxCount(), now.getMaxCount());
        diff.collection(
                Shacl.IN, "allowedValues", was.getAllowedValues(), now.getAllowedValues(), false);
        diff.member(ShapeModelReader.HAS_VALUE, "hasValue", was.getHasValue(), now.getHasValue());
        diff.numeric(
                ShapeModelReader.MIN_INCLUSIVE,
                "minInclusive",
                was.getMinInclusive(),
                now.getMinInclusive());
        diff.numeric(
                ShapeModelReader.MAX_INCLUSIVE,
                "maxInclusive",
                was.getMaxInclusive(),
                now.getMaxInclusive());
        diff.numeric(
                ShapeModelReader.MIN_EXCLUSIVE,
                "minExclusive",
                was.getMinExclusive(),
                now.getMinExclusive());
        diff.numeric(
                ShapeModelReader.MAX_EXCLUSIVE,
                "maxExclusive",
                was.getMaxExclusive(),
                now.getMaxExclusive());
        diff.number(
                ShapeModelReader.MIN_LENGTH, "minLength", was.getMinLength(), now.getMinLength());
        diff.number(
                ShapeModelReader.MAX_LENGTH, "maxLength", was.getMaxLength(), now.getMaxLength());
        diff.text(ShapeModelReader.PATTERN, "pattern", was.getPattern(), now.getPattern());
        diff.text(ShapeModelReader.FLAGS, "flags", was.getFlags(), now.getFlags());
        diff.iri(ShapeModelReader.SEVERITY, "severity", was.getSeverity(), now.getSeverity());
        diff.text(ShapeModelReader.MESSAGE, "message", was.getMessage(), now.getMessage());
        diff.numeric(ShapeModelReader.ORDER, "order", was.getOrder(), now.getOrder());
        diff.iri(ShapeModelReader.GROUP, "group", was.getGroup(), now.getGroup());
        diff.flag(Shacl.DEACTIVATED, "deactivated", was.getDeactivated(), now.getDeactivated());
    }

    private static final String SHARED_RULE =
            "This rule is written as its own shape, so changing it here would change it for every"
                    + " shape that references it. Change it on the rule itself, where the form can"
                    + " offer to give this shape a copy of it instead.";

    /** Every field of a rule, locked, so a change to any of them is refused by name. */
    private static List<RetainedClause> everyFieldLocked(String reason) {
        return ShapeModelReader.PROPERTY_FIELDS.values().stream()
                .map(field -> RetainedClause.builder().field(field.name()).reason(reason).build())
                .toList();
    }

    /** Collects the clauses an edit has to change, and refuses the ones it must not. */
    private static final class Diff {

        private final List<ClauseChange> changes = new ArrayList<>();
        private final PrefixMapping prefixes;

        /** What the subject of this diff is called, for a refusal that has to name it. */
        private final String subject;

        private Integer ordinal;
        private Map<String, String> locked = Map.of();

        private Diff(PrefixMapping prefixes) {
            this(prefixes, "shape");
        }

        private Diff(PrefixMapping prefixes, String subject) {
            this.prefixes = prefixes;
            this.subject = subject;
        }

        List<ClauseChange> changes() {
            return changes;
        }

        /** Switches to the rule written at {@code ordinal}, or to the shape itself for null. */
        void about(Integer ordinal, List<RetainedClause> retained) {
            this.ordinal = ordinal;
            this.locked = new HashMap<>();
            if (retained == null) {
                return;
            }
            retained.stream()
                    .filter(clause -> clause.getField() != null)
                    .forEach(clause -> locked.putIfAbsent(clause.getField(), clause.getReason()));
        }

        void iri(Node predicate, String field, String was, String now) {
            note(
                    predicate,
                    field,
                    blank(was),
                    blank(now),
                    ShapeModelWriter.iri(blank(now), prefixes));
        }

        void iris(Node predicate, String field, List<String> was, List<String> now) {
            note(
                    predicate,
                    field,
                    values(was),
                    values(now),
                    ShapeModelWriter.terms(values(now), prefixes));
        }

        void text(Node predicate, String field, String was, String now) {
            note(predicate, field, empty(was), empty(now), ShapeModelWriter.string(empty(now)));
        }

        void number(Node predicate, String field, Integer was, Integer now) {
            note(predicate, field, was, now, ShapeModelWriter.number(now));
        }

        /** A number whose new digits go inside whatever literal the document already wrote. */
        void numeric(Node predicate, String field, String was, String now) {
            var value = blank(now);
            note(predicate, field, blank(was), value, ShapeModelWriter.number(value), true);
        }

        /** One value written the way a {@code sh:in} member is: a term, or a plain string. */
        void member(Node predicate, String field, String was, String now) {
            note(
                    predicate,
                    field,
                    empty(was),
                    empty(now),
                    ShapeModelWriter.member(empty(now), prefixes));
        }

        void flag(Node predicate, String field, Boolean was, Boolean now) {
            note(predicate, field, was, now, ShapeModelWriter.flag(now));
        }

        void collection(
                Node predicate, String field, List<String> was, List<String> now, boolean iris) {
            note(
                    predicate,
                    field,
                    values(was),
                    values(now),
                    ShapeModelWriter.collection(values(now), iris, prefixes));
        }

        private void note(Node predicate, String field, Object was, Object now, String object) {
            note(predicate, field, was, now, object, false);
        }

        private void note(
                Node predicate,
                String field,
                Object was,
                Object now,
                String object,
                boolean keepsSpelling) {
            if (Objects.equals(was, now)) {
                return;
            }
            var reason = locked.get(field);
            if (reason != null) {
                throw new ResourceConflictException(
                        "The form does not change "
                                + predicate.getLocalName()
                                + (ordinal == null
                                        ? " on this " + subject + ". "
                                        : " on this rule. ")
                                + reason);
            }
            changes.add(
                    new ClauseChange(ordinal, predicate.getURI(), field, object, keepsSpelling));
        }

        /** An IRI field: written and blank mean the same thing, which is "not stated". */
        private static String blank(String value) {
            return value == null || value.isBlank() ? null : value;
        }

        /** A text field: an empty message is no message, which is how the form clears one. */
        private static String empty(String value) {
            return value == null || value.isEmpty() ? null : value;
        }

        private static List<String> values(List<String> values) {
            return values == null
                    ? List.of()
                    : values.stream().filter(value -> value != null && !value.isBlank()).toList();
        }
    }

    // -------------------------------------------------------------------------
    // Writing it in
    // -------------------------------------------------------------------------

    private static String applyClause(
            String text,
            String iri,
            ClauseChange change,
            PrefixMapping prefixes,
            List<String> warnings) {
        var region = regionFor(text, iri, change.ordinal(), prefixes);
        var stated =
                region.clauses().stream()
                        .filter(
                                clause ->
                                        change.predicate()
                                                .equals(
                                                        ShapeSource.predicateIriOf(
                                                                clause, prefixes)))
                        .toList();
        if (stated.size() > 1) {
            // The reader keeps such a field as written and the form shows it read-only, so this is
            // a client that asked anyway. Rewriting one of the clauses would leave the other
            // standing and the shape saying two things.
            throw new ResourceConflictException(
                    "The document states this more than once, so the form cannot change it. Edit"
                            + " it in the Turtle view.");
        }
        if (stated.isEmpty()) {
            return change.object() == null
                    ? text
                    : insert(
                            text,
                            region,
                            ShapeModelWriter.term(change.predicate(), prefixes)
                                    + " "
                                    + change.object());
        }
        var clause = stated.get(0);
        if (change.object() == null) {
            return delete(text, region, clause, warnings);
        }
        var written = text.substring(clause.objectsStart(), clause.objectsEnd());
        var replacement =
                change.keepsSpelling() && clause.objects().size() == 1
                        ? respell(written, change.object())
                        : change.object();
        return text.substring(0, clause.objectsStart())
                + replacement
                + text.substring(clause.objectsEnd());
    }

    /**
     * The object the document wrote, saying {@code value} instead.
     *
     * <p>Only the part between the quotes is replaced, so the datatype and the quoting style the
     * author chose survive an edit to the number they hold. A bare number has no shell to keep and
     * is simply replaced. This is what makes the official library's 1282 {@code "0.0"^^xsd:float}
     * value ranges editable rather than merely visible: a form holding a {@code double} would have
     * written back {@code 0.0} and quietly changed the datatype.
     */
    private static String respell(String written, String value) {
        if (written.isEmpty() || (written.charAt(0) != '"' && written.charAt(0) != '\'')) {
            return value;
        }
        var quote = String.valueOf(written.charAt(0));
        var delimiter = written.startsWith(quote.repeat(3)) ? quote.repeat(3) : quote;
        var closes = written.indexOf(delimiter, delimiter.length());
        return closes < 0 ? value : delimiter + value + written.substring(closes);
    }

    private static String applyAddition(
            String text, String iri, PropertyShapeModel rule, PrefixMapping prefixes) {
        var region = regionFor(text, iri, null, prefixes);
        var where = insertionFor(text, region);
        return insert(
                text, region, ShapeModelWriter.propertyClause(rule, prefixes, where.indent()));
    }

    /**
     * Removes the rule written at {@code ordinal}.
     *
     * <p>Usually that is a whole {@code sh:property} clause. A shape that states two rules under
     * one clause — {@code sh:property [ … ] , [ … ]} — loses only the one object and the comma that
     * joined it.
     */
    private static String applyRemoval(
            String text, String iri, int ordinal, PrefixMapping prefixes, List<String> warnings) {
        var region = regionFor(text, iri, null, prefixes);
        var slots = propertyObjects(region.clauses(), prefixes);
        if (ordinal >= slots.size()) {
            throw new ResourceConflictException(
                    "The form is showing a rule the document no longer has. Reload the document"
                            + " and make the change again.");
        }
        var clause = slots.get(ordinal).clause();
        var object = slots.get(ordinal).object();
        if (clause.objects().size() == 1) {
            return delete(text, region, clause, warnings);
        }
        int at = clause.objects().indexOf(object);
        int from = at == 0 ? object.start() : clause.objects().get(at - 1).end();
        int to = at == 0 ? clause.objects().get(1).start() : object.end();
        warnAboutComments(text, from, to, warnings);
        return text.substring(0, from) + text.substring(to);
    }

    /** One rule's place in the text: which clause states it, and which of that clause's objects. */
    private record Slot(ClauseLocator.Clause clause, ClauseLocator.ObjectSpan object) {}

    /** Every rule the shape states, in the order {@link ShapeModelReader} numbered them. */
    private static List<Slot> propertyObjects(
            List<ClauseLocator.Clause> clauses, PrefixMapping prefixes) {
        var slots = new ArrayList<Slot>();
        for (ClauseLocator.Clause clause : clauses) {
            if (!Shacl.PROPERTY.getURI().equals(ShapeSource.predicateIriOf(clause, prefixes))) {
                continue;
            }
            clause.objects().forEach(object -> slots.add(new Slot(clause, object)));
        }
        return slots;
    }

    /** The clauses an edit may touch: the shape's own, or those of one of its inline rules. */
    private static Region regionFor(
            String text, String iri, Integer ordinal, PrefixMapping prefixes) {
        var statement = onlyStatement(text, iri, prefixes, "shape");
        var clauses = ClauseLocator.of(text, statement);
        if (ordinal == null) {
            int from = statement.start() + statement.subjectToken().length();
            int to = statement.end();
            return new Region(clauses, from, text.charAt(to - 1) == '.' ? to - 1 : to);
        }
        var slots = propertyObjects(clauses, prefixes);
        if (ordinal >= slots.size() || slots.get(ordinal).object().nested().isEmpty()) {
            throw new ResourceConflictException(
                    "The form is showing a rule the document no longer writes here. Reload the"
                            + " document and make the change again.");
        }
        var object = slots.get(ordinal).object();
        return new Region(object.nested(), object.start() + 1, object.end() - 1);
    }

    private static String insert(String text, Region region, String clause) {
        var where = insertionFor(text, region);
        return text.substring(0, where.at())
                + where.before()
                + clause
                + where.after()
                + text.substring(where.at());
    }

    /**
     * Where a clause the document does not have yet goes, laid out the way its neighbours are.
     *
     * <p>After the last clause, so the document's own clause order is left alone — the new one is
     * simply the newest. Whether it goes on a line of its own is read off the last clause: a shape
     * whose clauses each have a line keeps that, and one written on a single line stays on it.
     */
    private static Insertion insertionFor(String text, Region region) {
        if (region.clauses().isEmpty()) {
            return new Insertion(region.start(), ShapeModelWriter.INDENT, " ", " ");
        }
        var last = region.clauses().get(region.clauses().size() - 1);
        var indent = lineIndent(text, last.start());
        return indent == null
                ? new Insertion(last.end(), ShapeModelWriter.INDENT, " ; ", "")
                : new Insertion(last.end(), indent, " ;\n" + indent, "");
    }

    /**
     * The whitespace in front of {@code at} on its line, or {@code null} when it is not alone
     * there.
     */
    private static String lineIndent(String text, int at) {
        int lineStart = text.lastIndexOf('\n', at - 1) + 1;
        var prefix = text.substring(lineStart, at);
        return prefix.isBlank() ? prefix : null;
    }

    /**
     * Removes one clause, and the separator that joined it to its neighbours.
     *
     * <p>A clause on a line of its own takes the whole line with it, so clearing a field does not
     * leave a blank one behind. The last clause of a list is taken back to the end of the one
     * before it, which is where its {@code ;} is.
     */
    private static String delete(
            String text, Region region, ClauseLocator.Clause clause, List<String> warnings) {
        var clauses = region.clauses();
        int at = clauses.indexOf(clause);
        if (clauses.size() == 1) {
            throw new ResourceConflictException(
                    "This is the only thing the shape says, and a shape cannot say nothing. Give"
                            + " it a class or a rule first, or delete the shape.");
        }
        if (at == clauses.size() - 1) {
            int from = clauses.get(at - 1).end();
            warnAboutComments(text, from, clause.end(), warnings);
            return text.substring(0, from) + text.substring(clause.end());
        }
        int lineStart = text.lastIndexOf('\n', clause.start() - 1) + 1;
        boolean ownLine = text.substring(lineStart, clause.start()).isBlank();
        int from = ownLine ? lineStart : clause.start();
        int separator = firstMeaningful(text, clause.end(), region.end());
        int to = separator >= 0 && text.charAt(separator) == ';' ? separator + 1 : clause.end();
        while (to < region.end() && (text.charAt(to) == ' ' || text.charAt(to) == '\t')) {
            to++;
        }
        if (ownLine && to < region.end() && text.charAt(to) == '\n') {
            to++;
        }
        warnAboutComments(text, from, to, warnings);
        return text.substring(0, from) + text.substring(to);
    }

    /** The first index at or after {@code from} holding something other than whitespace. */
    private static int firstMeaningful(String text, int from, int limit) {
        for (int index = from; index < limit; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static void warnAboutComments(String text, int from, int to, List<String> warnings) {
        var removed = text.substring(from, to);
        if (ShapeBlockLocator.containsComment(removed) && warnings.isEmpty()) {
            warnings.add(
                    "A comment written in the part of this shape the change removed went with it."
                            + " The rest of the document is unchanged.");
        }
    }
}
