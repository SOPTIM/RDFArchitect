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
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { saveFile, sparqlMediaType } from "$lib/utils/fileUtils.js";

    import { goto } from "$app/navigation";

    export async function onNext() {
        await goto("/mainpage");
    }

    async function generateMigrationScript() {
        try {
            const response = await fetchScript();
            const suggestedFilename = response.headers.get(
                "content-disposition",
            );
            const blob = await response.blob();
            saveFile(blob, suggestedFilename, sparqlMediaType);
        } catch (e) {
            console.error("Failed to generate script:", e);
        }
    }

    async function fetchScript() {
        return fetch(PUBLIC_BACKEND_URL + "/migrations/export", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
        });
    }
</script>

<div class="text-default-text flex h-full flex-col space-y-6 p-2">
    <InfoBox type="warn">
        Please note that the script generation might not be able to handle all
        edge cases yet, one such case being multiplicity changes on
        associations.
        <br />
        It is strongly recommended that you validate migrated data using the provided
        constrains (SHACL) after executing the script, and manually adjust any inconsistencies
        if necessary.
    </InfoBox>

    <div class="flex flex-col space-y-6">
        <div>
            <h3 class="mb-3 text-base font-medium">Migration Script</h3>
            <p class="mb-4 text-sm">
                The migration script contains SPARQL UPDATE queries that will
                transform your data from the source schema to the target schema
                based on the mappings and defaults you've configured.
            </p>
            <div class="w-64">
                <ButtonControl callOnClick={generateMigrationScript}>
                    Generate and download script
                </ButtonControl>
            </div>
        </div>

        <div>
            <h3 class="mb-3 text-base font-medium">Next Steps</h3>
            <ol class="ml-2 list-inside list-decimal space-y-2 text-sm">
                <li>
                    <span class="font-medium">Verify old data:</span>
                    Validate your source data against the old schema's constrains
                    (SHACL) to ensure data quality before migration
                </li>
                <li>
                    <span class="font-medium">Download the script:</span>
                    Use the button above to generate and download the migration script
                </li>
                <li>
                    <span class="font-medium">Apply the update:</span>
                    Execute the SPARQL UPDATE script on your dataset
                </li>
                <li>
                    <span class="font-medium">Verify the resulting data:</span>
                    Validate the migrated data against the new schema's constrains
                    (SHACL) to ensure successful migration
                </li>
            </ol>
        </div>
    </div>
</div>
