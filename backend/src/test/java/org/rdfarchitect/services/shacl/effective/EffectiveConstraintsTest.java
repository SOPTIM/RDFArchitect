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

package org.rdfarchitect.services.shacl.effective;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints.Key;

import java.util.LinkedHashMap;
import java.util.List;

class EffectiveConstraintsTest {

    private static final String EX = "http://example.org/";
    private static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    private static final String SH = "http://www.w3.org/ns/shacl#";

    private static final Key THING_VALUE = new Key(EX + "Thing", EX + "value");

    private static Graph shapes(String body) {
        var document =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                """
                        + body;
        return RDFParser.fromString(document, Lang.TURTLE).toGraph();
    }

    @Test
    void aRuleNamedFromAnotherDocumentCounts() {
        var referring = shapes("ex:Shape sh:targetClass ex:Thing ; sh:property ex:Rule .");
        var defining = shapes("ex:Rule sh:path ex:value ; sh:minCount 1 .");
        var documents = new LinkedHashMap<String, Graph>();
        documents.put("referring.ttl", referring);
        documents.put("defining.ttl", defining);

        var asserted = EffectiveConstraints.of(documents);

        assertThat(asserted.constraints().get(THING_VALUE).minCount()).isEqualTo(1);
        assertThat(asserted.statedIn().get(THING_VALUE)).containsExactly("referring.ttl");
    }

    @Test
    void aDocumentsOwnDefinitionOfARuleIsTheOneItMeans() {
        var referring =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ; sh:property ex:Rule .
                        ex:Rule sh:path ex:value ; sh:minCount 1 .
                        """);
        var other = shapes("ex:Rule sh:path ex:value ; sh:minCount 7 .");
        var documents = new LinkedHashMap<String, Graph>();
        documents.put("referring.ttl", referring);
        documents.put("other.ttl", other);

        assertThat(EffectiveConstraints.of(documents).constraints().get(THING_VALUE).minCount())
                .isEqualTo(1);
    }

    @Test
    void aPropertyShapeCanBeItsOwnTarget() {
        var graph = shapes("ex:Shape sh:targetClass ex:Thing ; sh:path ex:value ; sh:minCount 1 .");

        assertThat(EffectiveConstraints.of(graph).get(THING_VALUE).minCount()).isEqualTo(1);
    }

    @Test
    void aPropertysRulesSpreadOverSeveralShapesCollapseIntoOne() {
        var graph =
                shapes(
                        """
                        ex:First sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:minCount 1 ; sh:maxCount 5 ] .
                        ex:Second sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:minCount 0 ; sh:maxCount 3 ] ,
                                        [ sh:path ex:value ; sh:datatype xsd:string ] .
                        """);

        var constraint = EffectiveConstraints.of(graph).get(THING_VALUE);

        assertThat(constraint.minCount()).isEqualTo(1);
        assertThat(constraint.maxCount()).isEqualTo(3);
        assertThat(constraint.dataTypes()).containsExactly(XSD + "string");
    }

    @Test
    void twoDatatypesAreKeptSoThatAContradictionCanBeSeen() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:datatype xsd:string ] ,
                                        [ sh:path ex:value ; sh:datatype xsd:integer ] .
                        """);

        assertThat(EffectiveConstraints.of(graph).get(THING_VALUE).dataTypes())
                .containsExactlyInAnyOrder(XSD + "string", XSD + "integer");
    }

    @Test
    void aValueHasToBeInEveryListThatLimitsIt() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:in ( "a" "b" ) ] ,
                                        [ sh:path ex:value ; sh:in ( "b" "c" ) ] .
                        """);

        assertThat(EffectiveConstraints.of(graph).get(THING_VALUE).allowedValues())
                .extracting(Node::getLiteralLexicalForm)
                .containsExactly("b");
    }

    @Test
    void aValueTypeListIsAboutTheValuesOfItsProperty() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path ( ex:value rdf:type ) ;
                                          sh:in ( ex:Left ex:Right ) ;
                                          sh:nodeKind sh:IRI ] .
                        """);

        var constraint = EffectiveConstraints.of(graph).get(THING_VALUE);

        assertThat(constraint.valueTypes()).containsExactlyInAnyOrder(EX + "Left", EX + "Right");
        assertThat(constraint.nodeKinds()).containsExactly(SH + "IRI");
        assertThat(constraint.allowedValues()).isNull();
    }

    @Test
    void anyOtherPathExpressionHasNothingToBeComparedWith() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path [ sh:inversePath ex:value ] ; sh:minCount 1 ] ,
                                        [ sh:path ( ex:value ex:other ) ; sh:minCount 1 ] .
                        """);

        assertThat(EffectiveConstraints.of(graph)).isEmpty();
    }

    @Test
    void aShapeWithTwoPathsConstrainsBoth() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value , ex:other ; sh:maxCount 1 ] .
                        """);

        var read = EffectiveConstraints.of(graph);

        assertThat(read.get(THING_VALUE).maxCount()).isEqualTo(1);
        assertThat(read.get(new Key(EX + "Thing", EX + "other")).maxCount()).isEqualTo(1);
    }

    @Test
    void aDeactivatedShapeAsksNothing() {
        var graph =
                shapes(
                        """
                        ex:OffAltogether sh:targetClass ex:Thing ;
                            sh:deactivated true ;
                            sh:property [ sh:path ex:value ; sh:minCount 1 ] .
                        ex:OneRuleOff sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:maxCount 1 ; sh:deactivated true ] .
                        ex:StillOn sh:targetClass ex:Thing ;
                            sh:deactivated false ;
                            sh:property [ sh:path ex:value ; sh:datatype xsd:string ] .
                        """);

        var constraint = EffectiveConstraints.of(graph).get(THING_VALUE);

        assertThat(constraint.minCount()).isNull();
        assertThat(constraint.maxCount()).isNull();
        assertThat(constraint.dataTypes()).containsExactly(XSD + "string");
    }

    @Test
    void aPathThatConstrainsNothingLeavesNoEntry() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:name "named, not constrained" ] .
                        """);

        assertThat(EffectiveConstraints.of(graph)).isEmpty();
    }

    @Test
    void whatOnlyReportsIsKeptApartFromWhatIsEnforced() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:minCount 1 ;
                                          sh:severity sh:Warning ] .
                        """);
        var documents = new LinkedHashMap<String, Graph>();
        documents.put("advice.ttl", graph);

        var asserted = EffectiveConstraints.of(documents);

        assertThat(EffectiveConstraints.of(graph)).isEmpty();
        assertThat(asserted.constraints()).isEmpty();
        assertThat(asserted.advisory().get(THING_VALUE).minCount()).isEqualTo(1);
        assertThat(asserted.statedIn().get(THING_VALUE)).containsExactly("advice.ttl");
    }

    @Test
    void severalDocumentsApplyTogetherAndEachIsNamed() {
        var documents = new LinkedHashMap<String, Graph>();
        documents.put(
                "counts.ttl",
                shapes(
                        """
                        ex:Counts sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:minCount 1 ] .
                        """));
        documents.put(
                "types.ttl",
                shapes(
                        """
                        ex:Types sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:datatype xsd:string ] .
                        """));

        var asserted = EffectiveConstraints.of(documents);

        var constraint = asserted.constraints().get(THING_VALUE);
        assertThat(constraint.minCount()).isEqualTo(1);
        assertThat(constraint.dataTypes()).containsExactly(XSD + "string");
        assertThat(asserted.statedIn().get(THING_VALUE)).containsExactly("counts.ttl", "types.ttl");
    }

    @Test
    void readingChosenShapesLeavesOutThoseThatCannotFailValidation() {
        var graph =
                shapes(
                        """
                        ex:enforced sh:path ex:value ; sh:minCount 1 .
                        ex:advice sh:path ex:value ; sh:maxCount 1 ; sh:severity sh:Info .
                        ex:off sh:path ex:value ; sh:datatype xsd:string ; sh:deactivated true .
                        """);
        var chosen =
                List.of(
                        NodeFactory.createURI(EX + "enforced"),
                        NodeFactory.createURI(EX + "advice"),
                        NodeFactory.createURI(EX + "off"));

        var constraint = EffectiveConstraints.readAll(graph, chosen);

        assertThat(constraint.minCount()).isEqualTo(1);
        assertThat(constraint.maxCount()).isNull();
        assertThat(constraint.dataTypes()).isEmpty();
        assertThat(EffectiveConstraints.readAll(graph, List.of()).isEmpty()).isTrue();
    }

    @Test
    void aCyclicListIsNotAList() {
        var graph =
                shapes(
                        """
                        ex:Shape sh:targetClass ex:Thing ;
                            sh:property [ sh:path ex:value ; sh:in _:loop ] .
                        _:loop rdf:first "a" ; rdf:rest _:loop .
                        """);

        assertThat(EffectiveConstraints.of(graph)).isEmpty();
    }
}
