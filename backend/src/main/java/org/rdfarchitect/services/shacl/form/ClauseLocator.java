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

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the span of text each predicate-object clause occupies.
 *
 * <p>{@link ShapeBlockLocator} splits a document into statements; this splits a statement one level
 * further, into the {@code predicate object} clauses between its semicolons, and recurses into the
 * blank nodes {@code sh:property [ … ]} writes its rules as.
 *
 * <p>That is what lets a form edit change one clause and leave the rest of the shape as its author
 * wrote it — including the clauses the form has no field for. Before this, a shape was rewritten
 * whole from the model, so a single {@code sh:sparql} or a {@code sh:order 0.1} the form could not
 * reproduce had to make the entire shape read-only. Here it is simply a clause nobody touches.
 *
 * <p>Scanned rather than parsed for the same reason as statements are: the position of the source
 * text is exactly what a parse throws away. The scan tracks the three things that can hide a
 * separator — comments, string literals including the triple-quoted ones embedded SPARQL lives in,
 * and bracket nesting.
 */
public final class ClauseLocator {

    /**
     * One object of a clause, and the clauses inside it when it is a {@code [ … ]} blank node.
     *
     * <p>A clause may state several objects — {@code sh:targetClass cim:A , cim:B} is one clause
     * with two — so the objects are kept apart rather than treated as one blob of text.
     */
    public record ObjectSpan(int start, int end, List<Clause> nested) {}

    /** One {@code predicate objectList} clause: where it starts, where it ends, what it says. */
    public record Clause(String predicateToken, int start, int end, List<ObjectSpan> objects) {

        /** The first character of the clause's object list. */
        public int objectsStart() {
            return objects.get(0).start();
        }

        /** Just past the last character of the clause's object list. */
        public int objectsEnd() {
            return objects.get(objects.size() - 1).end();
        }
    }

    private ClauseLocator() {}

    /**
     * The clauses of one top-level statement, in reading order.
     *
     * <p>The statement's own terminating {@code .} is left out of the last clause, so a caller may
     * insert a clause after it without stepping on the full stop.
     */
    public static List<Clause> of(String turtle, ShapeBlockLocator.Statement statement) {
        int from = statement.start() + statement.subjectToken().length();
        int to = statement.end();
        if (to > from && turtle.charAt(to - 1) == '.') {
            to--;
        }
        return clauses(turtle, from, to);
    }

    /** The clauses written between {@code from} (inclusive) and {@code to} (exclusive). */
    static List<Clause> clauses(String text, int from, int to) {
        var clauses = new ArrayList<Clause>();
        int index = Math.max(0, from);
        int limit = Math.min(text.length(), to);
        while (index < limit) {
            index = skipIgnorable(text, index, limit);
            if (index >= limit) {
                break;
            }
            char c = text.charAt(index);
            if (c == ';' || c == ',' || c == '.') {
                // A separator with no clause in front of it: an empty clause, or the trailing `;`
                // many of the official files write before the closing bracket.
                index++;
                continue;
            }
            int predicateEnd = endOfTerm(text, index, limit);
            if (predicateEnd == index) {
                // Nothing readable here. Stepping on rather than looping is the only safe answer:
                // this runs on documents that parsed, so it means the scan met something it was
                // not taught, and the worst outcome is a clause the form leaves alone.
                index++;
                continue;
            }
            var predicate = text.substring(index, predicateEnd);
            var objects = objectsOf(text, predicateEnd, limit);
            int cursor = objects.isEmpty() ? predicateEnd : objects.get(objects.size() - 1).end();
            if (!objects.isEmpty()) {
                clauses.add(new Clause(predicate, index, cursor, List.copyOf(objects)));
            }
            index = skipIgnorable(text, cursor, limit);
            if (index < limit && text.charAt(index) == ';') {
                index++;
            }
        }
        return List.copyOf(clauses);
    }

    /** The comma-separated objects following a predicate. */
    private static List<ObjectSpan> objectsOf(String text, int from, int limit) {
        var objects = new ArrayList<ObjectSpan>();
        int cursor = from;
        while (true) {
            cursor = skipIgnorable(text, cursor, limit);
            if (cursor >= limit || text.charAt(cursor) == ';') {
                return objects;
            }
            int end = endOfTerm(text, cursor, limit);
            if (end == cursor) {
                return objects;
            }
            objects.add(new ObjectSpan(cursor, end, nestedIn(text, cursor, end)));
            cursor = skipIgnorable(text, end, limit);
            if (cursor >= limit || text.charAt(cursor) != ',') {
                return objects;
            }
            cursor++;
        }
    }

    /** The clauses inside a {@code [ … ]} object, or nothing for any other kind of object. */
    private static List<Clause> nestedIn(String text, int start, int end) {
        return text.charAt(start) == '[' ? clauses(text, start + 1, end - 1) : List.of();
    }

    /**
     * Just past the term starting at {@code from}: an IRI, a literal, a bracketed group, or a name.
     *
     * <p>A bare name runs to the next separator. It deliberately does not stop at a {@code .},
     * because {@code cim:ACLineSegment.length} is one name and {@code 0.1} is one number — the
     * statement's own full stop is outside {@code limit}, which is what ends those.
     */
    private static int endOfTerm(String text, int from, int limit) {
        char c = text.charAt(from);
        if (c == '<') {
            int close = text.indexOf('>', from);
            return close < 0 || close >= limit ? limit : close + 1;
        }
        if (c == '"' || c == '\'') {
            return endOfSuffix(text, ShapeBlockLocator.endOfLiteral(text, from), limit);
        }
        if (c == '[' || c == '(') {
            return endOfGroup(text, from, limit);
        }
        int index = from;
        while (index < limit && !isSeparator(text.charAt(index))) {
            index++;
        }
        return Math.min(index, limit);
    }

    /** A literal's {@code ^^datatype} or {@code @language}, which belong to the same object. */
    private static int endOfSuffix(String text, int from, int limit) {
        if (from + 1 < limit && text.charAt(from) == '^' && text.charAt(from + 1) == '^') {
            return endOfTerm(text, Math.min(from + 2, limit), limit);
        }
        if (from < limit && text.charAt(from) == '@') {
            int index = from + 1;
            while (index < limit
                    && (Character.isLetterOrDigit(text.charAt(index))
                            || text.charAt(index) == '-')) {
                index++;
            }
            return index;
        }
        return Math.min(from, limit);
    }

    /** Just past the {@code ]} or {@code )} closing the group that starts at {@code from}. */
    private static int endOfGroup(String text, int from, int limit) {
        int depth = 0;
        int index = from;
        while (index < limit) {
            char c = text.charAt(index);
            if (c == '#') {
                while (index < limit && text.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                index = ShapeBlockLocator.endOfLiteral(text, index);
                continue;
            }
            if (c == '<') {
                int close = text.indexOf('>', index);
                index = close < 0 || close >= limit ? limit : close + 1;
                continue;
            }
            if (c == '[' || c == '(') {
                depth++;
            } else if (c == ']' || c == ')') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
            index++;
        }
        return limit;
    }

    /** What ends a bare name: a separator, a bracket, or the start of another kind of term. */
    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c)
                || c == ';'
                || c == ','
                || c == ']'
                || c == ')'
                || c == '['
                || c == '('
                || c == '#'
                || c == '<'
                || c == '"'
                || c == '\'';
    }

    /** Past whitespace and comments, which may sit between any two tokens. */
    private static int skipIgnorable(String text, int from, int limit) {
        int index = from;
        while (index < limit) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
            } else if (c == '#') {
                while (index < limit && text.charAt(index) != '\n') {
                    index++;
                }
            } else {
                return index;
            }
        }
        return limit;
    }
}
