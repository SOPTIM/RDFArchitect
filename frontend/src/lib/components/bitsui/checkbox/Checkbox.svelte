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
    import { faCheck, faMinus } from "@fortawesome/free-solid-svg-icons";
    import { Checkbox as BitsUiCheckbox } from "bits-ui";
    import { Fa } from "svelte-fa";

    let {
        checked = $bindable(false),
        indeterminate = $bindable(false),
        disabled = false,
        onCheckedChange,
        onclick,
        ...restProps
    } = $props();
</script>

<BitsUiCheckbox.Root
    bind:checked
    bind:indeterminate
    {disabled}
    {onCheckedChange}
    {onclick}
    class="border-border bg-default-background data-[state=checked]:bg-button-default-background data-[state=checked]:text-button-default-text data-[state=indeterminate]:bg-button-default-background data-[state=indeterminate]:text-button-default-text flex h-4 w-4 items-center justify-center rounded border transition-colors disabled:cursor-not-allowed disabled:opacity-50"
    {...restProps}
>
    {#snippet children({ checked: isChecked, indeterminate: isIndeterminate })}
        {#if isIndeterminate}
            <Fa icon={faMinus} size="xs" />
        {:else if isChecked}
            <Fa icon={faCheck} size="xs" />
        {/if}
    {/snippet}
</BitsUiCheckbox.Root>
