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
    import { faDatabase } from "@fortawesome/free-solid-svg-icons";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import { PUBLIC_DESKTOP } from "$lib/config/runtime";

    import NewWorkspaceDialog from "../../NewWorkspaceDialog.svelte";
    import WorkspaceImportDialog from "../../WorkspaceImportDialog.svelte";
    import SchemaDropZone from "../SchemaDropZone.svelte";

    let showNewWorkspaceDialog = $state(false);
    let showOpenWorkspaceDialog = $state(false);
</script>

<SchemaDropZone
    hideContentOnDrag
    class="bg-window-background flex min-h-0 flex-1 items-center justify-center"
>
    <EmptyStateCard
        title="No workspace yet"
        description={PUBLIC_DESKTOP
            ? "A workspace holds your schemas, packages and diagrams. Create one, or open one you saved before."
            : "A workspace holds your schemas, packages and diagrams. Create one to get started."}
        icon={faDatabase}
    >
        <div class="flex flex-col items-center gap-2">
            <div class="w-52">
                <ButtonControl
                    callOnClick={() => (showNewWorkspaceDialog = true)}
                    height={9}
                >
                    Create Workspace
                </ButtonControl>
            </div>
            {#if PUBLIC_DESKTOP}
                <div class="w-52">
                    <ButtonControl
                        callOnClick={() => (showOpenWorkspaceDialog = true)}
                        height={9}
                    >
                        Open Workspace…
                    </ButtonControl>
                </div>
            {/if}
        </div>
    </EmptyStateCard>
</SchemaDropZone>

<NewWorkspaceDialog bind:showDialog={showNewWorkspaceDialog} />
{#if PUBLIC_DESKTOP}
    <WorkspaceImportDialog bind:showDialog={showOpenWorkspaceDialog} />
{/if}
