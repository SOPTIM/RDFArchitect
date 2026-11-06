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

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

import java.util.Set;

class EffectiveConstraintsDescribeTest {

    private static final String CIM = "https://cim.ucaiug.io/ns#";

    @Test
    void writesTheStandardVocabulariesShortWhenTheWorkspaceDoesNotBindThem() {
        // A schema binds cim:, rarely sh: or xsd:, and the full IRIs made every summary unreadable.
        var prefixes = PrefixMapping.Factory.create().setNsPrefix("cim", CIM);
        var constraint =
                new EffectiveConstraints.Constraint(
                        0,
                        1,
                        Set.of("http://www.w3.org/2001/XMLSchema#string"),
                        Set.of(CIM + "Equipment"),
                        null,
                        Set.of("http://www.w3.org/ns/shacl#IRI"),
                        null);

        assertThat(EffectiveConstraints.describe(constraint, prefixes))
                .contains("xsd:string")
                .contains("sh:IRI")
                .contains("cim:Equipment")
                .doesNotContain("<http");
    }

    @Test
    void keepsTheWorkspacesOwnPrefixForAStandardNamespace() {
        var prefixes =
                PrefixMapping.Factory.create().setNsPrefix("shacl", "http://www.w3.org/ns/shacl#");
        var constraint =
                new EffectiveConstraints.Constraint(
                        null,
                        null,
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of("http://www.w3.org/ns/shacl#IRI"),
                        null);

        assertThat(EffectiveConstraints.describe(constraint, prefixes)).contains("shacl:IRI");
    }

    private static final String BASE = CIM + "Base";
    private static final String LEAF = CIM + "Leaf";
    private static final String OTHER = CIM + "Other";

    private static ClassHierarchy leafDerivesFromBase() {
        var schema = GraphFactory.createDefaultGraph();
        schema.add(
                Triple.create(
                        NodeFactory.createURI(LEAF),
                        RDFS.subClassOf.asNode(),
                        NodeFactory.createURI(BASE)));
        return ClassHierarchy.of(schema);
    }

    private static String describeClasses(Set<String> classes, Set<String> types) {
        return EffectiveConstraints.describe(
                new EffectiveConstraints.Constraint(
                        null, null, Set.of(), classes, types, Set.of(), null),
                PrefixMapping.Factory.create().setNsPrefix("cim", CIM),
                leafDerivesFromBase());
    }

    @Test
    void aListOfARangeAndItsSubclassesReadsAsTheRange() {
        assertThat(describeClasses(Set.of(), Set.of(BASE, LEAF))).isEqualTo("of class cim:Base");
    }

    @Test
    void everyClassAValueMustBelongToIsNamedSinceTheyAllApply() {
        assertThat(describeClasses(Set.of(BASE, LEAF), null))
                .isEqualTo("of class cim:Base and cim:Leaf");
    }

    @Test
    void aClassAndTheSameRangeAsAListReadOnce() {
        assertThat(describeClasses(Set.of(BASE), Set.of(BASE, LEAF)))
                .isEqualTo("of class cim:Base");
    }

    @Test
    void aListOfUnrelatedClassesOffersThemAsAlternatives() {
        assertThat(describeClasses(Set.of(), Set.of(BASE, OTHER)))
                .isEqualTo("of class cim:Base or cim:Other");
    }

    @Test
    void aClassBesideAListWithSomethingElseInItNamesBoth() {
        assertThat(describeClasses(Set.of(LEAF), Set.of(BASE, OTHER)))
                .isEqualTo("of class cim:Leaf, of class cim:Base or cim:Other");
    }

    @Test
    void anEmptyListIsSaidEvenBesideAClass() {
        assertThat(describeClasses(Set.of(BASE), Set.of()))
                .isEqualTo("of class cim:Base, of class nothing");
    }

    @Test
    void listsWithNothingInCommonSaySo() {
        assertThat(describeClasses(Set.of(), Set.of())).isEqualTo("of class nothing");
    }
}
