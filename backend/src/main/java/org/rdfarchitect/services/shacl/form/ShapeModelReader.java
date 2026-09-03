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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;
import org.rdfarchitect.shacl.dto.RetainedClause;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Reads a shapes document into the structured model the form edits.
 *
 * <p>Two sources, deliberately: the graph says what a shape means, and {@link ShapeSource} says how
 * the document writes it. The form needs both, because an edit has to land in the right clause and
 * leave the rest of the text alone.
 *
 * <p>Naming a predicate is not enough to make it editable. The form holds one value per predicate
 * and the writer spells plain literals, so a shape stating {@code sh:message "…"@en, "…"@de} has
 * nowhere to put the second value and no way to write the language tag. What used to happen then
 * was that the whole shape went read-only. Now the *clause* is kept exactly as written and reported
 * as a {@link RetainedClause}, and everything else about the shape stays editable. Locking a whole
 * shape is now down to one cause: the document not writing it as one statement of its own, so an
 * edit would have no home. Anything the form cannot place *within* a shape — two rules written
 * exactly alike, a rule it cannot match to its text — locks that rule and leaves the rest alone.
 *
 * <p>The SHACL predicates come from CIMVocabCheck's {@code Shacl} rather than being redeclared,
 * except for the handful that class has no constant for.
 */
final class ShapeModelReader {

    static final Node NODE_SHAPE = NodeFactory.createURI(Shacl.NS + "NodeShape");
    static final Node NAME = NodeFactory.createURI(Shacl.NS + "name");
    static final Node DESCRIPTION = NodeFactory.createURI(Shacl.NS + "description");
    static final Node MESSAGE = NodeFactory.createURI(Shacl.NS + "message");
    static final Node SEVERITY = NodeFactory.createURI(Shacl.NS + "severity");
    static final Node ORDER = NodeFactory.createURI(Shacl.NS + "order");
    static final Node GROUP = NodeFactory.createURI(Shacl.NS + "group");
    static final Node CLOSED = NodeFactory.createURI(Shacl.NS + "closed");
    static final Node PATTERN = NodeFactory.createURI(Shacl.NS + "pattern");

    /** The one {@code rdf:type} the writer states on a rule it adds. */
    static final Node PROPERTY_SHAPE = NodeFactory.createURI(Shacl.NS + "PropertyShape");

    /**
     * The shape of value the writer can spell for a predicate.
     *
     * <p>Each case names what the writer emits, which is what decides whether reading a value and
     * writing it again returns the same triple.
     */
    enum ValueKind {
        /** Written as a term: only a URI survives. */
        IRI,
        /** Written as a comma-separated list of terms, so any number of URIs survives. */
        IRIS,
        /** Written as a plain string literal: a language tag or a datatype would be dropped. */
        STRING,
        /** Written as a bare integer. */
        INTEGER,
        /** Written as a bare {@code true} or {@code false}. */
        BOOLEAN,
        /** Written as an RDF collection of terms. */
        IRI_LIST,
        /** Written as an RDF collection whose members may be terms or plain strings. */
        MIXED_LIST
    }

    /** One predicate the form owns: how the writer spells it, and the field it fills. */
    record Field(ValueKind kind, String name) {}

    /** Everything the form models on a node shape. */
    static final Map<Node, Field> NODE_FIELDS =
            Map.ofEntries(
                    Map.entry(Shacl.TARGET_CLASS, new Field(ValueKind.IRIS, "targetClasses")),
                    Map.entry(
                            Shacl.IGNORED_PROPERTIES,
                            new Field(ValueKind.IRI_LIST, "ignoredProperties")),
                    Map.entry(Shacl.DEACTIVATED, new Field(ValueKind.BOOLEAN, "deactivated")),
                    Map.entry(CLOSED, new Field(ValueKind.BOOLEAN, "closed")),
                    Map.entry(NAME, new Field(ValueKind.STRING, "name")),
                    Map.entry(DESCRIPTION, new Field(ValueKind.STRING, "description")),
                    Map.entry(MESSAGE, new Field(ValueKind.STRING, "message")),
                    Map.entry(SEVERITY, new Field(ValueKind.IRI, "severity")));

    /** Everything the form models on a property shape. */
    static final Map<Node, Field> PROPERTY_FIELDS =
            Map.ofEntries(
                    Map.entry(Shacl.PATH, new Field(ValueKind.IRI, "path")),
                    Map.entry(Shacl.DATATYPE, new Field(ValueKind.IRI, "dataType")),
                    Map.entry(Shacl.CLASS, new Field(ValueKind.IRI, "classIri")),
                    Map.entry(Shacl.NODE_KIND, new Field(ValueKind.IRI, "nodeKind")),
                    Map.entry(Shacl.MIN_COUNT, new Field(ValueKind.INTEGER, "minCount")),
                    Map.entry(Shacl.MAX_COUNT, new Field(ValueKind.INTEGER, "maxCount")),
                    Map.entry(Shacl.IN, new Field(ValueKind.MIXED_LIST, "allowedValues")),
                    Map.entry(Shacl.DEACTIVATED, new Field(ValueKind.BOOLEAN, "deactivated")),
                    Map.entry(PATTERN, new Field(ValueKind.STRING, "pattern")),
                    Map.entry(NAME, new Field(ValueKind.STRING, "name")),
                    Map.entry(DESCRIPTION, new Field(ValueKind.STRING, "description")),
                    Map.entry(MESSAGE, new Field(ValueKind.STRING, "message")),
                    Map.entry(SEVERITY, new Field(ValueKind.IRI, "severity")),
                    Map.entry(ORDER, new Field(ValueKind.INTEGER, "order")),
                    Map.entry(GROUP, new Field(ValueKind.IRI, "group")));

    /** Predicates that carry structure rather than a field, and are never rewritten as one. */
    private static final List<String> STRUCTURAL =
            List.of(RDF.type.getURI(), Shacl.PROPERTY.getURI());

    private ShapeModelReader() {}

    /** The graph, the text it was parsed from, and which node shapes reference which named rule. */
    private record Reading(Graph graph, ShapeSource source, Map<String, List<String>> usedBy) {}

    /** Whether the form may write one rule back, and why not when it may not. */
    private record Lock(boolean editable, String reason) {

        static final Lock OPEN = new Lock(true, null);

        /** Open when there is nothing to say, closed with the reason when there is. */
        static Lock of(String reason) {
            return reason == null ? OPEN : new Lock(false, reason);
        }
    }

    /** Both halves of what a document says as shapes: its node shapes and its named rules. */
    record Shapes(List<NodeShapeModel> nodeShapes, List<PropertyShapeModel> propertyShapes) {}

    /**
     * Everything the document says, as shapes: node shapes in IRI order, then the named rules.
     *
     * <p>A shape SHACL infers rather than types — one carrying {@code sh:targetClass} or {@code
     * sh:property} but no {@code a sh:NodeShape} — is read too, and is editable like any other: a
     * clause-level edit changes the clause it was made on and adds no {@code rdf:type} its author
     * chose to leave out.
     */
    static Shapes read(Graph graph, ShapeSource source) {
        var reading = new Reading(graph, source, referencesToRules(graph));
        var declared =
                graph.stream(Node.ANY, RDF.type.asNode(), NODE_SHAPE)
                        .map(Triple::getSubject)
                        .filter(Node::isURI)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var implied =
                Stream.of(Shacl.TARGET_CLASS, Shacl.PROPERTY)
                        .flatMap(predicate -> graph.stream(Node.ANY, predicate, Node.ANY))
                        .map(Triple::getSubject)
                        .filter(Node::isURI)
                        .filter(subject -> !declared.contains(subject))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        var nodeShapes =
                Stream.concat(declared.stream(), implied.stream())
                        .map(shape -> readShape(reading, shape))
                        .sorted(Comparator.comparing(NodeShapeModel::getIri))
                        .toList();
        var byIri = nodeShapes.stream().map(NodeShapeModel::getIri).collect(Collectors.toSet());
        return new Shapes(nodeShapes, readPropertyShapes(reading, byIri));
    }

    /**
     * Which node shapes state {@code sh:property} on each named rule, in IRI order.
     *
     * <p>Read once for the whole document rather than per rule: this is the number the form has to
     * put in front of the user before a shared rule is changed, and in a {@code -Con-Simple-}
     * profile one rule commonly carries the cardinality of dozens of classes.
     */
    private static Map<String, List<String>> referencesToRules(Graph graph) {
        var references = new HashMap<String, SortedSet<String>>();
        graph.stream(Node.ANY, Shacl.PROPERTY, Node.ANY)
                .filter(triple -> triple.getSubject().isURI() && triple.getObject().isURI())
                .forEach(
                        triple ->
                                references
                                        .computeIfAbsent(
                                                triple.getObject().getURI(),
                                                ignored -> new TreeSet<>())
                                        .add(triple.getSubject().getURI()));
        return references.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    private static NodeShapeModel readShape(Reading reading, Node shape) {
        var graph = reading.graph();
        var source = reading.source();
        var written = source.forSubject(shape.getURI());
        var clauses = written == null ? List.<ClauseLocator.Clause>of() : written.clauses();
        var byPredicate = source.byPredicate(clauses);
        var rules = readRules(reading, shape, clauses);
        var unwritable = locatable(written, "shape");

        return NodeShapeModel.builder()
                .iri(shape.getURI())
                .targetClasses(
                        asWritten(
                                byPredicate.get(Shacl.TARGET_CLASS.getURI()),
                                source,
                                () -> uris(graph, shape, Shacl.TARGET_CLASS)))
                .closed(bool(graph, shape, CLOSED))
                .ignoredProperties(
                        RdfLists.uris(graph, object(graph, shape, Shacl.IGNORED_PROPERTIES)))
                .name(string(graph, shape, NAME))
                .description(string(graph, shape, DESCRIPTION))
                .severity(uri(graph, shape, SEVERITY))
                .message(string(graph, shape, MESSAGE))
                .deactivated(bool(graph, shape, Shacl.DEACTIVATED))
                .properties(rules.properties())
                .retained(retainedOf(graph, shape, byPredicate, NODE_FIELDS, source))
                .editable(rules.problem() == null && unwritable == null)
                .readOnlyReason(unwritable != null ? unwritable : rules.problem())
                .build();
    }

    /**
     * The rules the document writes as shapes of their own, in IRI order.
     *
     * <p>This is how the official {@code -Con-Simple-} profiles are composed: their node shapes
     * hold nothing but references, and every cardinality and datatype in the file lives in a named
     * property shape shared by dozens of classes. Shown as cards of their own as well as inline
     * under each shape that references them, because the two views answer different questions —
     * "what does this class have to look like" and "what does this rule say, and who relies on it".
     *
     * <p>A subject already read as a node shape is not repeated here, and neither is a rule the
     * document references but never writes: there would be nothing to show and nothing to edit.
     */
    private static List<PropertyShapeModel> readPropertyShapes(
            Reading reading, Set<String> nodeShapes) {
        var subjects = new LinkedHashSet<String>();
        reading.graph().stream(Node.ANY, RDF.type.asNode(), PROPERTY_SHAPE)
                .map(Triple::getSubject)
                .filter(Node::isURI)
                .map(Node::getURI)
                .forEach(subjects::add);
        subjects.addAll(reading.usedBy().keySet());
        return subjects.stream()
                .filter(iri -> !nodeShapes.contains(iri))
                .map(NodeFactory::createURI)
                .filter(rule -> reading.graph().contains(rule, Node.ANY, Node.ANY))
                .sorted(Comparator.comparing(Node::getURI))
                .map(rule -> readProperty(reading, rule, null, null, Lock.OPEN))
                .toList();
    }

    /**
     * Why the form cannot write this subject back at all, or {@code null} when it can.
     *
     * <p>The one judgement only the text can make, and both cases used to be found out the hard
     * way. A subject written as several statements would have been rewritten into one of them and
     * the others left standing, repeating everything they said. A subject the scanner cannot find —
     * written against a {@code @base}, or nested inside another shape — would have been appended,
     * defining it a second time.
     */
    private static String locatable(ShapeSource.SubjectSource written, String what) {
        if (written == null) {
            return "This "
                    + what
                    + " is not written as a statement of its own in the document, so the form"
                    + " cannot write it back without defining it a second time. Edit it in the"
                    + " Turtle view.";
        }
        if (written.statements().size() > 1) {
            return "This "
                    + what
                    + " is written as "
                    + written.statements().size()
                    + " separate statements. The form would not know which of them an edit"
                    + " belongs in. Edit it in the Turtle view, or write the "
                    + what
                    + " as one statement.";
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Rules
    // -------------------------------------------------------------------------

    /**
     * The shape's rules, and why the shape as a whole cannot be edited when it cannot.
     *
     * @param properties the rules, each carrying whether it can be written and why not
     * @param problem why the whole shape is read-only, or {@code null}
     */
    private record Rules(List<PropertyShapeModel> properties, String problem) {}

    /** How a rule is picked out of the document's text. */
    private enum Match {
        /** The document writes the rule as its own resource, so its IRI names it. */
        NAMED,
        /** An inline rule constraining one property, named by that property. */
        PATH,
        /** An inline rule with no plain property path, named by the predicates it states. */
        STATED
    }

    /**
     * A rule's key, in whichever of the three ways both the graph and the text can see it.
     *
     * <p>A rule usually has no name — {@code sh:property [ … ]} is a blank node — so the graph's
     * view of it and the text's view of it have to be matched up some other way. Its path serves
     * where that path is a plain property; where it is an expression, which the two sides do not
     * spell the same way, the set of predicates the rule states does. Matching once, here, is what
     * lets the writer address a rule by position afterwards, so that editing the path itself does
     * not move the rule out from under the edit.
     */
    private record RuleKey(Match match, String value) {}

    /** One {@code sh:property} object in the text, and the key it is known by. */
    private record WrittenRule(ClauseLocator.ObjectSpan object, RuleKey key) {}

    private static final String TWO_ALIKE =
            "This shape writes two rules the form cannot tell apart, so it will not change either"
                    + " of them — an edit would land in one of the two at random. Edit them in the"
                    + " Turtle view.";

    private static final String UNPLACEABLE =
            "The form cannot tell which part of the document this rule is written in, so it will"
                    + " not change it. Edit it in the Turtle view.";

    /**
     * The one thing that still locks a whole shape besides its being written twice.
     *
     * <p>Reached only when a rule cannot be given even a handle, which needs the graph to hold more
     * rules for a shape than the document writes {@code sh:property} clauses for it. Nothing in the
     * official library does, and nothing valid should — but a shape whose rules cannot all be
     * numbered cannot have one added or removed either, so the refusal is the shape's.
     */
    private static final String UNNUMBERED =
            "The form cannot tell which part of the document this shape's rules are written in, so"
                    + " it will not change it. Edit it in the Turtle view.";

    /**
     * Every rule of one shape, matched to the text it is written in.
     *
     * <p>Locking is per rule. One rule the form cannot place used to make the whole shape read-only
     * — 207 shapes of the generated {@code -AllowedProperties} families state the same rule twice
     * and lost every other field over it — and there is no need: a rule that cannot be placed is
     * simply not written back, while its neighbours in the same statement still can be.
     */
    private static Rules readRules(
            Reading reading, Node shape, List<ClauseLocator.Clause> clauses) {
        var slots = slotsOf(clauses, reading.source());
        var free = new LinkedHashMap<RuleKey, Deque<Integer>>();
        for (int index = 0; index < slots.size(); index++) {
            var key = slots.get(index).key();
            if (key != null) {
                free.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(index);
            }
        }
        var slotsPerKey = new HashMap<RuleKey, Integer>();
        free.forEach((key, ordinals) -> slotsPerKey.put(key, ordinals.size()));

        var rules =
                reading.graph().stream(shape, Shacl.PROPERTY, Node.ANY)
                        .map(Triple::getObject)
                        .toList();
        var keys = rules.stream().map(rule -> keyOf(reading.graph(), rule)).toList();
        var rulesPerKey = new HashMap<RuleKey, Integer>();
        keys.forEach(key -> rulesPerKey.merge(key, 1, Integer::sum));

        var ordinals = new Integer[rules.size()];
        var locks = new Lock[rules.size()];
        var matched = new boolean[rules.size()];
        for (int index = 0; index < rules.size(); index++) {
            var key = keys.get(index);
            var candidates = free.get(key);
            ordinals[index] = candidates == null ? null : candidates.poll();
            matched[index] = ordinals[index] != null;
            var alike = rulesPerKey.get(key) > 1 || slotsPerKey.getOrDefault(key, 0) > 1;
            locks[index] = alike ? Lock.of(TWO_ALIKE) : Lock.OPEN;
        }
        // A rule whose key matched no slot still needs a handle, or the writer would read it as a
        // rule being added and write it a second time. The graph cannot hold more rules than the
        // text writes, so there is always a slot left to name it by — but not one known to be this
        // rule's, so nothing is read from it and the rule is not offered for editing.
        var spare = unclaimed(slots.size(), ordinals);
        var placed = true;
        for (int index = 0; index < rules.size(); index++) {
            if (ordinals[index] == null) {
                ordinals[index] = spare.poll();
                locks[index] = Lock.of(UNPLACEABLE);
                placed &= ordinals[index] != null;
            }
        }

        var properties = new ArrayList<PropertyShapeModel>();
        for (int index = 0; index < rules.size(); index++) {
            properties.add(
                    readProperty(
                            reading,
                            rules.get(index),
                            ordinals[index],
                            matched[index] ? slots.get(ordinals[index]).object() : null,
                            locks[index]));
        }
        return new Rules(
                properties.stream().sorted(byOrderThenPath()).toList(), placed ? null : UNNUMBERED);
    }

    /** Every rule the shape writes, in the order the document writes them. */
    private static List<WrittenRule> slotsOf(
            List<ClauseLocator.Clause> clauses, ShapeSource source) {
        var slots = new ArrayList<WrittenRule>();
        for (ClauseLocator.Clause clause : clauses) {
            if (!Shacl.PROPERTY.getURI().equals(source.predicateIri(clause))) {
                continue;
            }
            for (ClauseLocator.ObjectSpan object : clause.objects()) {
                slots.add(new WrittenRule(object, keyFromText(object, source)));
            }
        }
        return slots;
    }

    /** The ordinals no rule claimed, in document order. */
    private static Deque<Integer> unclaimed(int slots, Integer[] taken) {
        var claimed = Stream.of(taken).filter(Objects::nonNull).collect(Collectors.toSet());
        return IntStream.range(0, slots)
                .boxed()
                .filter(ordinal -> !claimed.contains(ordinal))
                .collect(Collectors.toCollection(ArrayDeque::new));
    }

    /** The rule a {@code sh:property} object names, as the text writes it. */
    private static RuleKey keyFromText(ClauseLocator.ObjectSpan object, ShapeSource source) {
        if (object.nested().isEmpty()) {
            var iri = source.objectIri(object);
            return iri == null ? null : new RuleKey(Match.NAMED, iri);
        }
        return object.nested().stream()
                .filter(clause -> Shacl.PATH.getURI().equals(source.predicateIri(clause)))
                .filter(clause -> clause.objects().size() == 1)
                .map(clause -> source.objectIri(clause.objects().get(0)))
                .filter(Objects::nonNull)
                .findFirst()
                .map(iri -> new RuleKey(Match.PATH, iri))
                .orElseGet(
                        () ->
                                new RuleKey(
                                        Match.STATED,
                                        statedPredicates(
                                                object.nested().stream()
                                                        .map(source::predicateIri))));
    }

    /** The same rule as the graph sees it. */
    private static RuleKey keyOf(Graph graph, Node rule) {
        if (rule.isURI()) {
            return new RuleKey(Match.NAMED, rule.getURI());
        }
        var path = uri(graph, rule, Shacl.PATH);
        if (path != null) {
            return new RuleKey(Match.PATH, path);
        }
        return new RuleKey(
                Match.STATED,
                statedPredicates(
                        graph.stream(rule, Node.ANY, Node.ANY)
                                .map(triple -> triple.getPredicate().getURI())));
    }

    /**
     * What a rule states, as a key: its distinct predicates, sorted.
     *
     * <p>The fallback for a rule with no name and no plain property path — a sequence path, an
     * inverse path — which used to lock its whole shape, because there was nothing left for the two
     * views of it to be matched by. Distinct rather than counted: a graph holds one copy of a
     * triple the document writes twice, and the text holds both.
     */
    private static String statedPredicates(Stream<String> predicates) {
        return predicates
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static PropertyShapeModel readProperty(
            Reading reading,
            Node property,
            Integer ordinal,
            ClauseLocator.ObjectSpan written,
            Lock lock) {
        var graph = reading.graph();
        var source = reading.source();
        // A named rule is written back through its own statement rather than through a shape that
        // references it, so what decides whether it can be written is that statement.
        var effective =
                lock.editable() && property.isURI()
                        ? Lock.of(locatable(source.forSubject(property.getURI()), "rule"))
                        : lock;
        var clauses = clausesOf(property, written, source);
        return PropertyShapeModel.builder()
                .iri(property.isURI() ? property.getURI() : null)
                .sourceIndex(ordinal)
                .path(uri(graph, property, Shacl.PATH))
                .name(string(graph, property, NAME))
                .description(string(graph, property, DESCRIPTION))
                .dataType(uri(graph, property, Shacl.DATATYPE))
                .classIri(uri(graph, property, Shacl.CLASS))
                .nodeKind(uri(graph, property, Shacl.NODE_KIND))
                .minCount(integer(graph, property, Shacl.MIN_COUNT))
                .maxCount(integer(graph, property, Shacl.MAX_COUNT))
                .allowedValues(RdfLists.values(graph, object(graph, property, Shacl.IN)))
                .pattern(string(graph, property, PATTERN))
                .severity(uri(graph, property, SEVERITY))
                .message(string(graph, property, MESSAGE))
                .order(integer(graph, property, ORDER))
                .group(uri(graph, property, GROUP))
                .deactivated(bool(graph, property, Shacl.DEACTIVATED))
                // Recorded so a rewrite puts it back. Restating it unconditionally would add a
                // type to the shapes that leave it implicit, which is most of them.
                .typed(graph.contains(property, RDF.type.asNode(), PROPERTY_SHAPE))
                .retained(
                        retainedOf(
                                graph,
                                property,
                                source.byPredicate(clauses),
                                PROPERTY_FIELDS,
                                source))
                .usedBy(
                        property.isURI()
                                ? reading.usedBy().getOrDefault(property.getURI(), List.of())
                                : List.of())
                .editable(effective.editable())
                .readOnlyReason(effective.reason())
                .build();
    }

    /**
     * Where the document writes this rule's own clauses.
     *
     * <p>Inline for {@code sh:property [ … ]}, and for a rule written as its own resource, the
     * clauses of that resource's statement — which is how the {@code -Con-Simple-} profiles are
     * composed, and why a shared rule can be shown with the values it actually holds.
     */
    private static List<ClauseLocator.Clause> clausesOf(
            Node property, ClauseLocator.ObjectSpan written, ShapeSource source) {
        if (property.isURI()) {
            var subject = source.forSubject(property.getURI());
            return subject == null ? List.of() : subject.clauses();
        }
        return written == null ? List.of() : written.nested();
    }

    /** Reading order in the form: whatever sh:order says, then by path so it is stable. */
    private static Comparator<PropertyShapeModel> byOrderThenPath() {
        return Comparator.comparing(
                        PropertyShapeModel::getOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        PropertyShapeModel::getPath,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    // -------------------------------------------------------------------------
    // What the form keeps as written
    // -------------------------------------------------------------------------

    /**
     * The clauses of {@code subject} the form shows but will not rewrite.
     *
     * <p>Three ways a clause ends up here, and all three used to make the whole shape read-only:
     * the form has no field for the predicate; the document states the predicate more than once,
     * where the form has one field; or the value is one the writer cannot spell again — an {@code
     * xsd:decimal} order, a message with a language tag, {@code sh:closed "yes"}.
     */
    private static List<RetainedClause> retainedOf(
            Graph graph,
            Node subject,
            Map<String, List<ClauseLocator.Clause>> byPredicate,
            Map<Node, Field> fields,
            ShapeSource source) {
        var retained = new ArrayList<RetainedClause>();
        byPredicate.forEach(
                (predicate, stated) -> {
                    if (STRUCTURAL.contains(predicate)) {
                        return;
                    }
                    var field = fields.get(NodeFactory.createURI(predicate));
                    var reason = reasonToKeep(graph, subject, predicate, stated, field);
                    if (reason == null) {
                        return;
                    }
                    stated.forEach(
                            clause ->
                                    retained.add(
                                            RetainedClause.builder()
                                                    .predicate(predicate)
                                                    .value(source.objectsAsWritten(clause))
                                                    .field(field == null ? null : field.name())
                                                    .reason(reason)
                                                    .build()));
                });
        return List.copyOf(retained);
    }

    private static String reasonToKeep(
            Graph graph,
            Node subject,
            String predicate,
            List<ClauseLocator.Clause> stated,
            Field field) {
        if (field == null) {
            return "The form has no field for this, so it stays exactly as the document writes it.";
        }
        if (stated.size() > 1) {
            return "The document states this "
                    + stated.size()
                    + " times and the form has one field for it, so it stays exactly as the"
                    + " document writes it.";
        }
        var values =
                graph.stream(subject, NodeFactory.createURI(predicate), Node.ANY)
                        .map(Triple::getObject)
                        .toList();
        if (faithful(graph, field.kind(), values)) {
            return null;
        }
        return "The form cannot write this value back unchanged, so it stays exactly as the"
                + " document writes it.";
    }

    /** Whether every value of one predicate survives a read and a write unchanged. */
    private static boolean faithful(Graph graph, ValueKind kind, List<Node> values) {
        if (values.isEmpty()) {
            return true;
        }
        if (kind == ValueKind.IRIS) {
            // Repeatable by design: the writer spells them as one comma-separated object list.
            return values.stream().allMatch(Node::isURI);
        }
        // Every other predicate is one field on the form, so a second value has nowhere to go.
        return values.size() == 1 && faithful(graph, kind, values.get(0));
    }

    private static boolean faithful(Graph graph, ValueKind kind, Node value) {
        return switch (kind) {
            case IRI, IRIS -> value.isURI();
            case STRING -> isPlainString(value);
            case INTEGER -> isCanonicalInteger(value);
            case BOOLEAN -> isCanonicalBoolean(value);
            case IRI_LIST ->
                    RdfLists.nodes(graph, value)
                            .map(members -> members.stream().allMatch(Node::isURI))
                            .orElse(false);
            case MIXED_LIST ->
                    RdfLists.nodes(graph, value)
                            .map(
                                    members ->
                                            members.stream()
                                                    .allMatch(ShapeModelReader::faithfulListMember))
                            .orElse(false);
        };
    }

    /**
     * A literal the writer reproduces by printing a bare number.
     *
     * <p>Turtle reads a bare number as {@code xsd:integer}, so only an {@code xsd:integer} written
     * the way {@code Integer.toString} writes it survives: {@code "1"^^xsd:int} would come back
     * with a different datatype, and {@code 01} with different characters.
     */
    private static boolean isCanonicalInteger(Node value) {
        if (!isTyped(value, XSD.integer.getURI())) {
            return false;
        }
        var lexical = value.getLiteralLexicalForm();
        var parsed = parseInt(lexical);
        return parsed != null && parsed.toString().equals(lexical);
    }

    /** A literal the writer reproduces by printing a bare {@code true} or {@code false}. */
    private static boolean isCanonicalBoolean(Node value) {
        return isTyped(value, XSD.xboolean.getURI())
                && parseBoolean(value.getLiteralLexicalForm()).isPresent()
                && value.getLiteralLexicalForm().equals(value.getLiteralLexicalForm().trim());
    }

    private static boolean isTyped(Node value, String datatype) {
        return value.isLiteral()
                && value.getLiteralLanguage().isEmpty()
                && datatype.equals(value.getLiteralDatatypeURI());
    }

    /**
     * Whether one {@code sh:in} member survives the round trip.
     *
     * <p>The writer decides between a term and a quoted string by looking at the value's text, so a
     * plain string that reads like an IRI would come back as an IRI. A typed or language-tagged
     * literal it writes as a plain string, losing what made it different.
     */
    private static boolean faithfulListMember(Node member) {
        if (member.isURI()) {
            return true;
        }
        return isPlainString(member) && !looksLikeIri(member.getLiteralLexicalForm());
    }

    /** Kept in step with {@code ShapeModelWriter.looksLikeIri}. */
    private static boolean looksLikeIri(String value) {
        return value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("urn:");
    }

    /** A literal the writer can reproduce with plain quotes: no language tag, no other datatype. */
    private static boolean isPlainString(Node value) {
        if (!value.isLiteral() || !value.getLiteralLanguage().isEmpty()) {
            return false;
        }
        var datatype = value.getLiteralDatatypeURI();
        return datatype == null || XSD.xstring.getURI().equals(datatype);
    }

    // -------------------------------------------------------------------------
    // Values
    // -------------------------------------------------------------------------

    private static Node object(Graph graph, Node subject, Node predicate) {
        return graph.stream(subject, predicate, Node.ANY)
                .map(Triple::getObject)
                .findFirst()
                .orElse(null);
    }

    private static String uri(Graph graph, Node subject, Node predicate) {
        var object = object(graph, subject, predicate);
        return object != null && object.isURI() ? object.getURI() : null;
    }

    /**
     * A repeatable field's values in the order the document writes them, not the graph's.
     *
     * <p>The graph has no order, and showing two target classes the other way round would be the
     * form reordering a clause nobody edited the moment anything else about it changed. Falls back
     * to the graph only where the document does not write the shape as a statement of its own, and
     * so has no order to take.
     */
    private static List<String> asWritten(
            List<ClauseLocator.Clause> stated,
            ShapeSource source,
            java.util.function.Supplier<List<String>> fromGraph) {
        if (stated == null || stated.isEmpty()) {
            return fromGraph.get();
        }
        var values = new ArrayList<String>();
        for (ClauseLocator.Clause clause : stated) {
            for (ClauseLocator.ObjectSpan object : clause.objects()) {
                var iri = source.objectIri(object);
                if (iri == null) {
                    return fromGraph.get();
                }
                values.add(iri);
            }
        }
        return List.copyOf(values);
    }

    /** Every URI value of a repeatable predicate, in the order the graph holds them. */
    private static List<String> uris(Graph graph, Node subject, Node predicate) {
        return graph.stream(subject, predicate, Node.ANY)
                .map(Triple::getObject)
                .filter(Node::isURI)
                .map(Node::getURI)
                .distinct()
                .toList();
    }

    private static String string(Graph graph, Node subject, Node predicate) {
        var object = object(graph, subject, predicate);
        return object != null && object.isLiteral() ? object.getLiteralLexicalForm() : null;
    }

    private static Integer integer(Graph graph, Node subject, Node predicate) {
        var lexical = string(graph, subject, predicate);
        return lexical == null ? null : parseInt(lexical);
    }

    private static Integer parseInt(String lexical) {
        try {
            return Integer.valueOf(lexical.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Graph graph, Node subject, Node predicate) {
        var lexical = string(graph, subject, predicate);
        return lexical == null ? null : parseBoolean(lexical).orElse(null);
    }

    /**
     * {@code true} or {@code false}, or empty for anything else.
     *
     * <p>Anything-that-is-not-true used to read as {@code false}, which invented a rule: {@code
     * sh:closed "yes"} became {@code sh:closed false} on the next save. An unreadable value is
     * reported as kept-as-written instead, and the field is shown but not offered for editing.
     *
     * <p>{@code Optional} rather than a nullable {@code Boolean}: a three-valued Boolean is a
     * standing invitation to unbox one, and the two callers want different things from a value that
     * will not parse — one reports it as unwritable, the other treats it as absent.
     */
    private static Optional<Boolean> parseBoolean(String lexical) {
        var trimmed = lexical.trim();
        if ("true".equals(trimmed)) {
            return Optional.of(Boolean.TRUE);
        }
        return "false".equals(trimmed) ? Optional.of(Boolean.FALSE) : Optional.empty();
    }
}
