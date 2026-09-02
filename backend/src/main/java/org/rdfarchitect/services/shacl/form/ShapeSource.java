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

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A constraints document as its author wrote it: which subject is written where, and how.
 *
 * <p>The graph a document parses to answers what it means; this answers what it says, which is the
 * half a form edit has to keep. Built in one scan for the whole document, because the alternative —
 * asking {@link ShapeBlockLocator} per subject — rescans from the front every time and is quadratic
 * on a file with thousands of subjects.
 *
 * <p>Holds the document's prefixes as well as its text, because reading a clause's predicate means
 * resolving the token the document wrote it as.
 */
final class ShapeSource {

    /** Everything one subject's name appears in front of, across however many statements. */
    record SubjectSource(
            List<ShapeBlockLocator.Statement> statements, List<ClauseLocator.Clause> clauses) {}

    private final String turtle;
    private final PrefixMapping prefixes;
    private final Map<String, SubjectSource> bySubject;

    private ShapeSource(
            String turtle, PrefixMapping prefixes, Map<String, SubjectSource> bySubject) {
        this.turtle = turtle;
        this.prefixes = prefixes;
        this.bySubject = bySubject;
    }

    static ShapeSource of(String turtle, PrefixMapping prefixes) {
        var text = turtle == null ? "" : turtle;
        var statements = new LinkedHashMap<String, List<ShapeBlockLocator.Statement>>();
        var clauses = new LinkedHashMap<String, List<ClauseLocator.Clause>>();
        for (ShapeBlockLocator.Statement statement : ShapeBlockLocator.statements(text)) {
            var iri = ShapeBlockLocator.expand(statement.subjectToken(), prefixes);
            if (iri == null) {
                // A blank node subject, or one whose prefix the document does not bind. Neither is
                // a shape the form can address, and the parse would have failed on the latter.
                continue;
            }
            statements.computeIfAbsent(iri, ignored -> new ArrayList<>()).add(statement);
            clauses.computeIfAbsent(iri, ignored -> new ArrayList<>())
                    .addAll(ClauseLocator.of(text, statement));
        }
        var bySubject = new LinkedHashMap<String, SubjectSource>();
        statements.forEach(
                (iri, found) ->
                        bySubject.put(
                                iri,
                                new SubjectSource(
                                        List.copyOf(found), List.copyOf(clauses.get(iri)))));
        return new ShapeSource(text, prefixes, Map.copyOf(bySubject));
    }

    String turtle() {
        return turtle;
    }

    PrefixMapping prefixes() {
        return prefixes;
    }

    /** How the document writes this subject, or {@code null} when it does not write it at all. */
    SubjectSource forSubject(String iri) {
        return iri == null ? null : bySubject.get(iri);
    }

    /**
     * The clauses grouped by the predicate they state, in the order the document states them.
     *
     * <p>Grouped rather than mapped one to one because a subject may state the same predicate in
     * two clauses, and that is precisely what decides whether the form's one field for it can be
     * written back.
     */
    Map<String, List<ClauseLocator.Clause>> byPredicate(List<ClauseLocator.Clause> clauses) {
        var grouped = new LinkedHashMap<String, List<ClauseLocator.Clause>>();
        for (ClauseLocator.Clause clause : clauses) {
            var predicate = predicateIri(clause);
            if (predicate != null) {
                grouped.computeIfAbsent(predicate, ignored -> new ArrayList<>()).add(clause);
            }
        }
        return grouped;
    }

    /** The predicate a clause states, as an IRI. {@code null} when no prefix binds its token. */
    String predicateIri(ClauseLocator.Clause clause) {
        return predicateIriOf(clause, prefixes);
    }

    /**
     * The same, for a caller holding clauses without the index they came from.
     *
     * <p>The writer is one: it rescans the statement it is about to change after every change it
     * makes, rather than carrying spans that the previous change may have moved.
     */
    static String predicateIriOf(ClauseLocator.Clause clause, PrefixMapping prefixes) {
        var token = clause.predicateToken();
        return "a".equals(token) ? RDF.type.getURI() : ShapeBlockLocator.expand(token, prefixes);
    }

    /** A clause's object list, exactly as the document writes it. */
    String objectsAsWritten(ClauseLocator.Clause clause) {
        return turtle.substring(clause.objectsStart(), clause.objectsEnd());
    }

    /** The IRI an object stands for, or {@code null} when the object is not a term. */
    String objectIri(ClauseLocator.ObjectSpan object) {
        var token = turtle.substring(object.start(), object.end());
        return ShapeBlockLocator.expand(token, prefixes);
    }
}
