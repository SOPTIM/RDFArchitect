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
    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { graphViewState } from "$lib/sharedState.svelte.js";

    let { showDialog = $bindable() } = $props();

    let options = $state([
        {
            label: "include enum entries",
            value: graphViewState.filter.getValue().includeEnumEntries,
        },
        {
            label: "include attributes",
            value: graphViewState.filter.getValue().includeAttributes,
        },
        {
            label: "include associations",
            value: graphViewState.filter.getValue().includeAssociations,
        },
        {
            label: "include inheritance",
            value: graphViewState.filter.getValue().includeInheritance,
        },
        {
            label: "include relations to external packages",
            value: graphViewState.filter.getValue()
                .includeRelationsToExternalPackages,
        },
    ]);

    function submit() {
        graphViewState.filter.updateValue({
            includeEnumEntries: options[0].value,
            includeAttributes: options[1].value,
            includeAssociations: options[2].value,
            includeInheritance: options[3].value,
            includeRelationsToExternalPackages: options[4].value,
        });
        showDialog = false;
    }
</script>

<ActionDialog
    bind:showDialog
    primaryLabel="Save"
    onPrimary={submit}
    title="Select filters"
>
    <div class="flex flex-col space-y-2">
        {#each options as option}
            <div class="flex items-center space-x-2">
                <CheckBoxEditControl
                    label={option.label}
                    labelFirst={false}
                    bind:value={option.value}
                />
            </div>
        {/each}
    </div>
</ActionDialog>
