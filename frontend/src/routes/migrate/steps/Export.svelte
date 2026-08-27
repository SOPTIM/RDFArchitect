<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script>
    import { get } from "svelte/store";

    import { generateMigrationReport, generateMigrationScript } from "$lib/api/generated/index.ts";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { migrationState } from "$lib/sharedState.svelte.js";
    import { saveFile, sparqlMediaType } from "$lib/utils/fileUtils.js";

    import { goto } from "$app/navigation";

    export async function onNext() {
        await goto("/mainpage");
    }

    async function downloadMigrationScript() {
        try {
            const { data, response } = await generateMigrationScript();
            const suggestedFilename = response.headers.get(
                "content-disposition",
            );
            saveFile(data, suggestedFilename, sparqlMediaType);
        } catch (e) {
            console.error("Failed to generate script:", e);
        }
    }

    async function downloadMigrationReport(reportType) {
        const state = get(migrationState);
        try {
            const { data, response } = await generateMigrationReport({
                query: {
                    reportType: reportType,
                    originalCGMESVersion: state.cgmesVersionA,
                    updatedCGMESVersion: state.cgmesVersionB,
                },
            });
            const suggestedFilename = response.headers.get(
                "content-disposition",
            );
            saveFile(data, suggestedFilename, sparqlMediaType);
        } catch (e) {
            console.error("Failed to generate report:", e);
        }
    }
</script>

<div class="text-default-text flex h-full flex-col space-y-6 p-2">
    <InfoBox>
        In this step you can choose which artifacts you want to generate for the
        migration. You can generate a migration package containing the SPARQL
        updates for automatically migrating your data as well as SHACL shapes
        for validating your data against the new schema. You can also generate a
        markdown file based on the semantic changes configured during this
        migration process.
    </InfoBox>
    <InfoBox type="warn">
        Please note that the script generation might not be able to handle all
        edge cases yet, one such case being multiplicity changes on
        associations.
        <br />
        It is strongly recommended that you validate migrated data using the provided
        constraints (SHACL) after executing the script, and manually adjust any inconsistencies
        if necessary.
    </InfoBox>

    <div class="flex flex-col space-y-6">
        <div class="flex flex-col space-y-1">
            <div class="flex flex-col space-y-6">
                <div class="flex flex-col space-y-3">
                    <h3 class="text-base font-medium">Generate artifacts</h3>

                    <div
                        class="border-border flex items-start justify-between gap-4 rounded-lg border p-4"
                    >
                        <div class="flex min-w-0 flex-col space-y-1">
                            <span class="text-sm font-medium">
                                Migration Script
                            </span>
                            <span class="text-text-subtle text-sm">
                                SPARQL UPDATE script for automatically migrating
                                your data to the new schema as well as SHACL shapes for validating the data.
                            </span>
                        </div>
                        <div class="w-48 shrink-0">
                            <ButtonControl
                                callOnClick={downloadMigrationScript}
                            >
                                Download script
                            </ButtonControl>
                        </div>
                    </div>

                    <div
                        class="border-border flex items-start justify-between gap-4 rounded-lg border p-4"
                    >
                        <div class="flex min-w-0 flex-col space-y-1">
                            <span class="text-sm font-medium">
                                Summary Report
                            </span>
                            <span class="text-text-subtle text-sm">
                                Report containing only the directly changed
                                classes. If another class would inherit a change
                                it is listed underneath the superclass.
                            </span>
                        </div>
                        <div class="w-48 shrink-0">
                            <ButtonControl
                                callOnClick={() =>
                                    downloadMigrationReport("SUMMARY")}
                            >
                                Download report
                            </ButtonControl>
                        </div>
                    </div>

                    <div
                        class="border-border flex items-start justify-between gap-4 rounded-lg border p-4"
                    >
                        <div class="flex min-w-0 flex-col space-y-1">
                            <span class="text-sm font-medium">
                                Detailed Report
                            </span>
                            <span class="text-text-subtle text-sm">
                                A report containing all affected classes.
                                Changes inherited from a superclass are included
                                again on every inheriting class.
                            </span>
                        </div>
                        <div class="w-48 shrink-0">
                            <ButtonControl
                                callOnClick={() =>
                                    downloadMigrationReport("DETAILED")}
                            >
                                Download report
                            </ButtonControl>
                        </div>
                    </div>
                </div>

                <!-- Next Steps ... -->
            </div>
        </div>

        <div>
            <h3 class="mb-3 text-base font-medium">Next Steps</h3>
            <ol class="ml-2 list-inside list-decimal space-y-2 text-sm">
                <li>
                    <span class="font-medium">Verify old data:</span>
                    Validate your source data against the old schema's constraints
                    (SHACL) to ensure data quality before migration
                </li>
                <li>
                    <span class="font-medium">Download the script:</span>
                    Use the button above to generate and download the migration script
                </li>
                <li>
                    <span class="font-medium">Apply the update:</span>
                    Execute the SPARQL UPDATE script on your schema
                </li>
                <li>
                    <span class="font-medium">Verify the resulting data:</span>
                    Validate the migrated data against the new schema's constraints
                    (SHACL) to ensure successful migration
                </li>
            </ol>
        </div>
    </div>
</div>
