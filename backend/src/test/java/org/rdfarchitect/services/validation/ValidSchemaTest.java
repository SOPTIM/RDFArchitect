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

import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;

public class ValidSchemaTest extends SchemaValidationTestBase {

    @Test
    void validateSchema_fullyValidSchema_reportsValidWithoutErrors() {
        var report = service.validateSchema(validSchema().getGraph(), CGMESVersion.V3_0);

        assertThat(report.isValid()).isTrue();
        assertThat(errorsOf(report)).isEmpty();
    }

    @Test
    void validateSchema_fullyValidSchema_onlyEmitsInfoForMissingOptionalFields() {
        var report = service.validateSchema(validSchema().getGraph(), CGMESVersion.V3_0);

        // Everything that is not an ERROR here must be an INFO about optional header fields.
        assertThat(report.getIssues())
                .allMatch(i -> i.getSeverity() == SchemaValidationIssueDTO.Severity.INFO)
                .allMatch(i -> i.getMessage().contains("Optional profile header field is not set"));
    }
}
