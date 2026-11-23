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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Every named subject of every Turtle file at hand gets a line, and the line starts with that
 * subject: the official ENTSO-E profiles and this repository's own test data.
 */
class TurtleSubjectLinesAgainstTurtleFilesTest {

    private static final Path LIBRARY = Path.of("../external/entsoe-application-profiles-library");
    private static final Path TEST_DATA = Path.of("src/test");

    @Test
    void everyOfficialSubjectIsFoundWhereItsStatementStarts() throws IOException {
        var files = turtleFiles(LIBRARY);

        assertThat(files).isNotEmpty();
        assertThat(mismatches(files)).isEmpty();
    }

    @Test
    void everySubjectOfTheTestDataIsFoundWhereItsStatementStarts() throws IOException {
        var files = turtleFiles(TEST_DATA);

        assertThat(files).isNotEmpty();
        assertThat(mismatches(files)).isEmpty();
    }

    private static List<Path> turtleFiles(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(path -> path.toString().endsWith(".ttl")).sorted().toList();
        }
    }

    private static List<String> mismatches(List<Path> files) throws IOException {
        var mismatches = new ArrayList<String>();
        for (var file : files) {
            var text = Files.readString(file, StandardCharsets.UTF_8);
            var model = ModelFactory.createDefaultModel();
            RDFParser.fromString(text, Lang.TURTLE).parse(model);
            var lines = TurtleSubjectLines.linesBySubject(text, model);

            var subjects = new TreeSet<String>();
            model.listSubjects()
                    .filterKeep(subject -> subject.isURIResource())
                    .forEachRemaining(subject -> subjects.add(subject.getURI()));
            if (!lines.keySet().equals(subjects)) {
                var missing = new TreeSet<>(subjects);
                missing.removeAll(lines.keySet());
                mismatches.add(file + ": no line for " + missing.stream().limit(3).toList());
            }

            var sourceLines = text.lines().toList();
            lines.forEach(
                    (subject, line) -> {
                        var written = sourceLines.get(line - 1).stripLeading();
                        if (!startsWithSpellingOf(written, subject, model)) {
                            mismatches.add(file + ":" + line + " does not start with " + subject);
                        }
                    });
        }
        return mismatches;
    }

    /** Whether {@code line} starts with {@code iri} as a full IRI or as a prefixed name. */
    private static boolean startsWithSpellingOf(String line, String iri, Model model) {
        var spellings = new ArrayList<String>();
        spellings.add("<" + iri + ">");
        model.getNsPrefixMap()
                .forEach(
                        (prefix, namespace) -> {
                            if (iri.startsWith(namespace)) {
                                spellings.add(prefix + ":" + iri.substring(namespace.length()));
                            }
                        });
        return spellings.stream()
                .anyMatch(
                        spelling ->
                                line.startsWith(spelling)
                                        && !continuesName(line, spelling.length()));
    }

    /** Whether the name before {@code index} goes on: {@code ex:A.b} is not {@code ex:A}. */
    private static boolean continuesName(String line, int index) {
        if (index >= line.length()) {
            return false;
        }
        var next = line.charAt(index);
        if (next == '.') {
            return index + 1 < line.length() && isNameCharacter(line.charAt(index + 1));
        }
        return isNameCharacter(next);
    }

    private static boolean isNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '%';
    }
}
