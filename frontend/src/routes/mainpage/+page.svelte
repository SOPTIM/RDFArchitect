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

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { DiagramType, editorState } from "$lib/sharedState.svelte.js";
    import { resolveClassTarget } from "$lib/utils/deep-link.js";
    import { navigateToClass } from "$lib/utils/model-navigation.js";

    import PackageNavigation from "./packageNavigation/packageNavigation.svelte";
    import PackageWindow from "./packageWindow.svelte";

    const backend = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    onMount(() => {
        parseModelSelectionUrlParameters();
    });

    async function parseModelSelectionUrlParameters() {
        const url = new URL(window.location.href);
        const queryParams = new URLSearchParams(url.search);
        const dataset = queryParams.get("dataset") || null;
        const graph = queryParams.get("graph") || null;
        let pack = queryParams.get("package") || null;
        const classRef = queryParams.get("class") || null;

        // A class deep link (IRI or uuid) selects the class and its package diagram; dataset and
        // graph merely narrow the lookup. Falls through to the plain selection when not found.
        if (classRef && (await openClassFromUrl(dataset, graph, classRef))) {
            return;
        }

        editorState.selectedDataset.updateValue(dataset);
        editorState.selectedGraph.updateValue(graph);
        if (!dataset || !graph || !pack) return;
        if (pack !== "default" && !validate(pack)) {
            pack = await resolveIRI(dataset, graph, pack);
            if (!pack) return;
        }
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: pack,
        });
    }

    async function openClassFromUrl(dataset, graph, classRef) {
        const target = await resolveClassTarget(backend, {
            dataset,
            graph,
            classRef,
        });
        if (!target) {
            toastStore.error(
                "Class not found",
                `No schema in this session contains "${classRef}".`,
            );
            return false;
        }
        navigateToClass(target);
        return true;
    }

    async function resolveIRI(dataset, graph, iri) {
        const res = await backend.resolveIri(dataset, graph, iri);
        return res.ok ? await res.text() : null;
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
