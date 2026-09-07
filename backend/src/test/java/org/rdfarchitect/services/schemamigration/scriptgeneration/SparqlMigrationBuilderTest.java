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

package org.rdfarchitect.services.schemamigration.scriptgeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.context.SchemaMigrationContext;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.services.schemamigration.artifacts.SparqlMigrationBuilder;
import org.rdfarchitect.services.schemamigration.artifacts.SparqlUpdateGenerator;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SparqlMigrationBuilderTest {

    private static final String PREFIX = "http://iec.ch/TC57/CIM100#";
    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
    private static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";

    @Mock private MigrationSessionStore migrationSessionStore;

    private SparqlMigrationBuilder builder;

    @BeforeEach
    void setUp() {
        var context = new SchemaMigrationContext();
        context.setUpdatedSchema(ModelFactory.createDefaultModel().getGraph());
        lenient().when(migrationSessionStore.getContext()).thenReturn(context);
        builder = new SparqlMigrationBuilder(new SparqlUpdateGenerator(migrationSessionStore));
    }

    @Test
    void generateMigrationScript_datatypeChanged_convertsValuesWithDefaultAsFallback() {
        var script = generateFor(datatypeChange(XSD_STRING, XSD_INTEGER, false, List.of()));

        assertThat(script).contains("DELETE").contains("INSERT").contains("COALESCE");
    }

    @Test
    void generateMigrationScript_datatypesMarkedEquivalent_keepsExistingValues() {
        var script = generateFor(datatypeChange(XSD_STRING, XSD_INTEGER, true, List.of()));

        assertThat(script).isBlank();
    }

    @Test
    void generateMigrationScript_enumDatatypeChanged_replacesValues() {
        var script =
                generateFor(
                        datatypeChange(
                                PREFIX + "OldKind",
                                PREFIX + "NewKind",
                                false,
                                List.of(PREFIX + "NewKind.one")));

        assertThat(script).contains("DELETE").contains("INSERT");
    }

    @Test
    void generateMigrationScript_enumDatatypesMarkedEquivalent_keepsExistingValues() {
        var script =
                generateFor(
                        datatypeChange(
                                PREFIX + "OldKind",
                                PREFIX + "NewKind",
                                true,
                                List.of(PREFIX + "NewKind.one")));

        assertThat(script).isBlank();
    }

    private String generateFor(SemanticAttributeChange attributeChange) {
        var classChange =
                SemanticClassChange.builder()
                        .iri(PREFIX + "Switch")
                        .label("Switch")
                        .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                        .attributes(new ArrayList<>(List.of(attributeChange)))
                        .associations(new ArrayList<>())
                        .enumEntries(new ArrayList<>())
                        .build();

        return builder.generateMigrationScript(List.of(classChange));
    }

    private SemanticAttributeChange datatypeChange(
            String oldDataType,
            String newDataType,
            boolean equivalent,
            List<String> allowedValues) {
        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "Switch.state")
                        .label("state")
                        .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                        .oldDataType(oldDataType)
                        .dataType(newDataType)
                        .primitiveDataType(newDataType)
                        .defaultValue(allowedValues.isEmpty() ? "0" : allowedValues.getFirst())
                        .dataTypesEquivalent(equivalent)
                        .allowedValues(new ArrayList<>(allowedValues))
                        .build();
        attributeChange
                .getChanges()
                .add(
                        new SemanticFieldChange(
                                SemanticFieldChangeType.DATATYPE_CHANGE, oldDataType, newDataType));
        return attributeChange;
    }
}
