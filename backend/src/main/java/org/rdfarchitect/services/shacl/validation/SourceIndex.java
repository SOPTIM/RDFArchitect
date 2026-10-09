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
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * One document's text, prepared for locating many terms in it.
 *
 * <p>Gives exactly the positions CIMVocabCheck's {@code SourceLocator.locateWithHint} gives — same
 * search forms, same comment rule, same choice among several occurrences — but that method rescans
 * the whole text per finding, and counts lines by walking from the start of the text for every pair
 * of candidate occurrences it compares. On a 1 MB NC constraints file that made resolving 384
 * findings take three seconds against 40 ms for the validation itself. Here every occurrence list
 * is found once and kept, and a line is found by binary search in a table of line starts.
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

    private final String text;

    private final Map<String, String> prefixes;

    /** Offset of the first character of each line, ascending; line {@code i + 1} starts at [i]. */
    private final int[] lineStarts;

    private final Map<String, List<Integer>> fullIris = new ConcurrentHashMap<>();

    private final Map<String, List<Integer>> tokens = new ConcurrentHashMap<>();

    private volatile List<Integer> typeKeywords;

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
        out.addAll(fullIri(iri));
        for (var entry : prefixes.entrySet()) {
            var namespace = entry.getValue();
            if (namespace == null || namespace.isEmpty() || !iri.startsWith(namespace)) {
                continue;
            }
            var local = iri.substring(namespace.length());
            if (local.isEmpty() || local.charAt(0) == '/' || local.charAt(0) == '#') {
                continue;
            }
            out.addAll(token(entry.getKey() + ":" + local));
        }
    }

    private List<Integer> fullIri(String iri) {
        return fullIris.computeIfAbsent(
                iri,
                key -> {
                    var needle = "<" + key + ">";
                    var found = new ArrayList<Integer>();
                    for (int at = text.indexOf(needle);
                            at >= 0;
                            at = text.indexOf(needle, at + 1)) {
                        if (!isInComment(at)) {
                            found.add(at);
                        }
                    }
                    return found;
                });
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
                        if (startsToken && endsToken && !isInComment(at)) {
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
                if (isInComment(start)) {
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
     * Whether {@code index} falls inside a {@code #} line comment, tracking strings and {@code <…>}
     * IRIs from the start of its line so a {@code #} inside either does not count.
     */
    private boolean isInComment(int index) {
        int lineStart = lineStarts[lineOf(index) - 1];
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inIri = false;
        for (int i = lineStart; i < index; i++) {
            char c = text.charAt(i);
            if (inIri) {
                if (c == '>') {
                    inIri = false;
                }
            } else if (c == '\\' && (inSingle || inDouble)) {
                i++;
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '<' && !inSingle && !inDouble) {
                inIri = true;
            } else if (c == '#' && !inSingle && !inDouble) {
                return true;
            }
        }
        return false;
    }
}
