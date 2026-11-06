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

package org.rdfarchitect.services.shacl;

import org.apache.jena.shared.PrefixMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds the line each subject of a Turtle document is written on.
 *
 * <p>Done by scanning rather than parsing because the position in the source text is exactly what a
 * parse throws away. The scan tracks the three things that can hide a full stop: comments, string
 * literals (including the triple-quoted ones that embedded SPARQL lives in), and bracket nesting.
 */
final class TurtleSubjectLines {

    /** One top-level statement: its subject as written, and the text it covers. */
    record Statement(String subjectToken, int start, int end) {}

    private TurtleSubjectLines() {}

    /**
     * The 1-based line each named subject's statement starts on.
     *
     * <p>The first statement wins where a subject is written more than once: it is where a reader
     * would start looking.
     */
    static Map<String, Integer> linesBySubject(String turtle, PrefixMapping prefixes) {
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

    /** The token at {@code from}: an IRI to its {@code >}, anything else to where a name ends. */
    private static String tokenAt(String text, int from) {
        if (from < text.length() && text.charAt(from) == '<') {
            int close = text.indexOf('>', from);
            return close < 0 ? text.substring(from) : text.substring(from, close + 1);
        }
        int end = from;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isWhitespace(c) || c == '<' || c == '#') {
                break;
            }
            // Only an escape can put either of those into a name.
            end += c == '\\' ? 2 : 1;
        }
        return text.substring(from, Math.min(end, text.length()));
    }

    private static boolean isDirective(String token) {
        var lower = token.toLowerCase(Locale.ROOT);
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
            if (c == '\\') {
                // Outside a literal only a local name escapes a character, and `ex:A\.` does not
                // end the statement.
                index += 2;
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
     * <p>{@code cim:ACLineSegment.length} is one prefixed name, and so is {@code ex:a.ex:b}, since
     * a local name may hold a colon. A full stop terminates when what follows can't continue a name
     * or when what comes before isn't one: an IRI, a literal or a closed group.
     */
    private static boolean terminates(String text, int index) {
        if (index + 1 >= text.length()) {
            return true;
        }
        char next = text.charAt(index + 1);
        if (Character.isWhitespace(next)
                || next == '#'
                || next == '<'
                || next == '['
                || next == '(') {
            return true;
        }
        char previous = index > 0 ? text.charAt(index - 1) : ' ';
        return previous == '>'
                || previous == '"'
                || previous == '\''
                || previous == ']'
                || previous == ')';
    }

    /** Just past the literal starting at {@code start}. */
    private static int endOfLiteral(String text, int start) {
        char quote = text.charAt(start);
        var triple = "" + quote + quote + quote;
        if (text.startsWith(triple, start)) {
            int index = start + 3;
            while (index < text.length()) {
                if (text.charAt(index) == '\\') {
                    index += 2;
                } else if (text.startsWith(triple, index)) {
                    // A long string may end in quotes of its own — `"""say "hi""""` — so the
                    // delimiter is the last three of the run.
                    while (index + 3 < text.length() && text.charAt(index + 3) == quote) {
                        index++;
                    }
                    return index + 3;
                } else {
                    index++;
                }
            }
            return text.length();
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

    /** The IRI a subject token stands for, or {@code null} when none does. */
    static String expand(String token, PrefixMapping prefixes) {
        if (token.startsWith("<") && token.endsWith(">")) {
            return unescapeIri(token.substring(1, token.length() - 1));
        }
        int colon = token.indexOf(':');
        if (colon < 0 || token.startsWith("_:")) {
            return null;
        }
        var namespace = prefixes.getNsPrefixURI(token.substring(0, colon));
        return namespace == null ? null : namespace + unescapeLocal(token.substring(colon + 1));
    }

    /** A local name as it is meant: {@code ex:A\.} names {@code A.}. */
    private static String unescapeLocal(String local) {
        if (local.indexOf('\\') < 0) {
            return local;
        }
        var unescaped = new StringBuilder(local.length());
        for (int index = 0; index < local.length(); index++) {
            char c = local.charAt(index);
            if (c == '\\' && index + 1 < local.length()) {
                c = local.charAt(++index);
            }
            unescaped.append(c);
        }
        return unescaped.toString();
    }

    /** An {@code IRIREF} with its numeric escapes, {@code UCHAR} in the grammar, decoded. */
    private static String unescapeIri(String iri) {
        if (iri.indexOf('\\') < 0) {
            return iri;
        }
        var unescaped = new StringBuilder(iri.length());
        int index = 0;
        while (index < iri.length()) {
            char c = iri.charAt(index);
            int digits = c == '\\' && index + 1 < iri.length() ? escapeLength(iri, index + 1) : 0;
            if (digits > 0 && index + 2 + digits <= iri.length()) {
                try {
                    unescaped.appendCodePoint(
                            Integer.parseInt(iri.substring(index + 2, index + 2 + digits), 16));
                    index += 2 + digits;
                    continue;
                } catch (IllegalArgumentException e) {
                    // Not an escape after all, so it is kept as written.
                }
            }
            unescaped.append(c);
            index++;
        }
        return unescaped.toString();
    }

    private static int escapeLength(String iri, int marker) {
        return switch (iri.charAt(marker)) {
            case 'u' -> 4;
            case 'U' -> 8;
            default -> 0;
        };
    }
}
