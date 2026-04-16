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
    import {
        faClockRotateLeft,
        faCodeBranch,
        faRightLeft,
        faEye,
    } from "@fortawesome/free-solid-svg-icons";

    import { Menubar } from "$lib/components/bitsui/menubar";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import CompareDialog from "../../compare/CompareDialog.svelte";
    import SHACLFullViewDialog from "../../shacl/SHACLFullViewDialog.svelte";

    import { goto } from "$app/navigation";
    let showSHACLFullViewDialog = $state(false);
    let showCompareDialog = $state(false);

    let selectedDataset = $derived(editorState.selectedDataset.getValue());
    let selectedGraph = $derived(editorState.selectedGraph.getValue());
    let hasGraphSelected = $derived(!!selectedDataset && selectedGraph);

    $effect(async () => {
        editorState.selectedPackageUUID.subscribe();
        editorState.selectedClassUUID.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDataset.subscribe();
        forceReloadTrigger.subscribe();
    });
</script>

<Menubar.Menu value="view">
    <Menubar.Trigger>View</Menubar.Trigger>
    <Menubar.Content side="bottom" sideOffset={8}>
        <Menubar.Item.Button
            onSelect={() => goto("/changelog")}
            faIcon={faClockRotateLeft}
        >
            Changelog
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => (showCompareDialog = true)}
            faIcon={faCodeBranch}
        >
            Compare Schemas
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => goto("/migrate")}
            faIcon={faRightLeft}
        >
            Migrate Schema
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => (showSHACLFullViewDialog = true)}
            disabled={!hasGraphSelected}
            faIcon={faEye}
        >
            Constrains (SHACL)
        </Menubar.Item.Button>
    </Menubar.Content>
</Menubar.Menu>
<SHACLFullViewDialog bind:showDialog={showSHACLFullViewDialog} />
<CompareDialog bind:showDialog={showCompareDialog} />
