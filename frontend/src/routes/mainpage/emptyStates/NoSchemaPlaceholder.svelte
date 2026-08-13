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
    import { faDiagramProject } from "@fortawesome/free-solid-svg-icons";

    import { isReadOnly } from "$lib/api/apiWorkspaceUtils.js";
    import { asyncValue } from "$lib/asyncValue.svelte.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";

    import WorkspaceActionsMenu from "../workspaceActions/WorkspaceActionsMenu.svelte";

    let { workspaceName } = $props();

    const readonlyValue = asyncValue(() => workspaceName, isReadOnly);

    let showNewGraphDialog = $state(false);
    let showImportDialog = $state(false);
    const readonly = $derived(readonlyValue.current ?? false);
</script>

<ContextMenu.Root>
    <ContextMenu.TriggerArea class="contents">
        <div
            class="bg-window-background flex min-h-0 flex-1 items-center justify-center"
        >
            <EmptyStateCard
                title="No schema yet"
                description={readonly
                    ? `The workspace "${workspaceName}" is read-only. Enable editing to add schemas.`
                    : "Create a schema (RDFS) or import an existing one to start modelling."}
                icon={faDiagramProject}
            >
                <div class="flex gap-2">
                    <div class="w-44">
                        <ButtonControl
                            callOnClick={() => (showNewGraphDialog = true)}
                            disabled={readonly}
                            height={9}
                        >
                            New Schema
                        </ButtonControl>
                    </div>
                    <div class="w-44">
                        <ButtonControl
                            callOnClick={() => (showImportDialog = true)}
                            disabled={readonly}
                            variant="contrast"
                            height={9}
                        >
                            Import Schema (RDFS)
                        </ButtonControl>
                    </div>
                </div>
            </EmptyStateCard>
        </div>
    </ContextMenu.TriggerArea>
    <WorkspaceActionsMenu
        {workspaceName}
        {readonly}
        bind:showNewGraphDialog
        bind:showImportDialog
    />
</ContextMenu.Root>
