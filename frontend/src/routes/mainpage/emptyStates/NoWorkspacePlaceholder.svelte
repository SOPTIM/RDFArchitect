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

    import NewWorkspaceDialog from "../../NewWorkspaceDialog.svelte";

    let showNewWorkspaceDialog = $state(false);
</script>

<div
    class="bg-window-background flex min-h-0 flex-1 items-center justify-center"
>
    <EmptyStateCard
        title="No workspace yet"
        description="A workspace holds your schemas, packages and diagrams. Create one to get started."
        icon={faDatabase}
    >
        <div class="w-52">
            <ButtonControl
                callOnClick={() => (showNewWorkspaceDialog = true)}
                height={9}
            >
                Create Workspace
            </ButtonControl>
        </div>
    </EmptyStateCard>
</div>

<NewWorkspaceDialog bind:showDialog={showNewWorkspaceDialog} />
