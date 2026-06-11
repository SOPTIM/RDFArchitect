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
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { datasetStore } from "$lib/stores/DatasetStore.ts";
    import { versionControlStore } from "$lib/stores/VersionControlStore.ts";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";
    import Edit from "./layout/menu-bar/Edit.svelte";
    import File from "./layout/menu-bar/File.svelte";
    import Help from "./layout/menu-bar/Help.svelte";
    import View from "./layout/menu-bar/View.svelte";
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

    let selectedDataset = $derived(editorState.selectedDataset.getValue());

    $effect(async () => {
        editorState.selectedDiagram.subscribe();
        editorState.selectedClassUUID.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDataset.subscribe();
        forceReloadTrigger.subscribe();
        await fetchUndoRedo();
        isDatasetReadOnly = selectedDataset
            ? datasetStore.isReadOnly(selectedDataset)
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
        const { error } = await datasetStore.updateReadonly(
            selectedDataset,
            false,
        );
        if (error) return;

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
        canUndo = versionControlStore.canUndo();
        canRedo = versionControlStore.canRedo();
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

    function isInputElement(target) {
        return (
            target instanceof HTMLElement &&
            (target.isContentEditable ||
                target.tagName === "INPUT" ||
                target.tagName === "TEXTAREA" ||
                target.tagName === "SELECT")
        );
    }

    function isDialogOpen() {
        return !!document.querySelector(
            '[role="dialog"], [role="alertdialog"]',
        );
    }

    async function handleUndoRedo(isRedo) {
        if (isRedo) {
            if (canRedo && (await versionControlStore.redo())) await reload();
        } else {
            if (canUndo && (await versionControlStore.undo())) await reload();
        }
    }

    async function handleKeydown(event) {
        if (event.code === "AltLeft") isLeftAltPressed = true;

        if (event.key === "Escape" && !event.defaultPrevented) {
            eventStack.executeNewestEvent();
            return;
        }

        const key = event.key.toLowerCase();
        const hasCtrl = event.ctrlKey || event.metaKey;
        const inputFocused = isInputElement(event.target);

        const hasCtrlAltViaAltGr =
            event.getModifierState("AltGraph") && isLeftAltPressed;
        if (hasCtrl || hasCtrlAltViaAltGr) {
            if (key === "z" || key === "y") {
                if (inputFocused) return;
                if (isDialogOpen()) {
                    return;
                }
                event.preventDefault();
                await handleUndoRedo(
                    key === "y" || (key === "z" && event.shiftKey),
                );
                return;
            }
        }

        if (inputFocused) return;
        shortcutStore.handleEvent(event);
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
