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

package org.rdfarchitect.rdf.merge;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

class ModelResourceExclusiveMergeTest {

    private static final String PREFIXES =
            """
            @prefix ex: <http://example.org/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            """;

    private static Model model(String body) {
        return ModelFactory.createModelForGraph(
                RDFParser.fromString(PREFIXES + body, Lang.TURTLE).toGraph());
    }

    @Test
    void addsWhatTheSecondarySaysAboutResourcesThePrimaryDoesNotMention() {
        var primary = model("ex:A ex:p ex:B .");
        var secondary =
                model(
                        """
                        ex:A ex:q "the primary has A" .
                        ex:B ex:q "the primary mentions B" .
                        ex:C ex:r [ ex:s [ ex:t "nested" ] ] ; ex:u ( "x" "y" ) .
                        ex:D ex:link ex:B .
                        [] ex:orphan "a blank subject nothing named leads to" .
                        """);

        var merged = new ModelResourceExclusiveMerge().merge(primary, secondary);

        assertThat(
                        merged.isIsomorphicWith(
                                model(
                                        """
                                        ex:A ex:p ex:B .
                                        ex:C ex:r [ ex:s [ ex:t "nested" ] ] ; ex:u ( "x" "y" ) .
                                        ex:D ex:link ex:B .
                                        """)))
                .isTrue();
    }

    @Test
    void endsOnACyclicStructureInTheSecondary() {
        var primary = model("ex:A ex:p ex:B .");
        var secondary =
                model(
                        """
                        ex:C ex:r _:loop .
                        _:loop rdf:first "a" ; rdf:rest _:loop .
                        """);

        var merged = new ModelResourceExclusiveMerge().merge(primary, secondary);

        assertThat(merged.size()).isEqualTo(4);
    }
}
