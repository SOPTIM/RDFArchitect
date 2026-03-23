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
    import { faEye, faGear } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";
    import { v4 as uuidv4 } from "uuid";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/reactive-utils.js";

    import AsciidocComment from "../asciidocComment.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";

    const { comment } = $props();

    const classEditorContext = getContext("classEditor");
    const id = uuidv4();
    let editClassComment = $state(false);
    let readonly = $state(false);

    onMount(() => readonly = classEditorContext.readonly);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    })

</script>

<div class="flex min-h-0 flex-1 flex-col">
    <div class="text-default-text flex h-full w-full flex-col">
        <label for={id} class="text-blue block font-normal">Comment</label>
        {#if editClassComment}
            <div
                class="text-default-text border-border min-h-0 flex-1 overflow-auto rounded-xs border border-solid p-2
                        {readonly
                    ? 'bg-default-background'
                    : 'bg-window-background'}"
            >
                <AsciidocComment comment={comment.value} />
            </div>
        {:else}
            <div class="min-h-0 flex-1">
                <TextAreaControl
                    highlight={comment.isModified}
                    bind:value={comment.value}
                    {id}
                    {readonly}
                    warn={!comment.isValid}
                    buttons={getControlButtonsForReactiveObject(
                        comment,
                        readonly,
                    )}
                />
            </div>
        {/if}
    </div>
</div>
{#if !readonly}
    <div class="flex shrink-0 items-end justify-end space-x-1">
        <FaIconButton
            callOnClick={() => (editClassComment = !editClassComment)}
            text={editClassComment ? "Edit" : "Preview"}
            icon={editClassComment ? faGear : faEye}
        />
    </div>
{/if}
