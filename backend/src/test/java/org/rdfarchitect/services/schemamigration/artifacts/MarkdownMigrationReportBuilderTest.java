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

package org.rdfarchitect.services.schemamigration.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

import java.util.List;

class MarkdownMigrationReportBuilderTest {

    private static final String PREFIX = "http://example.org#";

    private final MarkdownMigrationReportBuilder builder =
            new MarkdownMigrationReportBuilder(new SchemaConfig());

    @Test
    void generateSummaryMigrationReport_movedAttribute_rendersOldAndNewClassLabels() {
        var domainChange =
                new SemanticFieldChange(
                        SemanticFieldChangeType.DOMAIN_CHANGE,
                        PREFIX + "ClassA",
                        PREFIX + "ClassB");

        var movedAttribute =
                SemanticAttributeChange.builder()
                        .label("voltage")
                        .iri(PREFIX + "ClassB.voltage")
                        .oldIRI(PREFIX + "ClassA.voltage")
                        .semanticResourceChangeType(SemanticResourceChangeType.MOVE)
                        .changes(List.of(domainChange))
                        .build();

        var classChange =
                SemanticClassChange.builder()
                        .label("ClassB")
                        .iri(PREFIX + "ClassB")
                        .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                        .attributes(List.of(movedAttribute))
                        .associations(List.of())
                        .enumEntries(List.of())
                        .build();

        var emptyGraph = ModelFactory.createDefaultModel().getGraph();

        var report =
                builder.generateSummaryMigrationReport(
                        List.of(classChange), emptyGraph, emptyGraph, false);

        assertThat(report).contains("Moved from ClassA.voltage to ClassB.voltage");
    }
}
