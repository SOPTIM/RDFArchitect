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
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import { editorState } from "$lib/sharedState.svelte.js";

    import Changes from "./Changes.svelte";
    import Navigation from "./Navigation.svelte";

    import { page } from "$app/state";

    let expandedStateMap = $state({});

    /**
     * The graph the changelog is held to, or `null` for the whole workspace.
     *
     * Kept here rather than in the editor's selection: which workspace is open decides what is
     * loaded, but holding the log to one of its graphs is a view setting, and reading a changelog
     * should not move the editor on to another graph behind the user's back. It starts at the graph
     * they were last in, unless whoever opened the page asked for the workspace as a whole.
     */
    let graphUri = $state(
        page.url.searchParams.get("scope") === "workspace"
            ? null
            : (editorState.selectedGraph.getValue() ?? null),
    );

    function getExpanded(key) {
        return !!expandedStateMap[key];
    }

    function setExpanded(key, value) {
        expandedStateMap[key] = !!value;
    }

    /**
     * Cleans the expandedStateMap by removing keys that a new changelog does not contain.
     * @param {Array} changelog - The new changelog array.
     */
    function cleanExpandedStateMap(changelog) {
        const validKeys = new Set();

        for (const change of changelog) {
            validKeys.add(`${change.changeId}::row`);
            validKeys.add(`${change.changeId}::additions`);
            validKeys.add(`${change.changeId}::deletions`);
        }

        for (const key in expandedStateMap) {
            if (!validKeys.has(key)) {
                delete expandedStateMap[key];
            }
        }
    }
</script>

<Splitpanes theme="opencgmes-theme" class="flex h-full">
    <Pane size={18} maxSize={30} class="bg-window-background">
        <Navigation {graphUri} onSelectGraph={uri => (graphUri = uri)} />
    </Pane>
    <Pane size={82} class="bg-window-background h-full">
        <Changes
            {getExpanded}
            {setExpanded}
            {cleanExpandedStateMap}
            {graphUri}
        />
    </Pane>
</Splitpanes>
