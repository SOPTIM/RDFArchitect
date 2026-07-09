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

package org.rdfarchitect.services.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

public class AssociationTest extends SchemaValidationTestBase {

    private Model modelWithTwoClasses() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);
        var pkg = addPackage(model);
        addClass(model, "ClassA", pkg);
        addClass(model, "ClassB", pkg);
        return model;
    }

    @Test
    void validateSchema_validAssociationWithExistingTarget_reportsNoAssociationError() {
        var model = modelWithTwoClasses();
        addAssociation(
                model,
                "ClassA.classB",
                model.getResource(NS + "ClassA"),
                model.getResource(NS + "ClassB"));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        report.getIssues().stream()
                                .anyMatch(i -> i.getMessage().contains("Association")))
                .isFalse();
    }

    @Test
    void validateSchema_associationTargetClassMissing_reportsError() {
        var model = modelWithTwoClasses();
        // Target ClassC is referenced but never declared as rdfs:Class.
        addAssociation(
                model,
                "ClassA.classC",
                model.getResource(NS + "ClassA"),
                model.createResource(NS + "ClassC"));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.classC",
                                "Association target class does not exist"))
                .isTrue();
    }

    @Test
    void validateSchema_associationRangeIsLiteral_reportsError() {
        var model = modelWithTwoClasses();
        var association = model.createResource(NS + "ClassA.broken");
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFS.label, model.createLiteral("broken", "en"));
        association.addProperty(RDFS.domain, model.getResource(NS + "ClassA"));
        association.addProperty(RDFS.range, model.createLiteral("not-a-resource"));
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        association.addProperty(CIMS.associationUsed, "Yes");
        association.addProperty(
                CIMS.inverseRoleName, model.createResource(NS + "ClassA.broken.inverse"));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.broken",
                                "target (rdfs:range) is not a resource"))
                .isTrue();
    }

    @Test
    void validateSchema_associationMissingRange_reportsError() {
        var model = modelWithTwoClasses();
        // isAssociation requires inverseRoleName + associationUsed; range is what we omit.
        var association = model.createResource(NS + "ClassA.noRange");
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFS.label, model.createLiteral("noRange", "en"));
        association.addProperty(RDFS.domain, model.getResource(NS + "ClassA"));
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        association.addProperty(CIMS.associationUsed, "Yes");
        association.addProperty(
                CIMS.inverseRoleName, model.createResource(NS + "ClassA.noRange.inverse"));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.noRange",
                                "missing rdfs:range (target class)"))
                .isTrue();
    }
}
