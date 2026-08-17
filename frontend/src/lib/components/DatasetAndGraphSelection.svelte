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
        displayAsCard = true,
    } = $props();

    const datasetSelectId = `datasetSelect-${uuidv4()}`;
    const graphSelectId = `graphSelect-${uuidv4()}`;

    let datasets = $state([]);
    let graphs = $state([]);

    const datasetLocked = $derived(lockedDatasetName !== undefined);
    const graphLocked = $derived(lockedGraphUri !== undefined);

    const graphSelectDisabled = $derived(graphLocked || !dataset);

    $effect(async () => {
        if (datasetLocked) return;
        if (!dataset) {
            graph = graphLocked ? lockedGraphUri : null;
            graphs = [];
            return;
        }

        graphs = (await graphStore.getGraphs(dataset)) ?? [];
        const valid = graphs.some(graphName => getUri(graphName) === graph);
        if (!valid && !graphLocked) {
            graph = null;
        }
    });

    onMount(async () => {
        datasets = (await datasetStore.getDatasets()) ?? [];
        if (datasetLocked) dataset = lockedDatasetName;
        if (graphLocked) graph = lockedGraphUri;

        if (!datasetLocked && dataset && !allowSelectionOfReadonlyDatasets) {
            const selectedDataset = datasets.find(
                option => option.label === dataset,
            );
            if (!selectedDataset || selectedDataset.readOnly) {
                dataset = null;
            }
        }

        if (dataset) {
            graphs = await graphStore.getGraphs(dataset);
        } else {
            graphs = [];
        }
    });

    /**
     * Full URI of a graph as it comes from the backend: a GraphDTO holding the
     * URI next to its dcat:keyword. A bare URI is still accepted so that a
     * locked graph can be passed in as is.
     */
    function getUri(graph) {
        const uri = graph.uri ?? graph;
        return (uri.prefix ?? "") + (uri.suffix ?? "");
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
        options={datasets}
        getOptionIsDisabled={dataset =>
            !allowSelectionOfReadonlyDatasets && dataset.readOnly}
        getOptionValue={dataset => dataset.label}
        getOptionLabel={dataset =>
            dataset.label + (dataset.readOnly ? " (readonly)" : "")}
        disabled={datasetLocked || (datasets?.length ?? 0) === 0}
        placeholder="Select dataset"
        onchange={() => (graph = null)}
    />

    <label for={graphSelectId} class="mt-3 mb-1 block text-sm">
        Schema (RDFS)
    </label>
    <SelectEditControl
        id={graphSelectId}
        bind:value={graph}
        options={graphs}
        disabled={graphSelectDisabled}
        placeholder={dataset ? "Select schema" : "Select a dataset first"}
        getOptionValue={getUri}
        getOptionLabel={g => g.keyword ?? g.uri.suffix}
    />
</div>
