<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    

    let {
        id,
        value = $bindable(null),
        options = [],
        getOptionIsDisabled = () => false,
        getOptionValue = o => o,
        getOptionLabel = o => o,
        disabled = false,
        placeholder,
        onchange,
        height = 9,
        placeholderValue = EMPTY,
    } = $props();const EMPTY = "__NULL__";

    let internalValue = $state(EMPTY);

    $effect(() => {
        internalValue = value == null ? EMPTY : value;
    });

    function handleChange() {
        value = internalValue === EMPTY ? null : internalValue;
        onchange?.(value);
    }
</script>

<select
    {id}
    bind:value={internalValue}
    class="block h-{height} bg-window-background disabled:bg-default-background
           text-default-text border-button-border focus:border-blue disabled:border-border
           w-full min-w-0
           rounded border border-solid
           px-2 font-[350]
           shadow-xs
           transition-colors outline-none
           disabled:cursor-not-allowed"
    {disabled}
    onchange={handleChange}
>
    {#if placeholder !== undefined}
        <option
            class="bg-window-background text-default-text"
            value={placeholderValue}
        >
            {placeholder}
        </option>
    {/if}

    {#each options as option (getOptionValue(option))}
        <option
            class="bg-window-background text-default-text disabled:text-text-subtle"
            disabled={getOptionIsDisabled(option)}
            value={getOptionValue(option)}
        >
            {getOptionLabel(option)}
        </option>
    {/each}
</select>
