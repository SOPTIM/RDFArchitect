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
    import { faCaretRight, faPlus } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onDestroy, onMount } from "svelte";
    import { Fa } from "svelte-fa";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import List from "$lib/components/List.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { userSettings } from "$lib/userSettings.svelte.js";

    import Association from "./Association.svelte";
    import AssociationEditorDialog from "./associationEditorDialog/AssociationEditorDialog.svelte";

    const {
        associations,
        inheritedAssociations = [],
        openPropertySHACLRulesDialog,
    } = $props();

    const MIN_W = 7;
    const MAX_W = 20;
    const REM_PER_W = 0.25;

    const classEditorContext = getContext("classEditor");

    const associationEditorDialog = $state({
        showDialog: false,
        association: null,
        targetClass: null,
    });

    let expandStereotypes = $state(true);
    let expandInherited = $state(false);

    let container;
    let w = $state(MIN_W);
    let resizeObserver;
    let readonly = $derived(classEditorContext.readonly);
    let showInherited = $derived(
        userSettings.get("showInheritedProperties", true) &&
            inheritedAssociations.length > 0,
    );

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = classEditorContext.readonly;
    });

    onMount(() => {
        updateWidth();
        resizeObserver = new ResizeObserver(updateWidth);
        resizeObserver.observe(container);
        readonly = classEditorContext.readonly;
    });

    onDestroy(() => {
        resizeObserver?.disconnect();
    });

    function openAssociationEditor(association, targetClass = null) {
        associationEditorDialog.association = association;
        associationEditorDialog.targetClass = targetClass;
        associationEditorDialog.showDialog = true;
    }

    function updateWidth() {
        // container.clientWidth / 40 because each w unit is 0.25rem and 1rem = 16px, so 40px = 1w
        const calculated = Math.floor(container.clientWidth / 40);
        w = Math.min(MAX_W, Math.max(MIN_W, calculated));
    }
</script>

<div bind:this={container} class="h-full w-full">
    <List
        legend="Associations"
        bind:isExpanded={expandStereotypes}
        highlight={associations.isModified}
        warn={!associations.isValid}
    >
        {#snippet actions()}
            {#if !readonly}
                <div class="size-8">
                    <FaIconButton
                        callOnClick={() => {
                            openAssociationEditor(null);
                            expandStereotypes = true;
                        }}
                        icon={faPlus}
                    />
                </div>
            {/if}
        {/snippet}

        {#snippet contents()}
            <thead>
                <tr>
                    <th
                        style="width:{w * REM_PER_W}rem"
                        class="text-blue pl-1 text-left font-normal"
                    >
                        Multiplicity
                    </th>
                    <th
                        style="width:{w * REM_PER_W}rem"
                        class="pl-1 text-left font-normal"
                    ></th>

                    <th class="text-blue pl-1 text-left font-normal">Label</th>
                    <th class="size-8"></th>
                    <th class="size-8"></th>
                    {#if !readonly}
                        <th class="size-8"></th>
                    {/if}
                </tr>
            </thead>

            <tbody>
                {#if showInherited}
                    <tr>
                        <td colspan={readonly ? 5 : 6} class="pt-0.5">
                            <button
                                type="button"
                                class="text-blue flex w-fit cursor-pointer items-center gap-1.5 pl-1 text-sm"
                                onclick={() =>
                                    (expandInherited = !expandInherited)}
                            >
                                <Fa
                                    icon={faCaretRight}
                                    class={`w-2 transition-transform duration-200 ${
                                        expandInherited
                                            ? "rotate-90"
                                            : "rotate-0"
                                    }`}
                                />
                                <span>Inherited associations</span>
                            </button>
                        </td>
                    </tr>
                    {#if expandInherited}
                        {#each inheritedAssociations as group}
                            <tr>
                                <td
                                    colspan={readonly ? 5 : 6}
                                    class="pt-0.5 pl-2"
                                >
                                    <span
                                        class="text-text-subtle text-xs italic"
                                    >
                                        from {group.sourceClassLabel}
                                    </span>
                                </td>
                            </tr>
                            {#each group.associations as association}
                                <Association
                                    {associations}
                                    {association}
                                    {openAssociationEditor}
                                    {openPropertySHACLRulesDialog}
                                    {w}
                                    inherited={true}
                                    targetClass={{
                                        uuid: group.sourceClassUuid,
                                        prefix: group.sourceClassPrefix,
                                        label: group.sourceClassLabel,
                                    }}
                                />
                            {/each}
                        {/each}
                    {/if}
                    <tr>
                        <td colspan={readonly ? 5 : 6} class="pt-1"></td>
                    </tr>
                {/if}
                {#each associations.values as association}
                    <Association
                        {associations}
                        {association}
                        {openAssociationEditor}
                        {openPropertySHACLRulesDialog}
                        {w}
                    />
                {/each}
            </tbody>
        {/snippet}
    </List>
</div>

<AssociationEditorDialog
    bind:showDialog={associationEditorDialog.showDialog}
    {associations}
    bind:association={associationEditorDialog.association}
    targetClass={associationEditorDialog.targetClass}
/>
