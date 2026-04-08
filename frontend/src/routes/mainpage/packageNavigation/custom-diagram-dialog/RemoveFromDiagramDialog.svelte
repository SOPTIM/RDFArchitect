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
    import { faExclamation } from "@fortawesome/free-solid-svg-icons";

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";

    let {
        showDialog = $bindable(),
        datasetName,
        graphUri,
        diagramId,
        cls
    } = $props();
    
    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    async function removeFromDiagram() {
        await bec.removeFromCustomDiagram(datasetName, graphUri, diagramId, cls.uuid);
    }
</script>

<ActionDialog
    bind:showDialog
    size="w-full max-w-lg"
    primaryLabel="Remove Class from Diagram"
    onPrimary={removeFromDiagram}
    primaryVariant="danger"
    title={cls?.label ? `Remove class "${cls.label}"?` : "Remove class?"}
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-2/3 text-sm leading-relaxed">
            This removes the class from this custom diagram. It will still be accessible from its package.
        </p>
    </div>
</ActionDialog>
