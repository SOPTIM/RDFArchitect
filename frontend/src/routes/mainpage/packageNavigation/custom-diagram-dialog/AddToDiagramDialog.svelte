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
    import { BackendConnection } from "$lib/api/backend.js";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        classes,
        graph
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let graphURI = $state(null);
    let selectedDiagram = $state(null);

    let diagramList = $state([]);
    let graphNames = $state([]);

    let disableSubmit = $derived(
        !graphURI ||
        !selectedDiagram
    );

    $effect(() => {
        if (!graphURI) {
            selectedDiagram = null;
            diagramList = [];
            return;
        }
        getCustomDiagrams(graphURI);
    });

    async function onOpen() {
        graphNames = await getGraphNames();
        graphURI = graph;
    }

    async function getCustomDiagrams(graphURI) {
        const res = await bec.getCustomDiagrams(lockedDatasetName, graphURI);
        diagramList = await res.json();
    }

    async function getGraphNames() {
        const res = await bec.getGraphNames(lockedDatasetName);
        return await res.json();
    }

    function onClose() {
        graphURI = null;
        selectedDiagram = null;
    }

    async function addToDiagram() {
        const classesToAdd = classes.map(cls => ({
            uuid: cls.uuid,
            graphUri: graphURI,
        }));
        await bec.addToCustomDiagram(
            lockedDatasetName,
            graphURI,
            selectedDiagram.diagramId,
            classesToAdd,
        );
        forceReloadTrigger.trigger();
    }

    function getUri(graph) {
        return (!graph.prefix ? "" : graph.prefix) + graph.suffix;
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Add to Diagram"
    onPrimary={addToDiagram}
    disablePrimary={disableSubmit}
    title="Add to Diagram"
>
    <div class="mx-2 flex h-full flex-col">
        <label for="graph-select" class="mt-3 mb-1 block text-sm">
            Graph
        </label>
        <SelectEditControl
            id="graph-select"
            bind:value={graphURI}
            options={graphNames}
            placeholder={"Select graph"}
            getOptionValue={getUri}
            getOptionLabel={g => g.suffix}
        />
        <label for="diagram-select" class="mt-3 mb-1 block text-sm">
            Diagram
        </label>
        <SelectEditControl
            id="diagram-select"
            bind:value={selectedDiagram}
            options={diagramList}
            disabled={!graphURI}
            placeholder={graphURI
                ? "Select diagram"
                : "Select a graph first"}
            getOptionLabel={diagram => diagram.name}
        />
    </div>
</ActionDialog>
