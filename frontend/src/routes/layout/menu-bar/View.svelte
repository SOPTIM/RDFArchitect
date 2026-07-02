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
        faCircleCheck,
    } from "@fortawesome/free-solid-svg-icons";
    import { onDestroy, onMount } from "svelte";

    import { Menubar } from "$lib/components/bitsui/menubar";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import CompareDialog from "../../compare/CompareDialog.svelte";
    import SHACLFullViewDialog from "../../shacl/SHACLFullViewDialog.svelte";
    import ValidationDialog from "../../validate/ValidationDialog.svelte";

    import { goto } from "$app/navigation";

    const shortcutsUnregister = [];

    let showSHACLFullViewDialog = $state(false);
    let showCompareDialog = $state(false);
    let showValidationDialog = $state(false);

    let selectedDataset = $derived(editorState.selectedDataset.getValue());
    let selectedGraph = $derived(editorState.selectedGraph.getValue());
    let hasGraphSelected = $derived(!!selectedDataset && selectedGraph);

    $effect(async () => {
        editorState.selectedDiagram.subscribe();
        editorState.selectedClass.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDataset.subscribe();
        forceReloadTrigger.subscribe();
    });

    onMount(() => {
        shortcutsUnregister.push(
            shortcutStore.register("changelog", ["ctrl", "shift", "h"], () =>
                goto("/changelog"),
            ),
            shortcutStore.register(
                "compare",
                ["ctrl", "shift", "c"],
                () => (showCompareDialog = true),
            ),
            shortcutStore.register("migrate", ["ctrl", "shift", "m"], () =>
                goto("/migrate"),
            ),
            shortcutStore.register(
                "shaclFullView",
                ["ctrl", "shift", "l"],
                () => {
                    if (hasGraphSelected) showSHACLFullViewDialog = true;
                },
            ),
            shortcutStore.register(
                "validation",
                ["ctrl", "shift", "d"],
                () => (showValidationDialog = true),
            ),
        );
    });

    onDestroy(() => {
        shortcutsUnregister.forEach(unregister => unregister());
    });
</script>

<Menubar.Menu value="view">
    <Menubar.Trigger>View</Menubar.Trigger>
    <Menubar.Content side="bottom" sideOffset={8}>
        <Menubar.Item.Button
            onSelect={() => goto("/changelog")}
            faIcon={faClockRotateLeft}
            altText="Ctrl+Shift+H"
        >
            Changelog
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => (showCompareDialog = true)}
            faIcon={faCodeBranch}
            altText="Ctrl+Shift+C"
        >
            Compare Schemas
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => goto("/migrate")}
            faIcon={faRightLeft}
            altText="Ctrl+Shift+M"
        >
            Migrate Schema
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => (showValidationDialog = true)}
            faIcon={faCircleCheck}
            altText="Ctrl+Shift+D"
        >
            Validate Schema
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => (showSHACLFullViewDialog = true)}
            disabled={!hasGraphSelected}
            faIcon={faEye}
            altText="Ctrl+Shift+L"
        >
            Constraints (SHACL)
        </Menubar.Item.Button>
    </Menubar.Content>
</Menubar.Menu>
<SHACLFullViewDialog bind:showDialog={showSHACLFullViewDialog} />
<CompareDialog bind:showDialog={showCompareDialog} />
<ValidationDialog bind:showDialog={showValidationDialog} />
