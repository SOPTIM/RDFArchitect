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
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        forceReloadTrigger,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        graphUri,
        diagramId,
        classIds = [],
        classLabels = [],
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const isSingle = $derived(classIds.length === 1);
    const subject = $derived(
        isSingle
            ? `class "${classLabels[0] ?? ""}"`
            : `${classIds.length} classes`,
    );

    async function removeFromDiagram() {
        try {
            const res = graphUri
                ? await bec.removeFromCustomGraphDiagram(
                      lockedDatasetName,
                      graphUri,
                      diagramId,
                      classIds,
                  )
                : await bec.removeFromCustomDatasetDiagram(
                      lockedDatasetName,
                      diagramId,
                      classIds,
                  );
            if (res && res.ok === false) {
                toastStore.error(
                    "Remove failed",
                    `Could not remove ${subject} from the diagram.`,
                );
                return;
            }
            multiSelectState.clear();
            toastStore.success(
                isSingle ? "Class removed" : "Classes removed",
                `${isSingle ? `"${classLabels[0] ?? ""}"` : `${classIds.length} classes`} ${isSingle ? "was" : "were"} removed from the diagram.`,
            );
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    size="w-full max-w-lg"
    primaryLabel={isSingle
        ? "Remove Class from Diagram"
        : "Remove Classes from Diagram"}
    onPrimary={removeFromDiagram}
    primaryVariant="danger"
    title={`Remove ${subject}?`}
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-2/3 text-sm leading-relaxed">
            This removes the {isSingle ? "class" : "classes"} from this custom diagram.
            {isSingle ? "It" : "They"} will still be accessible from
            {isSingle ? "its" : "their"} package.
        </p>
    </div>
</ActionDialog>
