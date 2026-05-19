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
    import "../app.css";
    import { onMount } from "svelte";

    import {
        undo,
        fetchCanUndo,
        redo,
        fetchCanRedo,
    } from "$lib/actions/versionControlActions.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { Menubar } from "$lib/components/bitsui/menubar";
    import BrandLogo from "$lib/components/BrandLogo.svelte";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";

    import {
        copyState,
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";
    import Edit from "./layout/menu-bar/Edit.svelte";
    import File from "./layout/menu-bar/File.svelte";
    import Help from "./layout/menu-bar/Help.svelte";
    import View from "./layout/menu-bar/View.svelte";
    import { saveCopyClass } from "./mainpage/packageNavigation/save-copy-class-to-backend.js";
    import Searchbar from "./Searchbar.svelte";

    import { goto } from "$app/navigation";
    import { page } from "$app/state";

    /** @type {{children?: import("svelte").Snippet}} */
    let { children } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let canUndo = $state(false);
    let canRedo = $state(false);
    let menubarValue = $state(undefined);

    let isDatasetReadOnly = $state(false);
    let canCopyClass = $derived(editorState.selectedClassUUID.getValue());
    let canPasteClass = $derived(
        !isDatasetReadOnly &&
            editorState.selectedDataset.getValue() &&
            editorState.selectedGraph.getValue() &&
            editorState.selectedPackageUUID.getValue() &&
            copyState.datasetName.getValue() &&
            copyState.graphURI.getValue() &&
            copyState.classUUID.getValue(),
    );

    let selectedDataset = $derived(editorState.selectedDataset.getValue());

    $effect(async () => {
        editorState.selectedPackageUUID.subscribe();
        editorState.selectedClassUUID.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDataset.subscribe();
        forceReloadTrigger.subscribe();
        await fetchUndoRedo();
        isDatasetReadOnly = selectedDataset
            ? await isReadOnly(selectedDataset)
            : false;
    });

    onMount(() => {
        loadSnapshot();
    });

    async function requestEnableEditing() {
        if (!selectedDataset || !isDatasetReadOnly) {
            return;
        }
        await bec.enableEditing(selectedDataset);
        forceReloadTrigger.trigger();
        editorState.selectedClassUUID.trigger();
        editorState.selectedPackageUUID.trigger();
        isDatasetReadOnly = false;
    }

    async function loadSnapshot() {
        const base64Param = page.url.searchParams.get("snapshot");
        if (base64Param) {
            const res = await bec.loadSnapshot(base64Param);
            if (res.ok) {
                await goto("/mainpage");
            }
        }
    }

    async function fetchUndoRedo() {
        canUndo = await fetchCanUndo();
        canRedo = await fetchCanRedo();
    }

    async function isReadOnly(datasetName) {
        const res = await bec.isReadOnly(datasetName);
        return await res.json();
    }

    async function reload() {
        await fetchUndoRedo();
        editorState.selectedDataset.trigger();
        editorState.selectedGraph.trigger();
        editorState.selectedClassUUID.trigger();
        forceReloadTrigger.trigger();
    }

    function navigateHome() {
        editorState.reset();
        goto("/mainpage");
    }

    function copyClass() {
        copyState.classUUID.updateValue(
            editorState.selectedClassUUID.getValue(),
        );
        copyState.graphURI.updateValue(
            editorState.selectedClassGraph.getValue(),
        );
        copyState.datasetName.updateValue(
            editorState.selectedClassDataset.getValue(),
        );
    }

    async function getPackage(datasetName, graphURI, packageUUID) {
        if (!datasetName || !graphURI) {
            return [];
        }
        const res = await bec.getPackage(datasetName, graphURI, packageUUID);
        return await res.json();
    }

    async function pasteClass(
        copyAsAbstract,
        copyAttributes,
        copyAssociations,
    ) {
        let packageDTO =
            editorState.selectedPackageUUID.getValue() === "default"
                ? null
                : await getPackage(
                      editorState.selectedDataset.getValue(),
                      editorState.selectedGraph.getValue(),
                      editorState.selectedPackageUUID.getValue(),
                  );
        await saveCopyClass(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            packageDTO,
            copyAsAbstract,
            copyAttributes,
            copyAssociations,
        );
    }

    async function handleKeydown(event) {
        if (event.key === "Escape") {
            if (event.defaultPrevented) {
                return;
            }
            eventStack.executeNewestEvent();
            return;
        }

        const target = event.target;
        if (
            target instanceof HTMLElement &&
            (target.isContentEditable ||
                target.tagName === "INPUT" ||
                target.tagName === "TEXTAREA" ||
                target.tagName === "SELECT")
        ) {
            return;
        }

        if (!(event.ctrlKey || event.metaKey)) {
            return;
        }

        switch (event.key.toLowerCase()) {
            case "z":
                event.preventDefault();
                if (event.shiftKey) {
                    if (canRedo && (await redo())) await reload();
                } else {
                    if (canUndo && (await undo())) await reload();
                }
                break;
            case "y":
                event.preventDefault();
                if (canRedo && (await redo())) await reload();
                break;
            case "c":
                event.preventDefault();
                if (canCopyClass) copyClass();
                break;
            case "v":
                event.preventDefault();
                if (canPasteClass) {
                    if (event.shiftKey && event.altKey) {
                        await pasteClass(true, false, false);
                    } else if (event.shiftKey) {
                        await pasteClass(false, false, true);
                    } else if (event.altKey) {
                        await pasteClass(false, true, false);
                    } else {
                        await pasteClass(false, true, true);
                    }
                }
                break;
        }
    }
</script>

<svelte:window onkeydown={handleKeydown} />
<div class="fixed top-0 left-0 flex h-screen max-h-screen w-screen flex-col">
    {#if page.url.pathname !== "/"}
        <nav
            class="toolbar-surface text-default-text flex h-12 min-h-12 w-full items-center"
        >
            <!-- Left -->
            <div class="w-1/3">
                <div class="flex min-w-0 flex-1 items-center">
                    <BrandLogo class="toolbar-brand" onclick={navigateHome} />

                    {#if page.url.pathname !== "/"}
                        <Menubar.Root
                            bind:value={menubarValue}
                            class="toolbar-menubar"
                        >
                            <File {isDatasetReadOnly} />
                            <Edit
                                {canRedo}
                                {canUndo}
                                {isDatasetReadOnly}
                                {reload}
                            />
                            <View />
                            <Help />
                        </Menubar.Root>
                    {/if}
                </div>
            </div>

            <!-- Center -->
            <div class="w-1/3">
                {#if page.url.pathname === "/mainpage"}
                    <div class="w-full">
                        <Searchbar />
                    </div>
                {/if}
            </div>

            <!-- Right -->
            <div class="w-1/3">
                <div
                    class="mr-2 ml-auto flex max-w-35 flex-1 justify-end space-x-2"
                >
                    {#if isDatasetReadOnly}
                        <ButtonControl
                            callOnClick={requestEnableEditing}
                            height={9}
                        >
                            Enable Editing
                        </ButtonControl>
                    {/if}
                </div>
            </div>
        </nav>
    {/if}

    <div class="min-h-0 flex-1">
        {@render children?.()}
    </div>
</div>
