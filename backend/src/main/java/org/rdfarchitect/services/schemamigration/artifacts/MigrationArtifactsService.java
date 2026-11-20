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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.services.validation.SchemaValidationReportToMarkdownService;
import org.rdfarchitect.services.validation.SchemaValidationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the confirmed migration changes into the artefacts handed to the user: the SPARQL migration
 * script and the Markdown migration report, the latter prefixed with the validation reports of both
 * schemas.
 */
@Service
@RequiredArgsConstructor
public class MigrationArtifactsService
        implements GenerateMigrationScriptUseCase, GenerateMigrationReportUseCase {

    private final MigrationSessionStore migrationSessionStore;
    private final MigrationScriptBuilder migrationScriptBuilder;
    private final MigrationReportBuilder migrationReportBuilder;
    private final SchemaValidationService validationService;
    private final SchemaValidationReportToMarkdownService schemaValidationReportToMarkdownService;

    @Override
    public String generateMigrationScript() {
        var changes = migrationSessionStore.getContext().getDiffAfterDefaultValueConfirm();
        return migrationScriptBuilder.generateMigrationScript(changes);
    }

    @Override
    public String generateDetailedMigrationReport(
            CGMESVersion originalCGMESVersion, CGMESVersion updatedCGMESVersion) {
        var context = migrationSessionStore.getContext();
        var changes = copyOfConfirmedChanges();

        return validationReports(originalCGMESVersion, updatedCGMESVersion)
                + migrationReportBuilder.generateDetailedMigrationReport(
                        changes,
                        context.getOriginalSchema(),
                        context.getUpdatedSchema(),
                        context.isIgnorePrefixes());
    }

    @Override
    public String generateSummaryMigrationReport(
            CGMESVersion originalCGMESVersion, CGMESVersion updatedCGMESVersion) {
        var context = migrationSessionStore.getContext();
        var changes = copyOfConfirmedChanges();

        return validationReports(originalCGMESVersion, updatedCGMESVersion)
                + migrationReportBuilder.generateSummaryMigrationReport(
                        changes,
                        context.getOriginalSchema(),
                        context.getUpdatedSchema(),
                        context.isIgnorePrefixes());
    }

    private List<SemanticClassChange> copyOfConfirmedChanges() {
        var changes = migrationSessionStore.getContext().getDiffAfterDefaultValueConfirm();
        var copies = new ArrayList<SemanticClassChange>();
        for (var change : changes) {
            copies.add(new SemanticClassChange(change));
        }
        return copies;
    }

    private String validationReports(
            CGMESVersion originalCGMESVersion, CGMESVersion updatedCGMESVersion) {
        var context = migrationSessionStore.getContext();

        var originalValidationReport =
                validationService.validateSchema(context.getOriginalSchema(), originalCGMESVersion);
        var updatedValidationReport =
                validationService.validateSchema(context.getUpdatedSchema(), updatedCGMESVersion);

        return schemaValidationReportToMarkdownService.convertToMarkdown(
                        originalValidationReport, "Validation Report Original Schema")
                + schemaValidationReportToMarkdownService.convertToMarkdown(
                        updatedValidationReport, "Validation Report Updated Schema");
    }
}
