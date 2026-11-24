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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds the line each subject of a Turtle document is written on.
 *
 * <p>A parse does not keep positions, so the text is read again with the tokenizer Jena's Turtle
 * parser is built on, which knows each token's line. A subject is the first token after a full stop
 * outside brackets. Directives take effect where they stand, as they do in the parser, so a
 * prefixed name or a relative IRI means here what it means there.
 */
final class TurtleSubjectLines {

    private static final Set<String> DIRECTIVES = Set.of("prefix", "base", "version");

    private TurtleSubjectLines() {}

    /**
     * The 1-based line each named subject's statement starts on.
     *
     * <p>The first statement wins where a subject is written more than once: it is where a reader
     * would start looking. A lexical error ends the scan, as it ends the parse, so a subject after
     * it has no line.
     *
     * @param base what relative IRIs resolve against until the text sets its own {@code @base}; the
     *     base the text was parsed with
     * @param prefixes prefixes bound before the text starts; the text's own directives override
     *     them from where they stand
     */
    static Map<String, Integer> linesBySubject(String turtle, String base, PrefixMapping prefixes) {
        if (turtle == null || turtle.isEmpty()) {
            return Map.of();
        }
        // The checks RDFParser uses for Turtle.
        var profile = RiotLib.profile(Lang.TURTLE, base, ErrorHandlerFactory.errorHandlerNoLogging);
        profile.getPrefixMap().putAll(prefixes);
        // Lines are counted as the editor counts them, where a lone \r ends one too. The tokenizer
        // counts only \n.
        var text = turtle.replace("\r\n", "\n").replace('\r', '\n');
        var tokens =
                TokenizerText.create()
                        .fromString(text)
                        .errorHandler(ErrorHandlerFactory.errorHandlerNoLogging)
                        .build();
        var lines = new HashMap<String, Integer>();
        try {
            int depth = 0;
            boolean atSubject = true;
            while (tokens.hasNext()) {
                var token = tokens.next();
                if (atSubject && isDirective(token)) {
                    readDirective(token.getImage().toLowerCase(Locale.ROOT), tokens, profile);
                    continue;
                }
                if (atSubject
                        && (token.hasType(TokenType.IRI)
                                || token.hasType(TokenType.PREFIXED_NAME))) {
                    var subject = profile.create(null, token);
                    if (subject.isURI()) {
                        lines.putIfAbsent(subject.getURI(), (int) token.getLine());
                    }
                }
                atSubject = false;
                switch (token.getType()) {
                    case LBRACKET, LPAREN, LT2, L_TRIPLE, L_ANN -> depth++;
                    case RBRACKET, RPAREN, GT2, R_TRIPLE, R_ANN -> depth--;
                    case DOT -> atSubject = depth <= 0;
                    default -> {
                        // Anything else neither nests nor ends a statement.
                    }
                }
            }
        } catch (RiotException e) {
            // The parser stops at the same place, so the graph holds nothing after it either.
        }
        return Map.copyOf(lines);
    }

    /** {@code @prefix}, {@code @base}, {@code @version}, or one of them in SPARQL's spelling. */
    private static boolean isDirective(Token token) {
        return token.hasType(TokenType.DIRECTIVE)
                || (token.hasType(TokenType.KEYWORD)
                        && DIRECTIVES.contains(token.getImage().toLowerCase(Locale.ROOT)));
    }

    /** Reads a directive's operands and applies it to {@code profile}, as the parser does. */
    private static void readDirective(String directive, Tokenizer tokens, ParserProfile profile) {
        switch (directive) {
            case "prefix" -> {
                var prefix = expect(tokens, TokenType.PREFIXED_NAME).getImage();
                var iri = expect(tokens, TokenType.IRI);
                profile.getPrefixMap()
                        .add(
                                prefix,
                                profile.resolveIRI(iri.getImage(), iri.getLine(), iri.getColumn()));
            }
            case "base" -> {
                var iri = expect(tokens, TokenType.IRI);
                profile.setBaseIRI(
                        profile.resolveIRI(iri.getImage(), iri.getLine(), iri.getColumn()));
            }
            case "version" -> {
                var version = next(tokens);
                // `VERSION "1.2"` with `@prefix` right after it reads as a literal with a
                // language tag, and the tag is the next directive.
                if (version.hasType(TokenType.LITERAL_LANG)) {
                    readDirective(version.getImage2(), tokens, profile);
                }
            }
            default -> throw new RiotException("Unknown directive @" + directive);
        }
    }

    private static Token expect(Tokenizer tokens, TokenType type) {
        var token = next(tokens);
        if (!token.hasType(type)) {
            throw new RiotException("Expected " + type + " in a directive, found " + token);
        }
        return token;
    }

    private static Token next(Tokenizer tokens) {
        if (!tokens.hasNext()) {
            throw new RiotException("A directive ends with the text");
        }
        return tokens.next();
    }
}
