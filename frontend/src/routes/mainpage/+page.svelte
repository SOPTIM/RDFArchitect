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
    import { onMount } from "svelte";
    import { Pane, Splitpanes } from "svelte-splitpanes";
    import { validate } from "uuid";

    import { resolveIri as fetchResolveIRI } from "$lib/api/generated/index";
    import { DiagramType, editorState } from "$lib/sharedState.svelte.js";

    import PackageNavigation from "./packageNavigation/packageNavigation.svelte";
    import PackageWindow from "./packageWindow.svelte";

    onMount(() => {
        parseModelSelectionUrlParameters();
    });

    async function parseModelSelectionUrlParameters() {
        const url = new URL(window.location.href);
        const queryParams = new URLSearchParams(url.search);
        const dataset = queryParams.get("dataset") || null;
        const graph = queryParams.get("graph") || null;
        let pack = queryParams.get("package") || null;
        editorState.selectedDataset.updateValue(dataset);
        editorState.selectedGraph.updateValue(graph);
        if (!dataset || !graph || !pack) return;
        if (pack !== "default" && !validate(pack)) {
            pack = await resolveIRI(dataset, graph, pack);
        }
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: pack,
        });
    }

    async function resolveIRI(dataset, graph, iri) {
        fetchResolveIRI({
            path: { dataset: dataset, graph: graph, iri: iri },
        }).then(res => res.data);
    }
</script>

<div class="h-full w-full">
    <Splitpanes theme="opencgmes-theme" class="flex h-full">
        <Pane
            size={18}
            maxSize={30}
            class="bg-window-background rounded-xs border-none "
        >
            <div class="h-full">
                <PackageNavigation />
            </div>
        </Pane>
        <Pane size={82} class="bg-window-background rounded-xs border-none">
            <PackageWindow />
        </Pane>
    </Splitpanes>
</div>
