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

package org.rdfarchitect.services.shacl.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints.Constraint;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints.Key;

import java.util.List;
import java.util.Map;
import java.util.Set;

class OtherVocabulariesTest {

    private static final String CIM18 = "https://cim.ucaiug.io/ns#";
    private static final String CIM100 = "http://iec.ch/TC57/CIM100#";
    private static final String EXT = "http://example.org/extension#";

    private static final Key SWITCH =
            new Key(CIM18 + "TopologyAction", CIM18 + "TopologyAction.Switch");

    private static Constraint types(Set<String> valueTypes) {
        return new Constraint(1, 1, Set.of(), Set.of(), valueTypes, Set.of(), null);
    }

    private static Constraint kept(Key key, Constraint constraint) {
        var asserted =
                new EffectiveConstraints.Asserted(
                        Map.of(key, constraint), Map.of(), Map.of(key, List.of("doc.ttl")));
        return OtherVocabularies.leaveOut(asserted, Set.of(CIM18)).constraints().get(key);
    }

    @Test
    void aTypeListNamingOnlyAnotherVocabularyStatesNoType() {
        assertThat(kept(SWITCH, types(Set.of(CIM100 + "Switch", CIM100 + "Breaker"))).valueTypes())
                .isNull();
    }

    @Test
    void aTypeListKeepsTheTypesOfTheSchemasVocabulary() {
        assertThat(kept(SWITCH, types(Set.of(CIM100 + "Switch", CIM18 + "Switch"))).valueTypes())
                .containsExactly(CIM18 + "Switch");
    }

    @Test
    void aTypeListNamingNoTypeAdmitsNothingAndStaysSo() {
        assertThat(kept(SWITCH, types(Set.of())).valueTypes()).isEmpty();
    }

    @Test
    void aRuleOnAClassOfAnotherVocabularyIsLeftOut() {
        var key = new Key(CIM100 + "TopologyAction", CIM18 + "TopologyAction.Switch");

        assertThat(kept(key, types(Set.of(CIM18 + "Switch")))).isNull();
    }

    @Test
    void anExtensionIsWrittenInTheVocabularyOfTheClassesItExtendsAsWell() {
        var extension =
                RDFParser.fromString(
                                """
                                @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                                @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
                                @prefix cim:  <http://iec.ch/TC57/CIM100#> .
                                @prefix ext:  <http://example.org/extension#> .

                                ext:Note a rdfs:Class .
                                ext:Equipment.note a rdf:Property ;
                                    rdfs:domain cim:Equipment ; cims:dataType ext:Note .
                                """,
                                Lang.TURTLE)
                        .toGraph();

        assertThat(OtherVocabularies.of(extension)).contains(EXT, CIM100);
    }
}
