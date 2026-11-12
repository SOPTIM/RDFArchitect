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

package org.rdfarchitect.services.schemamigration.renamings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappingOverview;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.context.SchemaMigrationContext;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.ArrayList;
import java.util.List;

/**
 * The property step has to tell a mapping the user rejected apart from one that was never proposed,
 * because the request leaves out a class whose mappings were all unlinked.
 */
@ExtendWith(MockitoExtension.class)
class PropertyMappingServiceTest {

    private static final String PREFIX = "http://example.org#";

    @Mock private MigrationSessionStore migrationSessionStore;

    private SchemaMigrationContext context;
    private PropertyMappingService service;

    @BeforeEach
    void setUp() {
        context = new SchemaMigrationContext();
        lenient().when(migrationSessionStore.getContext()).thenReturn(context);
        service = new PropertyMappingService(migrationSessionStore);

        // the attribute sits on Base before and on its subclass Child after, so the detector
        // has a move to propose
        var oldModel = declareClasses();
        declareAttribute(oldModel, "Base.p", "Base");
        var newModel = declareClasses();
        declareAttribute(newModel, "Child.p", "Child");

        context.setOriginalSchema(oldModel.getGraph());
        context.setUpdatedSchema(newModel.getGraph());
        context.setDiffAfterClassConfirm(
                new ArrayList<>(
                        List.of(
                                classChange(
                                        "Base",
                                        attribute(
                                                "Base.p",
                                                "Base",
                                                SemanticResourceChangeType.DELETE)),
                                classChange(
                                        "Child",
                                        attribute(
                                                "Child.p",
                                                "Child",
                                                SemanticResourceChangeType.ADD)))));
    }

    private Model declareClasses() {
        var model = ModelFactory.createDefaultModel();
        for (var name : List.of("Base", "Child")) {
            var cls = model.createResource(PREFIX + name);
            cls.addProperty(RDF.type, RDFS.Class);
            cls.addProperty(RDFS.label, model.createLiteral(name));
            cls.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        }
        model.getResource(PREFIX + "Child")
                .addProperty(RDFS.subClassOf, model.getResource(PREFIX + "Base"));
        return model;
    }

    private void declareAttribute(Model model, String name, String domain) {
        var attribute = model.createResource(PREFIX + name);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral("p"));
        attribute.addProperty(RDFS.domain, model.createResource(PREFIX + domain));
    }

    private SemanticAttributeChange attribute(
            String iri, String domain, SemanticResourceChangeType type) {
        return SemanticAttributeChange.builder()
                .iri(PREFIX + iri)
                .label("p")
                .domain(PREFIX + domain)
                .semanticResourceChangeType(type)
                .build();
    }

    private SemanticClassChange classChange(String name, SemanticAttributeChange attributeChange) {
        return SemanticClassChange.builder()
                .iri(PREFIX + name)
                .label(name)
                .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                .attributes(new ArrayList<>(List.of(attributeChange)))
                .associations(new ArrayList<SemanticAssociationChange>())
                .enumEntries(new ArrayList<SemanticEnumEntryChange>())
                .build();
    }

    private long proposedMappings(List<ClassPropertyMappingOverview> overviews) {
        return overviews.stream()
                .flatMap(overview -> overview.getAttributes().getDeletedAndRemapped().stream())
                .filter(mapping -> !mapping.getNewResources().isEmpty())
                .count();
    }

    @Test
    void getPropertyMappings_firstVisit_proposesTheMove() {
        assertThat(proposedMappings(service.getPropertyMappings())).isEqualTo(1);
    }

    @Test
    void confirmPropertyMappings_classLeftOutOfTheRequest_losesItsMappings() {
        service.getPropertyMappings();

        // the user unlinked the only mapping, so no class carries one and the request is empty
        service.confirmPropertyMappings(List.of());

        assertThat(context.getDiffAfterClassConfirm())
                .allSatisfy(cls -> assertThat(cls.getAttributeResourceMappings()).isEmpty());
    }

    @Test
    void getPropertyMappings_afterConfirming_doesNotProposeTheRejectedMoveAgain() {
        service.getPropertyMappings();
        service.confirmPropertyMappings(List.of());

        assertThat(proposedMappings(service.getPropertyMappings())).isZero();
    }
}
