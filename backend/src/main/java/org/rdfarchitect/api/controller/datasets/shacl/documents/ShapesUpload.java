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

package org.rdfarchitect.api.controller.datasets.shacl.documents;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.InvalidContentException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Reads an uploaded constraints file into the Turtle a shapes document holds.
 *
 * <p>Only syntaxes that are self-contained are accepted. JSON-LD in particular is refused: a remote
 * {@code @context} makes the parser fetch an arbitrary URL from the server.
 */
final class ShapesUpload {

    private static final Set<Lang> ACCEPTED = Set.of(Lang.TURTLE, Lang.RDFXML, Lang.NTRIPLES);

    private static final char BOM = '\uFEFF';

    private ShapesUpload() {}

    /**
     * The syntax of an upload, from its file name and otherwise its declared content type.
     *
     * @throws ResponseStatusException 415 for anything but Turtle, RDF/XML and N-Triples
     */
    static Lang languageOf(String fileName, String contentType) {
        var lang = fromFileName(fileName);
        if (lang == null && contentType != null) {
            lang = RDFLanguages.contentTypeToLang(contentType);
        }
        if (Lang.N3.equals(lang)) {
            // Jena reads .n3 as Turtle, and so does the editor.
            lang = Lang.TURTLE;
        }
        if (lang == null || !ACCEPTED.contains(lang)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Constraints can be imported from Turtle (.ttl), RDF/XML (.rdf, .xml) or "
                            + "N-Triples (.nt); \""
                            + fileName
                            + "\" is none of these.");
        }
        return lang;
    }

    private static Lang fromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        // Not an extension Jena knows, but the conventional one for a Turtle shapes file.
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".shacl")) {
            return Lang.TURTLE;
        }
        return RDFLanguages.filenameToLang(fileName);
    }

    /**
     * The upload as Turtle text.
     *
     * <p>Turtle is kept as written, less a leading byte order mark, since the text is what the
     * editor shows and what is exported again. RDF/XML is parsed from the bytes, so its encoding
     * declaration is honoured, and anything else is converted once here.
     *
     * @throws InvalidContentException when a syntax that has to be UTF-8 is not
     */
    static String toTurtle(byte[] content, Lang lang) {
        if (Lang.TURTLE.equals(lang)) {
            var text = decodeUtf8(content, lang);
            return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
        }
        if (!Lang.RDFXML.equals(lang)) {
            decodeUtf8(content, lang);
        }
        var model = ModelFactory.createDefaultModel();
        try {
            // Without logging: this is the client's file and goes back to it as a 400.
            RDFParser.source(new ByteArrayInputStream(content))
                    .lang(lang)
                    .errorHandler(ErrorHandlerFactory.errorHandlerNoLogging)
                    .parse(model);
        } catch (RiotException e) {
            throw new InvalidContentException(
                    "The constraints could not be read as %s: %s"
                            .formatted(lang.getLabel(), e.getMessage()));
        }
        try (var out = new ByteArrayOutputStream()) {
            model.write(out, Lang.TURTLE.getName());
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Error while writing constraints as Turtle", e);
        }
    }

    /**
     * Refuses bytes that aren't UTF-8, as a lenient decode would turn each into U+FFFD. The result
     * still parses, so the file would import with its text quietly damaged.
     */
    private static String decodeUtf8(byte[] content, Lang lang) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidContentException(
                    "%s has to be UTF-8, and this file is not. Save it as UTF-8 and import it again."
                            .formatted(lang.getLabel()));
        }
    }
}
