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
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogButtons from "$lib/dialog/DialogButtons.svelte";
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
        graphViewState.showGraphFilter.updateValue(false);
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

<Dialog bind:showDialog>
    <div class="flex flex-col pb-1">
        {#each options as option}
            <CheckBoxEditControl
                label={option.label}
                labelFirst={false}
                bind:value={option.value}
            />
        {/each}
    </div>
    //TODO: RDFA-403 finish refactoring
    <DialogButtons
        bind:showDialog
        submitLabel="Save Changes"
        onSubmit={submit}
    />
</Dialog>
