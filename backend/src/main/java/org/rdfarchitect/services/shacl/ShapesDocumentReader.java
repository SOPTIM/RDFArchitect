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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.exception.database.InvalidContentException;
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the text a shapes document is to be saved with. Creating and editing a document and
 * importing a workspace file all go through here, so that whatever is stored was read the same way.
 */
public final class ShapesDocumentReader {

    private ShapesDocumentReader() {}

    /**
     * Parses content the client sent, reporting a syntax error as one.
     *
     * <p>Jena's own message carries the line and column, which is the only part of the answer the
     * user can do anything with, so it is passed through rather than replaced. An IRI Jena only
     * warns about but no store can be sent is refused too, see {@link UnwritableIris}.
     *
     * @param content the text, in {@code lang}
     * @param lang its syntax
     * @return the shapes with their blank nodes normalised and the prefixes the text declares
     * @throws InvalidContentException when the text cannot be read, or cannot be saved
     */
    public static Model read(String content, Lang lang) {
        var model = ModelFactory.createDefaultModel();
        var warnings = new ArrayList<UnwritableIris.Warning>();
        // Only RDF/XML needs this, which allows nothing before its declaration. Trimming anything
        // else would move the line and column a syntax error reports.
        var text = content == null ? "" : content;
        try {
            RDFParser.fromString(Lang.RDFXML.equals(lang) ? text.trim() : text, lang)
                    .base(ShapesDocument.BASE_IRI)
                    .errorHandler(recordingWarnings(warnings))
                    .parse(model);
        } catch (RiotException e) {
            throw new InvalidContentException(
                    "The constraints could not be read as %s: %s"
                            .formatted(lang.getLabel(), e.getMessage()));
        } catch (StackOverflowError e) {
            // Jena parses brackets recursively, so this says something about the text and not about
            // the server.
            throw new InvalidContentException(ShapesTurtleParser.TOO_DEEP);
        }
        var unwritable = UnwritableIris.in(model.getGraph(), warnings);
        if (!unwritable.isEmpty()) {
            var more = unwritable.size() - 1;
            throw new InvalidContentException(
                    "The constraints cannot be saved: "
                            + unwritable.getFirst().describeWithPlace()
                            + (more > 0
                                    ? " %d more IRI%s cannot be written either."
                                            .formatted(more, more == 1 ? "" : "s")
                                    : ""));
        }
        return ModelFactory.createModelForGraph(GraphUtils.normalizeBlankNodes(model.getGraph()));
    }

    /**
     * Fails on what the parser calls an error, without logging: this is the client's text and goes
     * back to it as a 400, so it isn't an error of this server's for Jena to log as one. Warnings
     * are kept, as they are what places an IRI that cannot be written.
     */
    private static ErrorHandler recordingWarnings(List<UnwritableIris.Warning> warnings) {
        var failing = ErrorHandlerFactory.errorHandlerNoLogging;
        return new ErrorHandler() {
            @Override
            public void warning(String message, long line, long col) {
                warnings.add(new UnwritableIris.Warning(message, line, col));
            }

            @Override
            public void error(String message, long line, long col) {
                failing.error(message, line, col);
            }

            @Override
            public void fatal(String message, long line, long col) {
                failing.fatal(message, line, col);
            }
        };
    }
}
