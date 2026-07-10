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

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

public class PackageTest extends SchemaValidationTestBase {

    @Test
    void validateSchema_packageWithoutLabel_reportsError() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);
        var pkg = model.createResource(NS + "package");
        pkg.addProperty(RDF.type, CIMS.classCategory);

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "package",
                                "missing rdfs:label"))
                .isTrue();
    }
}
