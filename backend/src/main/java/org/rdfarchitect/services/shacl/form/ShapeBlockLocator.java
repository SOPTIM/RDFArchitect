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

import org.apache.jena.irix.IRIs;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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
        return scan(turtle).stream()
                .filter(statement -> !isDirective(statement.subjectToken()))
                .toList();
    }

    /** Every top-level statement in reading order, directives included. */
    private static List<Statement> scan(String turtle) {
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
            statements.add(new Statement(subject, start, end));
            index = end;
        }
        return statements;
    }

    /**
     * The prefixes in effect at {@code offset}: those declared in front of it, as last declared.
     *
     * <p>What text inserted at that offset may use. The graph's prefix mapping is the document's
     * final one, and a name abbreviated with a prefix declared further down does not parse — Turtle
     * binds a prefix from its declaration onwards, not for the whole document.
     */
    static PrefixMapping prefixesBefore(String turtle, int offset) {
        var mapping = new PrefixMappingImpl();
        String base = null;
        for (Statement directive : scan(turtle)) {
            if (directive.start() >= offset) {
                break;
            }
            var token = directive.subjectToken().toLowerCase(Locale.ROOT);
            if (!isDirective(token)) {
                continue;
            }
            var text = turtle.substring(directive.start(), directive.end());
            if (token.endsWith("base")) {
                var iri = IRIREF.matcher(text);
                if (iri.find()) {
                    base = resolve(base, iri.group(1));
                }
                continue;
            }
            var declared = PREFIX.matcher(text);
            if (declared.find()) {
                var namespace = resolve(base, declared.group(2));
                if (namespace != null) {
                    mapping.setNsPrefix(declared.group(1), namespace);
                }
            }
        }
        return mapping;
    }

    /**
     * The document's prefixes, able to resolve the relative IRIs it writes as well.
     *
     * <p>Only for a document that declares one base: with several, which one a relative IRI means
     * depends on where it is written, and a name the form cannot be sure of is better left
     * unresolved — its subject is then reported as one the form cannot write — than guessed.
     */
    static PrefixMapping documentPrefixes(String turtle, PrefixMapping prefixes) {
        var bases = new LinkedHashSet<String>();
        for (Statement directive : scan(turtle)) {
            var token = directive.subjectToken().toLowerCase(Locale.ROOT);
            if (isDirective(token) && token.endsWith("base")) {
                var iri = IRIREF.matcher(turtle.substring(directive.start(), directive.end()));
                if (iri.find()) {
                    bases.add(iri.group(1));
                }
            }
        }
        if (bases.size() != 1) {
            return prefixes;
        }
        var base = resolve(null, bases.iterator().next());
        if (base == null || !isAbsolute(base)) {
            return prefixes;
        }
        var based = new Based(base);
        based.setNsPrefixes(prefixes);
        return based;
    }

    /** A prefix mapping that also knows the base the document resolves relative IRIs against. */
    private static final class Based extends PrefixMappingImpl {
        private final String base;

        private Based(String base) {
            this.base = base;
        }
    }

    private static final Pattern IRIREF = Pattern.compile("<([^>]*)>");

    private static final Pattern PREFIX = Pattern.compile("(?i)prefix\\s+([^\\s:]*):\\s*<([^>]*)>");

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
            if (c == '\\') {
                index += 2;
                continue;
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

    /** The IRI a subject or predicate token stands for, or {@code null} when none does. */
    static String expand(String token, PrefixMapping prefixes) {
        if (token.startsWith("<") && token.endsWith(">")) {
            var iri = unescapeIri(token.substring(1, token.length() - 1));
            if (!isAbsolute(iri) && prefixes instanceof Based based) {
                return resolve(based.base, iri);
            }
            return iri;
        }
        int colon = token.indexOf(':');
        if (colon < 0 || token.startsWith("_:")) {
            return null;
        }
        var namespace = prefixes.getNsPrefixURI(token.substring(0, colon));
        return namespace == null ? null : namespace + unescapeLocal(token.substring(colon + 1));
    }

    private static boolean isAbsolute(String iri) {
        return iri.matches("[A-Za-z][A-Za-z0-9+.\\-]*:.*");
    }

    /** {@code iri} resolved against {@code base}; {@code null} when it cannot be. */
    private static String resolve(String base, String iri) {
        var unescaped = unescapeIri(iri);
        if (base == null) {
            return unescaped;
        }
        try {
            return IRIs.resolve(base, unescaped);
        } catch (RuntimeException e) {
            return null;
        }
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
