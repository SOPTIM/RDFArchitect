<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import { onMount } from "svelte";
    import { v4 as uuidv4 } from "uuid";

    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { datasetStore } from "$lib/stores/DatasetStore.ts";
    import { graphStore } from "$lib/stores/GraphStore.ts";

    let {
        dataset = $bindable(),
        graph = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        allowSelectionOfReadonlyDatasets = true,
        displayAsCard = true
    } = $props();

    const datasetSelectId = `datasetSelect-${uuidv4()}`;
    const graphSelectId = `graphSelect-${uuidv4()}`;

    let graphNames = $state([]);

    const datasetLocked = $derived(lockedDatasetName !== undefined);
    const graphLocked = $derived(lockedGraphUri !== undefined);

    const graphSelectDisabled = $derived(graphLocked || !dataset);

    $effect(async () => {
        if (datasetLocked) return;
        if (!dataset) {
            graph = graphLocked ? lockedGraphUri : null;
            graphNames = [];
            return;
        }

        await graphStore.load(dataset)
        graphNames = graphStore.getGraphs(dataset);
        const valid = graphNames.some(graphName => getUri(graphName) === graph);
        if (!valid && !graphLocked) {
            graph = null;
        }
    });

    onMount(async () => {
        await datasetStore.load();
        if (datasetLocked) dataset = lockedDatasetName;
        if (graphLocked) graph = lockedGraphUri;

        if (!datasetLocked && dataset && !allowSelectionOfReadonlyDatasets) {
            const selectedDataset = $datasetStore.data.find(
                option => option.label === dataset
            );
            if (!selectedDataset || selectedDataset.readonly) {
                dataset = null;
            }
        }

        if (dataset) {
            await graphStore.load(dataset);
            graphNames = graphStore.getGraphs(dataset);
        } else {
            graphNames = [];
        }
    });

    function getUri(graph) {
        return (!graph.prefix ? "" : graph.prefix) + graph.suffix;
    }
</script>

<div
    class={displayAsCard
        ? "border-border bg-background-subtle rounded border p-3"
        : ""}
>
    <label for={datasetSelectId} class="mb-1 block text-sm">Dataset</label>
    <SelectEditControl
        id={datasetSelectId}
        bind:value={dataset}
        options={$datasetStore.data}
        getOptionIsDisabled={dataset =>
            !allowSelectionOfReadonlyDatasets && dataset.readonly}
        getOptionValue={dataset => dataset.label}
        getOptionLabel={dataset =>
            dataset.label + (dataset.readonly ? " (readonly)" : "")}
        disabled={datasetLocked || ($datasetStore.data?.length ?? 0) === 0}
        placeholder="Select dataset"
        onchange={() => (graph = null)}
    />

    <label for={graphSelectId} class="mt-3 mb-1 block text-sm">
        Schema (RDFS)
    </label>
    <SelectEditControl
        id={graphSelectId}
        bind:value={graph}
        options={graphNames}
        disabled={graphSelectDisabled}
        placeholder={dataset ? "Select schema" : "Select a dataset first"}
        getOptionValue={getUri}
        getOptionLabel={g => g.suffix}
    />
</div>
