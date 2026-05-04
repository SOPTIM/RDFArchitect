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
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";
    import TtlCodeEditor from "$lib/ttl/TtlCodeEditor.svelte";

    let { showDialog = $bindable() } = $props();

    let customSHACL = $state();
    let customSHACLBackup = $state("");
    let readOnly = $state(false);
    let generatedShacl = $state("");
    let showGeneratedShacl = $state(false);

    function onOpen() {
        if (
            !editorState.selectedDataset.getValue() ||
            !editorState.selectedGraph.getValue()
        ) {
            customSHACL = "No dataset or schema selected.";
            customSHACLBackup = customSHACL;
            readOnly = true;
        } else {
            fetchCustomShacl();
            fetchGeneratedShacl();
        }
    }

    function fetchGeneratedShacl() {
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(editorState.selectedDataset.getValue()) +
                "/graphs/" +
                encodeURIComponent(editorState.selectedGraph.getValue()) +
                "/shacl/generate/string",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(res => {
                if (!res.ok) {
                    return "No Constraints (SHACL) found.";
                }
                return res.text();
            })
            .then(res => (generatedShacl = res));
    }

    /**
     * fetches the custom SHACL rules for the selected class.
     */
    function fetchCustomShacl() {
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(editorState.selectedDataset.getValue()) +
                "/graphs/" +
                encodeURIComponent(editorState.selectedGraph.getValue()) +
                "/shacl/custom/string",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(res => {
                if (!res.ok) {
                    return "No Constraints (SHACL) found.";
                }
                return res.text();
            })
            .then(res => {
                customSHACL = res;
                customSHACLBackup = res;
            });
    }

    function submitChanges() {
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(editorState.selectedDataset.getValue()) +
                "/graphs/" +
                encodeURIComponent(editorState.selectedGraph.getValue()) +
                "/shacl/custom/string",
            {
                method: "PUT",
                body: customSHACL,
                credentials: "include",
            },
        ).then(res => {
            if (res.ok) {
                customSHACLBackup = customSHACL;
            }
        });
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    size="w-2/3 h-4/5"
    title={`Constraints (SHACL) for: "${editorState.selectedDataset.getValue()}/${editorState.selectedGraph.getValue()}"`}
    primaryLabel={null}
>
    <div class="flex h-full flex-col space-y-2">
        <!-- Button controls -->
        <div class="flex h-9 w-full shrink-0 space-x-2">
            <div class="text-nowrap">
                <ButtonControl
                    callOnClick={() => (showGeneratedShacl = true)}
                    variant={showGeneratedShacl ? "" : "inline"}
                >
                    Generated Constraints
                </ButtonControl>
            </div>
            <div class="text-nowrap">
                <ButtonControl
                    callOnClick={() => (showGeneratedShacl = false)}
                    variant={showGeneratedShacl ? "inline" : ""}
                >
                    Custom Constraints
                </ButtonControl>
            </div>
        </div>

        <!-- Scrollable content area -->
        <div class="min-h-0 flex-1 overflow-y-auto">
            {#if showGeneratedShacl}
                {#if customSHACL !== undefined}
                    <TtlCodeEditor
                        bind:value={generatedShacl}
                        readOnly={true}
                    />
                {/if}
            {:else}
                {#if customSHACLBackup !== customSHACL}
                    <div class="mb-2 h-8 w-fit">
                        <ButtonControl callOnClick={submitChanges}>
                            Save Changes
                        </ButtonControl>
                    </div>
                {/if}
                {#if customSHACL !== undefined}
                    <TtlCodeEditor bind:value={customSHACL} {readOnly} />
                {/if}
            {/if}
        </div>
    </div>
</ActionDialog>
