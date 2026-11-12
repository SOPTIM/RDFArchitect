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
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.Set;

class ClassHierarchyTest {

    private static final String EX = "http://example.org/";
    private static final String BASE = EX + "Base";
    private static final String MIDDLE = EX + "Middle";
    private static final String LEAF = EX + "Leaf";
    private static final String SIBLING = EX + "Sibling";
    private static final String FOREIGN = EX + "Foreign";

    private static Graph schema(String body) {
        var document =
                """
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix ex: <http://example.org/> .
                """
                        + body;
        return RDFParser.fromString(document, Lang.TURTLE).toGraph();
    }

    /** Base and Middle are abstract; Leaf derives from both, and Sibling from Base alone. */
    private static ClassHierarchy hierarchy() {
        var concrete = "<" + CIMS.stereotype.getURI() + "> <" + CIMStereotypes.concreteString + ">";
        return ClassHierarchy.of(
                schema(
                        """
                        ex:Base a rdfs:Class .
                        ex:Middle a rdfs:Class ; rdfs:subClassOf ex:Base .
                        ex:Leaf a rdfs:Class ; rdfs:subClassOf ex:Middle ; %1$s .
                        ex:Sibling a rdfs:Class ; rdfs:subClassOf ex:Base ; %1$s .
                        """
                                .formatted(concrete)));
    }

    @Test
    void aSchemaMarkingNothingConcreteFiltersNothing() {
        var hierarchy = ClassHierarchy.of(schema("ex:Declared a rdfs:Class ."));
        var listed = Set.of(EX + "Declared", "http://other.example/Foreign");

        assertThat(hierarchy.typedAs(listed)).isEqualTo(listed);
    }

    /**
     * Not an oversight. Official lists name classes the schema doesn't mark concrete on purpose:
     * CGMES's Header writes {@code sh:in (cim:Statements)} and the geographical location profile
     * {@code sh:in (cim:StreetAddress)}, and reading those as "admits nothing" would report the
     * official files as contradicting their own schema.
     */
    @Test
    void aListOfOnlyUnmarkedClassesStillMeansThem() {
        assertThat(hierarchy().typedAs(Set.of(BASE))).containsExactly(BASE);
    }

    @Test
    void aClassDerivesFromItselfAndFromEveryAncestor() {
        var hierarchy = hierarchy();

        assertThat(hierarchy.isSubClassOf(LEAF, LEAF)).isTrue();
        assertThat(hierarchy.isSubClassOf(LEAF, MIDDLE)).isTrue();
        assertThat(hierarchy.isSubClassOf(LEAF, BASE)).isTrue();
        assertThat(hierarchy.isSubClassOf(BASE, LEAF)).isFalse();
        assertThat(hierarchy.isSubClassOf(SIBLING, MIDDLE)).isFalse();
    }

    @Test
    void anInstanceOfAClassIsTypedWithAConcreteClassDerivingFromIt() {
        assertThat(hierarchy().instancesOf(Set.of(BASE))).containsExactlyInAnyOrder(LEAF, SIBLING);
    }

    @Test
    void aValueTypeListAdmitsOnlyWhatItNamesAndAnAbstractClassAdmitsNothing() {
        assertThat(hierarchy().typedAs(Set.of(BASE, LEAF))).containsExactly(LEAF);
    }

    @Test
    void aClassTheSchemaDoesNotDeclareCannotBeKnownToBeAbstract() {
        assertThat(hierarchy().typedAs(Set.of(FOREIGN, MIDDLE))).containsExactly(FOREIGN);
    }

    @Test
    void aSchemaMarkingNoClassConcreteSaysNothingAboutAbstractness() {
        var unmarked =
                ClassHierarchy.of(
                        schema(
                                """
                                ex:Base a rdfs:Class .
                                ex:Leaf a rdfs:Class ; rdfs:subClassOf ex:Base .
                                """));

        assertThat(unmarked.instancesOf(Set.of(BASE))).containsExactlyInAnyOrder(BASE, LEAF);
    }

    @Test
    void aListNamingARangeAndItsSubclassesReadsAsTheRange() {
        assertThat(hierarchy().roots(Set.of(BASE, MIDDLE, LEAF, FOREIGN)))
                .containsExactlyInAnyOrder(BASE, FOREIGN);
    }

    @Test
    void reportsWhichClassesItDeclares() {
        var hierarchy = hierarchy();

        assertThat(hierarchy.isEmpty()).isFalse();
        assertThat(hierarchy.declares(LEAF)).isTrue();
        assertThat(hierarchy.declares(FOREIGN)).isFalse();
        assertThat(hierarchy.declared()).containsExactlyInAnyOrder(BASE, MIDDLE, LEAF, SIBLING);
    }

    @Test
    void noHierarchyLetsEveryClassAdmitItselfAlone() {
        assertThat(ClassHierarchy.NONE.isEmpty()).isTrue();
        assertThat(ClassHierarchy.NONE.instancesOf(Set.of(BASE))).containsExactly(BASE);
        assertThat(ClassHierarchy.NONE.isSubClassOf(LEAF, BASE)).isFalse();
    }

    @Test
    void classesInACycleAllStayAsRootsSinceNeitherIsMoreGeneral() {
        var cyclic =
                ClassHierarchy.of(
                        schema(
                                """
                                ex:Base rdfs:subClassOf ex:Leaf .
                                ex:Leaf rdfs:subClassOf ex:Base .
                                """));

        assertThat(cyclic.roots(Set.of(BASE, LEAF))).containsExactlyInAnyOrder(BASE, LEAF);
    }

    @Test
    void aCycleInTheHierarchyEnds() {
        var cyclic =
                ClassHierarchy.of(
                        schema(
                                """
                                ex:Base rdfs:subClassOf ex:Leaf .
                                ex:Leaf rdfs:subClassOf ex:Base .
                                """));

        assertThat(cyclic.isSubClassOf(LEAF, BASE)).isTrue();
        assertThat(cyclic.isSubClassOf(BASE, LEAF)).isTrue();
    }
}
