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

import static org.assertj.core.api.Assertions.assertThat;

import de.soptim.opencgmes.cimvocabcheck.core.SourceLocator;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The index has to find exactly what CIMVocabCheck's locator finds, only faster.
 *
 * <p>Compared on real constraints files rather than on crafted text, because what matters is that
 * no finding moves: every schema term the file uses is looked up with its subject as the hint.
 */
class SourceIndexTest {

    private static final String LIBRARY = "../external/entsoe-application-profiles-library/";

    /**
     * Enough lookups per file to cover every form; the reference implementation is the slow one.
     */
    private static final int LOOKUPS = 150;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CGMES/CurrentRelease/SHACL/TTL/61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl",
                "CGMES/PastReleases/v2-4/Enchanced/SHACL/StateVariablesProfile.ttl",
                "NCP/CurrentRelease/SHACL/SecurityAnalysisResult-AP-Con-Complex-SHACL.ttl"
            })
    void findsWhatTheLocatorFinds(String file) throws IOException {
        var text = Files.readString(Path.of(LIBRARY + file));
        assertSamePositions(text);
        // Windows line endings, which the official files do not use but uploads may.
        assertSamePositions(text.replace("\r\n", "\n").replace("\n", "\r\n"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // A term in a comment is not its use.
                "# see cim:Foo\nex:s a sh:NodeShape ;\n    sh:targetClass cim:Foo .\n",
                // A '#' inside an IRI or a string is not a comment.
                "ex:s sh:name \"# not\" ; <http://ex.org/a#b> cim:Foo ;\n    sh:targetClass cim:Foo .\n",
                // The subject on its own line, the term further down; and the 'a' keyword.
                "ex:s\n    a sh:NodeShape ;\n    sh:targetClass cim:Foo .\nex:t a sh:NodeShape ;\n"
                        + "    sh:targetClass cim:Foo .\n",
                // A prefixed name must be a whole token.
                "ex:s sh:targetClass cim:FooBar , cim:Foo .\n"
            })
    void findsWhatTheLocatorFindsInTheCornerCases(String body) {
        assertSamePositions(
                "@prefix sh: <http://www.w3.org/ns/shacl#> .\n"
                        + "@prefix cim: <http://iec.ch/TC57/CIM100#> .\n"
                        + "@prefix ex: <http://ex.org/> .\n"
                        + body);
    }

    private static void assertSamePositions(String text) {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.fromString(text, Lang.TURTLE).parse(graph);
        var index = new SourceIndex(text, graph.getPrefixMapping());

        var lookups = lookups(graph);
        assertThat(lookups).isNotEmpty();
        for (var lookup : lookups) {
            var expected =
                    SourceLocator.locateWithHint(
                            text, lookup[0], graph.getPrefixMapping(), lookup[1]);
            var actual = index.locate(lookup[0], lookup[1]);
            assertThat(actual == null ? null : actual.line())
                    .as("line of %s near %s", lookup[0], lookup[1])
                    .isEqualTo(expected.line());
            assertThat(actual == null ? null : actual.column())
                    .as("column of %s near %s", lookup[0], lookup[1])
                    .isEqualTo(expected.column());
        }
    }

    private static boolean isVocabulary(Node term) {
        return term.getURI().startsWith("http://www.w3.org/");
    }

    /** Each schema term the file uses, with its subject as hint, spread over the whole file. */
    private static List<Node[]> lookups(Graph graph) {
        var all = new ArrayList<Node[]>();
        graph.find()
                .forEachRemaining(
                        triple -> {
                            // The terms findings are about: the schema's classes and properties.
                            // Vocabulary terms such as sh:Violation are written thousands of times,
                            // and the reference implementation takes minutes to choose among them.
                            if (triple.getObject().isURI() && !isVocabulary(triple.getObject())) {
                                all.add(new Node[] {triple.getObject(), triple.getSubject()});
                            }
                        });
        // Terms nobody wrote, and lookups without a hint.
        all.add(new Node[] {NodeFactory.createURI("http://ex.org/nowhere"), null});
        if (!all.isEmpty()) {
            all.add(new Node[] {all.get(0)[0], null});
        }
        int step = Math.max(1, all.size() / LOOKUPS);
        var sampled = new ArrayList<Node[]>();
        for (int i = 0; i < all.size(); i += step) {
            sampled.add(all.get(i));
        }
        return sampled;
    }
}
