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

    import { enableEditing } from "$lib/actions/editingActions.js";
    import {
        undo,
        fetchCanUndo,
        redo,
        fetchCanRedo,
    } from "$lib/actions/versionControlActions.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import {
        installBackendFetchInterceptor,
        probeBackendConnection,
    } from "$lib/api/backendConnectionMonitor.svelte.js";
    import { Menubar } from "$lib/components/bitsui/menubar";
    import BrandLogo from "$lib/components/BrandLogo.svelte";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import ToastContainer from "$lib/components/ToastContainer.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

    import {
        copyState,
        DiagramType,
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

    let isLeftAltPressed = false;
    let canCopyClass = $derived(editorState.selectedClassUUID.getValue());
    let canPasteClass = $derived(
        !isDatasetReadOnly &&
            editorState.selectedDataset.getValue() &&
            editorState.selectedGraph.getValue() &&
            editorState.selectedDiagram.getProperty("id") &&
            editorState.selectedDiagram.getProperty("type") ===
                DiagramType.PACKAGE &&
            copyState.datasetName.getValue() &&
            copyState.datasetName.getValue() &&
            copyState.graphURI.getValue() &&
            copyState.classUUID.getValue(),
    );

    let selectedDataset = $derived(editorState.selectedDataset.getValue());

    $effect(async () => {
        editorState.selectedDiagram.subscribe();
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
        installBackendFetchInterceptor();
        probeBackendConnection();
        loadSnapshot();
    });

    async function requestEnableEditing() {
        if (!selectedDataset || !isDatasetReadOnly) {
            return;
        }
        if (!(await enableEditing(selectedDataset))) {
            return;
        }
        forceReloadTrigger.trigger();
        editorState.selectedClassUUID.trigger();
        editorState.selectedDiagram.trigger();
        isDatasetReadOnly = false;
    }

    async function loadSnapshot() {
        const base64Param = page.url.searchParams.get("snapshot");
        if (base64Param) {
            const res = await bec.loadSnapshot(base64Param);
            if (res.ok) {
                await goto("/mainpage");
                toastStore.success(
                    "Snapshot loaded",
                    "The shared snapshot has been loaded.",
                );
            } else {
                toastStore.error(
                    "Could not load snapshot",
                    "The snapshot link is invalid or has expired.",
                );
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

    async function pasteClass(
        copyAsAbstract,
        copyAttributes,
        copyAssociations,
    ) {
        const res = await bec.getPackages(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
        );
        const packagesJSON = await res.json();
        let packages = [
            ...packagesJSON.internalPackageList,
            ...packagesJSON.externalPackageList,
        ];
        const selectedPackageUUID =
            editorState.selectedDiagram.getProperty("id") === "default"
                ? null
                : editorState.selectedDiagram.getProperty("id");
        let packageDTO = selectedPackageUUID
            ? (packages.find(pkg => pkg.uuid === selectedPackageUUID) ?? null)
            : null;
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
        if (event.code === "AltLeft") {
            isLeftAltPressed = true;
        }

        if (event.key === "Escape") {
            if (event.defaultPrevented) {
                return;
            }
            eventStack.executeNewestEvent();
            return;
        }

        const hasCtrl = event.ctrlKey || event.metaKey;
        const hasCtrlAltViaAltGr =
            event.getModifierState("AltGraph") && isLeftAltPressed;

        if (!hasCtrl && !hasCtrlAltViaAltGr) {
            return;
        }

        // Undo/Redo always fires, even when an input is focused
        let key = event.key.toLowerCase();
        if (key === "z" || key === "y") {
            event.preventDefault();
            if (key === "z") {
                if (event.shiftKey) {
                    if (canRedo && (await redo())) {
                        await reload();
                        toastStore.info("Redone");
                    }
                } else {
                    if (canUndo && (await undo())) {
                        await reload();
                        toastStore.info("Undone");
                    }
                }
            } else if (key === "y"){
                if (canRedo && (await redo())) {
                    await reload();
                    toastStore.info("Redone");
                }
            }
            return;
        }

        // All other shortcuts: skip when an input-like element is focused
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

        switch (event.code) {
            case "KeyC":
                event.preventDefault();
                if (canCopyClass) copyClass();
                break;
            case "KeyV":
                event.preventDefault();
                if (canPasteClass) {
                    if (event.shiftKey && isLeftAltPressed) {
                        await pasteClass(true, false, false);
                    } else if (event.shiftKey) {
                        await pasteClass(false, false, true);
                    } else if (isLeftAltPressed) {
                        await pasteClass(false, true, false);
                    } else {
                        await pasteClass(false, true, true);
                    }
                }
                break;
        }
    }
</script>

<svelte:window
    onkeydown={handleKeydown}
    onkeyup={e => {
        if (e.code === "AltLeft") isLeftAltPressed = false;
    }}
    onblur={() => {
        isLeftAltPressed = false;
    }}
/>
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
<ToastContainer />
