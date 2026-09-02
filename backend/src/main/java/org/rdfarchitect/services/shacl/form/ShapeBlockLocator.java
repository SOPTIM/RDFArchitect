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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the span of text a single Turtle statement occupies.
 *
 * <p>This is what lets the form editor write back into a document without reformatting it. A
 * document's verbatim text is the source of truth — official ENTSO-E files carry comments and a
 * deliberate ordering people expect back unchanged — so a form edit replaces one statement's
 * characters and leaves every other byte alone, rather than re-serialising the graph.
 *
 * <p>Splitting statements is done by scanning rather than parsing because the position of the
 * source text is exactly what a parse throws away. The scan tracks the three things that can hide a
 * full stop: comments, string literals (including the triple-quoted ones that embedded SPARQL lives
 * in), and bracket nesting.
 */
public final class ShapeBlockLocator {

    /** One top-level statement: its subject as written, and the text it covers. */
    public record Statement(String subjectToken, int start, int end) {}

    private ShapeBlockLocator() {}

    /**
     * The statement whose subject is {@code iri}, resolved through the document's prefixes.
     *
     * <p>Empty when the shape is not written as its own top-level statement — nested in another
     * shape, say. The caller then has nothing to replace surgically and must fall back.
     */
    public static Optional<Statement> locate(String turtle, String iri, PrefixMapping prefixes) {
        return locateAll(turtle, iri, prefixes).stream().findFirst();
    }

    /**
     * Every top-level statement whose subject is {@code iri}, in reading order.
     *
     * <p>Turtle lets one subject be written as many statements, and the form reads all of a
     * subject's triples whichever statement they came from. A writer that replaced only the first
     * of them — which is what asking for one statement invites — would rewrite the whole shape into
     * that statement and leave the others standing: the rules in them came back a second time, and
     * grew by one on every further edit. Callers that rewrite a shape need to see all of them.
     */
    public static List<Statement> locateAll(String turtle, String iri, PrefixMapping prefixes) {
        return statements(turtle).stream()
                .filter(statement -> iri.equals(expand(statement.subjectToken(), prefixes)))
                .toList();
    }

    /**
     * The 1-based line each named subject's statement starts on.
     *
     * <p>One scan for the whole document, for callers that need the line of many subjects rather
     * than of one. Asking {@link #locate} per subject rescans the text from the start every time,
     * which on an official constraints file with thousands of subjects is quadratic in its length.
     *
     * <p>The first statement wins where a subject is written more than once, matching {@code
     * locate}: it is where a reader would start looking.
     */
    public static Map<String, Integer> linesBySubject(String turtle, PrefixMapping prefixes) {
        if (turtle == null || turtle.isEmpty()) {
            return Map.of();
        }
        var lines = new HashMap<String, Integer>();
        // Newlines are counted once from the front rather than per statement: the statements come
        // back in reading order, so the cursor only ever moves forward.
        int counted = 0;
        int line = 1;
        for (Statement statement : statements(turtle)) {
            while (counted < statement.start()) {
                if (turtle.charAt(counted) == '\n') {
                    line++;
                }
                counted++;
            }
            var iri = expand(statement.subjectToken(), prefixes);
            if (iri != null) {
                lines.putIfAbsent(iri, line);
            }
        }
        return Map.copyOf(lines);
    }

    /** Every top-level statement in reading order, directives excluded. */
    static List<Statement> statements(String turtle) {
        var statements = new ArrayList<Statement>();
        var text = turtle == null ? "" : turtle;
        int index = 0;
        while (index < text.length()) {
            index = skipIgnorable(text, index);
            if (index >= text.length()) {
                break;
            }
            int start = index;
            var subject = tokenAt(text, start);
            int end = endOfStatement(text, start, subject);
            if (!isDirective(subject)) {
                statements.add(new Statement(subject, start, end));
            }
            index = end;
        }
        return statements;
    }

    /**
     * Replaces a statement's text, keeping the newline that followed it.
     *
     * <p>The replacement is expected to end with the statement's own {@code .} but not a line
     * break, so that the document's existing spacing between statements is what survives.
     */
    static String replace(String turtle, Statement statement, String replacement) {
        return turtle.substring(0, statement.start())
                + replacement
                + turtle.substring(statement.end());
    }

    // -------------------------------------------------------------------------
    // Scanning
    // -------------------------------------------------------------------------

    /**
     * Whether {@code turtle} holds a comment.
     *
     * <p>Scanned rather than searched for {@code #}: the character is ordinary inside an absolute
     * IRI — every CIM term ends {@code …#ACLineSegment} — and inside a literal, so looking for it
     * alone calls almost every shape commented.
     */
    static boolean containsComment(String turtle) {
        int index = 0;
        while (index < turtle.length()) {
            char c = turtle.charAt(index);
            if (c == '#') {
                return true;
            }
            if (c == '"' || c == '\'') {
                index = endOfLiteral(turtle, index);
                continue;
            }
            if (c == '<') {
                int close = turtle.indexOf('>', index);
                index = close < 0 ? turtle.length() : close + 1;
                continue;
            }
            index++;
        }
        return false;
    }

    private static int skipIgnorable(String text, int from) {
        int index = from;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
            } else if (c == '#') {
                while (index < text.length() && text.charAt(index) != '\n') {
                    index++;
                }
            } else {
                return index;
            }
        }
        return index;
    }

    private static String tokenAt(String text, int from) {
        int end = from;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            if (text.charAt(end) == '<') {
                int close = text.indexOf('>', end);
                return close < 0 ? text.substring(from) : text.substring(from, close + 1);
            }
            end++;
        }
        return text.substring(from, end);
    }

    private static boolean isDirective(String token) {
        var lower = token.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("@prefix")
                || lower.equals("@base")
                || lower.equals("prefix")
                || lower.equals("base");
    }

    /**
     * The index just past the statement starting at {@code start}.
     *
     * <p>SPARQL-style {@code PREFIX} and {@code BASE} are terminated by their IRI rather than by a
     * full stop, which is why the directive's shape has to be known here and not only skipped.
     */
    private static int endOfStatement(String text, int start, String subject) {
        if (subject.equalsIgnoreCase("PREFIX") || subject.equalsIgnoreCase("BASE")) {
            int close = text.indexOf('>', start);
            return close < 0 ? text.length() : close + 1;
        }
        int depth = 0;
        int index = start;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == '#') {
                while (index < text.length() && text.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                index = endOfLiteral(text, index);
                continue;
            }
            if (c == '<') {
                int close = text.indexOf('>', index);
                index = close < 0 ? text.length() : close + 1;
                continue;
            }
            if (c == '[' || c == '(') {
                depth++;
            } else if (c == ']' || c == ')') {
                depth--;
            } else if (c == '.' && depth <= 0 && terminates(text, index)) {
                return index + 1;
            }
            index++;
        }
        return text.length();
    }

    /**
     * Whether a {@code .} ends the statement rather than sitting inside a name.
     *
     * <p>{@code cim:ACLineSegment.length} is one prefixed name, so a full stop only terminates when
     * whitespace, a comment or the end of the document follows it.
     */
    private static boolean terminates(String text, int index) {
        if (index + 1 >= text.length()) {
            return true;
        }
        char next = text.charAt(index + 1);
        return Character.isWhitespace(next) || next == '#';
    }

    /** Just past the literal starting at {@code start}. Shared with {@link ClauseLocator}. */
    static int endOfLiteral(String text, int start) {
        char quote = text.charAt(start);
        var triple = "" + quote + quote + quote;
        if (text.startsWith(triple, start)) {
            int close = text.indexOf(triple, start + 3);
            return close < 0 ? text.length() : close + 3;
        }
        int index = start + 1;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == '\\') {
                index += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return index + 1;
            }
            index++;
        }
        return text.length();
    }

    /** The IRI a subject or predicate token stands for, or {@code null} when none does. */
    static String expand(String token, PrefixMapping prefixes) {
        if (token.startsWith("<") && token.endsWith(">")) {
            return token.substring(1, token.length() - 1);
        }
        var expanded = prefixes.expandPrefix(token);
        return expanded.equals(token) ? null : expanded;
    }
}
