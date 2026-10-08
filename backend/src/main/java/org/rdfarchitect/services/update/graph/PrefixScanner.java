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

package org.rdfarchitect.services.update.graph;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the namespace prefixes a file declares without building a graph from it. The import has to
 * know every prefix of every file before it stores the first one, and holding all parsed graphs
 * until then would cost far more memory than the prefixes do.
 */
final class PrefixScanner {

    private static final Logger logger = LoggerFactory.getLogger(PrefixScanner.class);

    private PrefixScanner() {}

    /**
     * The prefixes one file of the import declares.
     *
     * @param fileName the file, for a zip entry the name of the entry
     * @param prefixes prefix (without a trailing colon) to namespace URI, in declaration order
     * @param readable whether the file could be parsed at all
     */
    record ScannedFile(String fileName, Map<String, String> prefixes, boolean readable) {}

    /**
     * Scans a single file. The scan parses the whole file, so a file it cannot read is one the
     * import cannot read either.
     */
    static ScannedFile scan(String fileName, MultipartFile file) {
        var collector = new PrefixCollector();
        try (var inputStream = file.getInputStream()) {
            RDFParser.source(inputStream).lang(languageOf(fileName)).parse(collector);
        } catch (IOException | RuntimeException exception) {
            logger.warn("Unable to read '{}': {}", fileName, exception.getMessage());
            return new ScannedFile(fileName, Map.of(), false);
        }
        return new ScannedFile(fileName, collector.prefixes, true);
    }

    /** Mirrors how the import itself picks the language, which defaults to RDF/XML. */
    private static Lang languageOf(String fileName) {
        return Objects.requireNonNullElse(RDFLanguages.filenameToLang(fileName), Lang.RDFXML);
    }

    private static final class PrefixCollector extends StreamRDFBase {

        private final Map<String, String> prefixes = new LinkedHashMap<>();

        /** Lets the last declaration win, as it would when parsing the file into a graph. */
        @Override
        public void prefix(String prefix, String iri) {
            prefixes.put(prefix, iri);
        }
    }
}
