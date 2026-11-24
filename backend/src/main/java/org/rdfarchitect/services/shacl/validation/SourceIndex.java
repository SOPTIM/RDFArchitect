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

package org.rdfarchitect.services.shacl.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.irix.IRIs;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.riot.system.ParserProfile;
import org.apache.jena.riot.system.RiotLib;
import org.apache.jena.riot.tokens.Token;
import org.apache.jena.riot.tokens.TokenType;
import org.apache.jena.riot.tokens.Tokenizer;
import org.apache.jena.riot.tokens.TokenizerText;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.database.ShapesDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * One document's text, prepared for locating many terms in it.
 *
 * <p>Gives the positions CIMVocabCheck's {@code SourceLocator.locateWithHint} gives — same search
 * forms, same comment rule, same choice among several occurrences — but that method rescans the
 * whole text per finding, and counts lines by walking from the start of the text for every pair of
 * candidate occurrences it compares, which on a large NC constraints file takes far longer than the
 * validation itself. Here every occurrence list is found once and kept, and a line is found by
 * binary search in a table of line starts.
 *
 * <p>It reads the text more closely than that locator in three ways. A prefixed name means what its
 * prefix is bound to where the name stands, so a prefix bound twice is read both ways. An IRI in
 * angle brackets is resolved against the base in force where it stands — {@link
 * ShapesDocument#BASE_IRI}, the base every document is parsed with, unless the text sets one. And a
 * term mentioned in a one-line string, such as an {@code sh:message}, is not taken for its use.
 * Long strings are searched, because the SPARQL of an {@code sh:select} is written in them.
 *
 * <p>Safe to share: the occurrence lists are kept in concurrent maps, and computing one twice gives
 * the same list.
 */
final class SourceIndex {

    /** A 1-based position. */
    record Location(int line, int column) {}

    private static final String RDF_TYPE_URI = RDF.type.getURI();

    /** {@code a} token followed by whitespace and a predicate-position start char. */
    private static final Pattern A_KEYWORD = Pattern.compile("a\\s+[<\\w?$\\[(]");

    /** A scheme, which makes an IRI absolute. */
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

    /** What an IRI in angle brackets cannot hold, besides whitespace. */
    private static final String NOT_IN_IRI = "<\"{}|^`\\";

    private static final Set<String> DIRECTIVES = Set.of("prefix", "base", "version");

    /**
     * A prefix binding or a base, from where it is written on. {@code prefix} is null for a base.
     */
    private record Directive(int offset, String prefix, String iri) {}

    private final String text;

    /** What the parsed graph binds, for a prefix the text binds nowhere. */
    private final Map<String, String> prefixes;

    /** Offset of the first character of each line, ascending; line {@code i + 1} starts at [i]. */
    private final int[] lineStarts;

    private final Map<String, List<Integer>> tokens = new ConcurrentHashMap<>();

    private volatile List<Integer> typeKeywords;

    /** The text's directives in reading order. */
    private volatile List<Directive> directives;

    /** Every IRI written in angle brackets, resolved, with where it is written. */
    private volatile Map<String, List<Integer>> iris;

    SourceIndex(String text, PrefixMapping prefixes) {
        this.text = text;
        this.prefixes = prefixes == null ? Map.of() : prefixes.getNsPrefixMap();
        this.lineStarts = lineStarts(text);
    }

    /** Where {@code term} is written, nearest {@code hint} when it is written more than once. */
    Location locate(Node term, Node hint) {
        if (term == null || !term.isURI()) {
            return null;
        }
        var termOffsets = termOffsets(term);
        if (termOffsets.isEmpty()) {
            return null;
        }
        if (termOffsets.size() == 1 || hint == null) {
            return toLocation(termOffsets.get(0));
        }
        var hintOffsets = nodeOffsets(hint);
        if (hintOffsets.isEmpty()) {
            return toLocation(termOffsets.get(0));
        }

        // Same line as a hint occurrence, hints in order, first matching term occurrence.
        for (int hintOffset : hintOffsets) {
            int hintLine = lineOf(hintOffset);
            for (int termOffset : termOffsets) {
                if (lineOf(termOffset) == hintLine) {
                    return toLocation(termOffset);
                }
            }
        }

        // Nearest occurrence following a hint occurrence.
        int bestForward = -1;
        int bestForwardDistance = Integer.MAX_VALUE;
        for (int hintOffset : hintOffsets) {
            for (int termOffset : termOffsets) {
                if (termOffset >= hintOffset && termOffset - hintOffset < bestForwardDistance) {
                    bestForwardDistance = termOffset - hintOffset;
                    bestForward = termOffset;
                }
            }
        }
        if (bestForward >= 0) {
            return toLocation(bestForward);
        }

        // Nearest in either direction.
        int best = termOffsets.get(0);
        int bestDistance = minDistance(best, hintOffsets);
        for (int offset : termOffsets) {
            int distance = minDistance(offset, hintOffsets);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = offset;
            }
        }
        return toLocation(best);
    }

    /** The 1-based position of a character offset. */
    Location toLocation(int offset) {
        int line = lineOf(offset);
        return new Location(line, offset - lineStarts[line - 1] + 1);
    }

    // -------------------------------------------------------------------------
    // Occurrences
    // -------------------------------------------------------------------------

    private List<Integer> termOffsets(Node term) {
        var offsets = new ArrayList<Integer>();
        iriOffsets(term.getURI(), offsets);
        if (RDF_TYPE_URI.equals(term.getURI())) {
            offsets.addAll(typeKeywords());
        }
        offsets.sort(Integer::compare);
        return offsets;
    }

    private List<Integer> nodeOffsets(Node hint) {
        var offsets = new ArrayList<Integer>();
        if (hint.isVariable()) {
            offsets.addAll(token("?" + hint.getName()));
            offsets.addAll(token("$" + hint.getName()));
        } else if (hint.isURI()) {
            iriOffsets(hint.getURI(), offsets);
        }
        offsets.sort(Integer::compare);
        return offsets;
    }

    private void iriOffsets(String iri, List<Integer> out) {
        out.addAll(iris().getOrDefault(iri, List.of()));
        for (var binding : bindings().entrySet()) {
            var prefix = binding.getKey();
            for (var namespace : binding.getValue()) {
                if (namespace == null || namespace.isEmpty() || !iri.startsWith(namespace)) {
                    continue;
                }
                var local = iri.substring(namespace.length());
                if (local.isEmpty() || local.charAt(0) == '/' || local.charAt(0) == '#') {
                    continue;
                }
                for (int offset : token(prefix + ":" + local)) {
                    if (namespace.equals(namespaceInForce(prefix, offset))) {
                        out.add(offset);
                    }
                }
            }
        }
    }

    /** Every namespace each prefix is bound to, anywhere in the text or by the parsed graph. */
    private Map<String, Set<String>> bindings() {
        var bindings = new HashMap<String, Set<String>>();
        prefixes.forEach(
                (prefix, namespace) ->
                        bindings.computeIfAbsent(prefix, key -> new LinkedHashSet<>())
                                .add(namespace));
        for (var directive : directives()) {
            if (directive.prefix() != null) {
                bindings.computeIfAbsent(directive.prefix(), key -> new LinkedHashSet<>())
                        .add(directive.iri());
            }
        }
        return bindings;
    }

    /**
     * What {@code prefix} is bound to at {@code offset}: by the last directive before it, nothing
     * when the text binds it only further on, and what the parsed graph binds it to when the text
     * binds it nowhere.
     */
    private String namespaceInForce(String prefix, int offset) {
        boolean bindsIt = false;
        String bound = null;
        for (var directive : directives()) {
            if (!prefix.equals(directive.prefix())) {
                continue;
            }
            bindsIt = true;
            if (directive.offset() < offset) {
                bound = directive.iri();
            }
        }
        return bindsIt ? bound : prefixes.get(prefix);
    }

    private String baseInForce(int offset) {
        var base = ShapesDocument.BASE_IRI;
        for (var directive : directives()) {
            if (directive.offset() >= offset) {
                break;
            }
            if (directive.prefix() == null) {
                base = directive.iri();
            }
        }
        return base;
    }

    private Map<String, List<Integer>> iris() {
        var found = iris;
        if (found == null) {
            found = new HashMap<>();
            for (int at = text.indexOf('<'); at >= 0; at = text.indexOf('<', at + 1)) {
                int close = iriEnd(at);
                if (close < 0 || isHidden(at)) {
                    continue;
                }
                var written = text.substring(at + 1, close);
                var iri = SCHEME.matcher(written).find() ? written : resolve(written, at);
                if (iri != null) {
                    found.computeIfAbsent(iri, key -> new ArrayList<>()).add(at);
                }
            }
            iris = found;
        }
        return found;
    }

    /** Where the IRI opened at {@code open} closes, or -1 when what follows cannot be an IRI. */
    private int iriEnd(int open) {
        for (int i = open + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '>') {
                return i;
            }
            if (Character.isWhitespace(c) || NOT_IN_IRI.indexOf(c) >= 0) {
                return -1;
            }
        }
        return -1;
    }

    private String resolve(String relative, int offset) {
        try {
            return IRIs.resolve(baseInForce(offset), relative);
        } catch (RuntimeException notResolvable) {
            return null;
        }
    }

    private List<Integer> token(String token) {
        return tokens.computeIfAbsent(
                token,
                key -> {
                    var found = new ArrayList<Integer>();
                    for (int at = text.indexOf(key); at >= 0; at = text.indexOf(key, at + 1)) {
                        boolean startsToken = at == 0 || !isNameChar(text.charAt(at - 1));
                        int after = at + key.length();
                        boolean endsToken =
                                after >= text.length() || !isNameChar(text.charAt(after));
                        if (startsToken && endsToken && !isHidden(at)) {
                            found.add(at);
                        }
                    }
                    return found;
                });
    }

    private List<Integer> typeKeywords() {
        var found = typeKeywords;
        if (found == null) {
            found = new ArrayList<>();
            var matcher = A_KEYWORD.matcher(text);
            while (matcher.find()) {
                int start = matcher.start();
                if (isHidden(start)) {
                    continue;
                }
                if (start == 0) {
                    found.add(start);
                    continue;
                }
                char previous = text.charAt(start - 1);
                if (Character.isWhitespace(previous) || previous == '.' || previous == ';') {
                    found.add(start);
                }
            }
            found = List.copyOf(found);
            typeKeywords = found;
        }
        return found;
    }

    // -------------------------------------------------------------------------
    // Text
    // -------------------------------------------------------------------------

    private static int[] lineStarts(String text) {
        var starts = new int[16];
        int count = 0;
        starts[count++] = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (count == starts.length) {
                    starts = Arrays.copyOf(starts, count * 2);
                }
                starts[count++] = i + 1;
            }
        }
        return Arrays.copyOf(starts, count);
    }

    /** The 1-based line holding {@code offset}. */
    private int lineOf(int offset) {
        int found = Arrays.binarySearch(lineStarts, offset);
        return found >= 0 ? found + 1 : -found - 1;
    }

    private static int minDistance(int offset, List<Integer> candidates) {
        int min = Integer.MAX_VALUE;
        for (int candidate : candidates) {
            min = Math.min(min, Math.abs(offset - candidate));
        }
        return min;
    }

    /** Characters that may continue a prefixed name, as the locator counts them. */
    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '%';
    }

    /**
     * Whether {@code index} falls inside a {@code #} line comment or a one-line string, tracking
     * strings and {@code <…>} IRIs from the start of its line so a {@code #} inside either does not
     * count. A long string hides nothing: the SPARQL of a query is written in one.
     */
    private boolean isHidden(int index) {
        int lineStart = lineStarts[lineOf(index) - 1];
        char quote = 0;
        boolean longString = false;
        boolean inIri = false;
        for (int i = lineStart; i < index; i++) {
            char c = text.charAt(i);
            if (inIri) {
                if (c == '>') {
                    inIri = false;
                }
            } else if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (longString && text.startsWith(tripled(quote), i)) {
                    quote = 0;
                    i += 2;
                } else if (!longString && c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                longString = text.startsWith(tripled(c), i);
                if (longString) {
                    i += 2;
                }
            } else if (c == '<') {
                inIri = true;
            } else if (c == '#') {
                return true;
            }
        }
        return quote != 0 && !longString;
    }

    private static String tripled(char quote) {
        return String.valueOf(quote).repeat(3);
    }

    // -------------------------------------------------------------------------
    // Directives
    // -------------------------------------------------------------------------

    private List<Directive> directives() {
        var found = directives;
        if (found == null) {
            found = readDirectives();
            directives = found;
        }
        return found;
    }

    /**
     * The prefix and base directives, read with the tokenizer Jena's Turtle parser is built on: a
     * directive inside a string or a comment is none, and a relative namespace resolves as the
     * parser resolves it. A lexical error ends the reading, as it ends the parse.
     */
    private List<Directive> readDirectives() {
        var found = new ArrayList<Directive>();
        var profile =
                RiotLib.profile(
                        Lang.TURTLE,
                        ShapesDocument.BASE_IRI,
                        ErrorHandlerFactory.errorHandlerNoLogging);
        var tokens =
                TokenizerText.create()
                        .fromString(text.replace("\r\n", "\n"))
                        .errorHandler(ErrorHandlerFactory.errorHandlerNoLogging)
                        .build();
        try {
            while (tokens.hasNext()) {
                var token = tokens.next();
                if (isDirective(token)) {
                    readDirective(
                            token.getImage().toLowerCase(Locale.ROOT),
                            offsetOf(token),
                            tokens,
                            profile,
                            found);
                }
            }
        } catch (RiotException unreadable) {
            // The parser stops at the same place, so nothing after it is bound either.
        }
        return List.copyOf(found);
    }

    private static boolean isDirective(Token token) {
        return token.hasType(TokenType.DIRECTIVE)
                || (token.hasType(TokenType.KEYWORD)
                        && DIRECTIVES.contains(token.getImage().toLowerCase(Locale.ROOT)));
    }

    private static void readDirective(
            String directive,
            int offset,
            Tokenizer tokens,
            ParserProfile profile,
            List<Directive> found) {
        switch (directive) {
            case "prefix" -> {
                var prefix = expect(tokens, TokenType.PREFIXED_NAME).getImage();
                var iri = expect(tokens, TokenType.IRI);
                var namespace = profile.resolveIRI(iri.getImage(), iri.getLine(), iri.getColumn());
                profile.getPrefixMap().add(prefix, namespace);
                found.add(new Directive(offset, prefix, namespace));
            }
            case "base" -> {
                var iri = expect(tokens, TokenType.IRI);
                var base = profile.resolveIRI(iri.getImage(), iri.getLine(), iri.getColumn());
                profile.setBaseIRI(base);
                found.add(new Directive(offset, null, base));
            }
            case "version" -> {
                var version = expect(tokens, null);
                // `VERSION "1.2"` with `@prefix` right after it reads as a literal with a language
                // tag, and the tag is the next directive.
                if (version.hasType(TokenType.LITERAL_LANG)) {
                    readDirective(version.getImage2(), offset, tokens, profile, found);
                }
            }
            default -> throw new RiotException("Unknown directive @" + directive);
        }
    }

    private static Token expect(Tokenizer tokens, TokenType type) {
        if (!tokens.hasNext()) {
            throw new RiotException("A directive ends with the text");
        }
        var token = tokens.next();
        if (type != null && !token.hasType(type)) {
            throw new RiotException("Expected " + type + " in a directive, found " + token);
        }
        return token;
    }

    /** The token's offset in the text, from the line and column the tokenizer counts. */
    private int offsetOf(Token token) {
        int line = (int) Math.min(Math.max(token.getLine(), 1), lineStarts.length);
        return lineStarts[line - 1] + (int) Math.max(token.getColumn() - 1, 0);
    }
}
