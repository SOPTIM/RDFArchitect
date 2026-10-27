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

package org.rdfarchitect.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;

import java.util.UUID;

/** What the changelog says about a change, and what it never says. */
class ChangeDescriptionsTest {

    @Test
    void actionOn_namedTarget_namesIt() {
        assertThat(ChangeDescriptions.actionOn("Deleted", "diagram", "Overview"))
                .isEqualTo("Deleted diagram \"Overview\"");
    }

    @Test
    void actionOn_unnamedTarget_saysLessRatherThanIdentifying() {
        assertThat(ChangeDescriptions.actionOn("Deleted", "diagram", ""))
                .isEqualTo("Deleted a diagram");
    }

    /** An action that already ends in a preposition must not read "… to in diagram". */
    @Test
    void actionOn_actionEndingInAPreposition_readsAsASentence() {
        assertThat(ChangeDescriptions.actionOn("Added classes to", "diagram", "Overview"))
                .isEqualTo("Added classes to diagram \"Overview\"");
        assertThat(ChangeDescriptions.actionOn("Added classes to", "diagram", ""))
                .isEqualTo("Added classes to a diagram");
    }

    @Test
    void actionIn_unnamedContainer_leavesThePlaceOut() {
        assertThat(ChangeDescriptions.actionIn("Moved classes", "package", "Core"))
                .isEqualTo("Moved classes in package \"Core\"");
        assertThat(ChangeDescriptions.actionIn("Moved classes", "package", ""))
                .isEqualTo("Moved classes");
    }

    /** The label object itself must never reach a reader, which "%s" would print in full. */
    @Test
    void name_label_isItsText() {
        assertThat(ChangeDescriptions.name(new RDFSLabel("Breaker", "en"))).isEqualTo("Breaker");
        assertThat(ChangeDescriptions.name((RDFSLabel) null)).isEmpty();
        assertThat(ChangeDescriptions.name(new RDFSLabel(null, "en"))).isEmpty();
        assertThat(ChangeDescriptions.name(new RDFSLabel("  ", "en"))).isEmpty();
    }

    @Test
    void className_classTheGraphDoesNotDeclare_hasNoName() {
        assertThat(
                        ChangeDescriptions.className(
                                GraphFactory.createDefaultGraph(), UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void localName_iri_isTheTailTheEditorShows() {
        assertThat(ChangeDescriptions.localName("http://example.org/cim#Breaker"))
                .isEqualTo("Breaker");
        assertThat(ChangeDescriptions.localName("http://example.org/graphs/core"))
                .isEqualTo("core");
        assertThat(ChangeDescriptions.localName(null)).isEmpty();
    }
}
