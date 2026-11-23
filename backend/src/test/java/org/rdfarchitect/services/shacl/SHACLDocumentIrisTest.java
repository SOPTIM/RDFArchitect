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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.modify.request.QuadDataAcc;
import org.apache.jena.sparql.modify.request.UpdateDataInsert;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.snapshots.ShapesDocumentGraphs;
import org.rdfarchitect.exception.database.InvalidContentException;

import java.util.List;

/**
 * A saved document holds only IRIs that can be sent to a store.
 *
 * <p>Persisting writes every document of the workspace in one SPARQL update, so one IRI the update
 * cannot carry would make every later persist of the workspace fail.
 */
class SHACLDocumentIrisTest {

    private static final String WORKSPACE = "cgmes";

    private static final String PREFIXES = "@prefix sh: <http://www.w3.org/ns/shacl#> .\n";

    private DatabasePort databasePort;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        SessionContext.setSessionId(getClass().getSimpleName());
        databasePort.createWorkspaceIfAbsent(WORKSPACE);
        databasePort.enableEditing(WORKSPACE);
        service = new SHACLStoringService(databasePort);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private static String shapeNamed(String iri) {
        return PREFIXES + iri + " a sh:NodeShape .\n";
    }

    /** What persisting the workspace's documents would send, parsed as a store would parse it. */
    private void assertPersistable() {
        var quads = new QuadDataAcc();
        try (var transaction = databasePort.beginTransaction(WORKSPACE, ReadWrite.READ)) {
            ShapesDocumentGraphs.of(transaction.shapesDocuments().values())
                    .forEach(
                            (name, graph) -> {
                                quads.setGraph(NodeFactory.createURI(name));
                                graph.find().forEachRemaining(quads::addTriple);
                            });
        }
        var update = new UpdateRequest().add(new UpdateDataInsert(quads)).toString();
        assertThatNoException().isThrownBy(() -> UpdateFactory.create(update));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<http://example.org/a|b>",
                "<http://example.org/a^b>",
                "<http://example.org/a`b>",
                "<http://example.org/a{b}>",
                "<http://example.org/a\\u0022b>",
                "<http://example.org/a\\u003Eb>",
                "<http://example.org/a\\u005Cb>",
                "<http://example.org/a\\u0020b>",
                "<http://example.org/a\\u000Ab>"
            })
    void anIriTheParserOnlyWarnsAboutIsRefusedWhereItStands(String iri) {
        assertThatExceptionOfType(InvalidContentException.class)
                .isThrownBy(
                        () ->
                                service.createShapesDocument(
                                        WORKSPACE,
                                        List.of(),
                                        "odd.ttl",
                                        null,
                                        shapeNamed(iri),
                                        Lang.TURTLE))
                .withMessageContaining("[line: 2, col: 1]")
                .withMessageContaining("cannot hold");
        assertThat(service.listShapesDocuments(WORKSPACE)).isEmpty();
    }

    @Test
    void anEditCannotBringSuchAnIriIn() {
        var id =
                service.createShapesDocument(
                                WORKSPACE,
                                List.of(),
                                "eq.ttl",
                                null,
                                shapeNamed("<http://example.org/a>"),
                                Lang.TURTLE)
                        .getId();

        assertThatExceptionOfType(InvalidContentException.class)
                .isThrownBy(
                        () ->
                                service.replaceShapesDocumentText(
                                        WORKSPACE,
                                        id,
                                        shapeNamed("<http://example.org/a|b>"),
                                        null))
                .withMessageContaining("%7C");
        assertThat(service.getShapesDocumentText(WORKSPACE, id))
                .isEqualTo(shapeNamed("<http://example.org/a>"));
    }

    @Test
    void anIriInAnotherSyntaxIsCheckedToo() {
        var rdfXml =
                """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                  <rdf:Description rdf:about="http://example.org/a|b">
                    <rdf:type rdf:resource="http://www.w3.org/ns/shacl#NodeShape"/>
                  </rdf:Description>
                </rdf:RDF>
                """;

        assertThatExceptionOfType(InvalidContentException.class)
                .isThrownBy(
                        () ->
                                service.createShapesDocument(
                                        WORKSPACE,
                                        List.of(),
                                        "odd.rdf",
                                        "odd.rdf",
                                        rdfXml,
                                        Lang.RDFXML))
                .withMessageContaining("<http://example.org/a|b>");
    }

    @Test
    void aDatatypeIsAnIriToo() {
        assertThatExceptionOfType(InvalidContentException.class)
                .isThrownBy(
                        () ->
                                service.createShapesDocument(
                                        WORKSPACE,
                                        List.of(),
                                        "odd.ttl",
                                        null,
                                        PREFIXES
                                                + "<http://example.org/S> sh:minInclusive"
                                                + " \"1\"^^<http://example.org/a|b> .\n",
                                        Lang.TURTLE));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<http://example.org/a%7Cb>",
                "<http://example.org/ä>",
                "<urn:x>",
                "<http://example.org/a#b#c>"
            })
    void anIriAStoreTakesIsSavedAndCanBePersisted(String iri) {
        service.createShapesDocument(
                WORKSPACE, List.of(), "fine.ttl", null, shapeNamed(iri), Lang.TURTLE);

        assertThat(service.listShapesDocuments(WORKSPACE)).hasSize(1);
        assertPersistable();
    }

    @Test
    void aSavedDocumentCanAlwaysBePersisted() {
        for (var iri :
                List.of(
                        "<http://example.org/a|b>",
                        "<http://example.org/a\\u003Eb>",
                        "<http://example.org/ok>")) {
            try {
                service.createShapesDocument(
                        WORKSPACE,
                        List.of(),
                        "d" + iri.hashCode() + ".ttl",
                        null,
                        shapeNamed(iri),
                        Lang.TURTLE);
            } catch (InvalidContentException _) {
                // Refused, so it cannot reach a persist.
            }
        }

        assertThat(service.listShapesDocuments(WORKSPACE)).hasSize(1);
        assertPersistable();
    }

    @Test
    void validationReportsTheIriAsAnErrorWhereItStands() {
        var result =
                ShapesTurtleParser.parse(
                        PREFIXES
                                + "<http://example.org/ok> a sh:NodeShape .\n"
                                + "<http://example.org/a|b> a sh:NodeShape .\n");

        assertThat(result.failed()).isFalse();
        assertThat(result.findings())
                .filteredOn(finding -> finding.getSeverity().name().equals("ERROR"))
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getCode())
                                    .isEqualTo(ShapesTurtleParser.UNWRITABLE_IRI_CODE);
                            assertThat(finding.getLine()).isEqualTo(3);
                            assertThat(finding.getMessage()).contains("'|'", "%7C");
                        });
        // The parser's own warning naming the whole IRI says the same, only as a warning.
        assertThat(result.findings())
                .noneMatch(finding -> finding.getMessage().startsWith("Bad IRI"));
    }
}
