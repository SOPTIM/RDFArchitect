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
    import InputWithButtonsControl from "$lib/components/InputWithButtonsControl.svelte";

    let {
        label,
        placeholder = "",
        value = $bindable(),
        callOnInput = () => {},
        optionValues = [],
        highlight = false,
        warn = false,
        disabled = false,
        readonly = false,
        buttons = [],
    } = $props();

    let datalistID = crypto.randomUUID();
    let id = crypto.randomUUID();
</script>

<div class="block h-full w-full">
    <label for={id} class="text-default-text w-full font-semibold">
        {#if label}
            {label}
        {/if}
    </label>
    <InputWithButtonsControl
        type="text"
        {id}
        {highlight}
        {warn}
        {placeholder}
        bind:value
        list={datalistID}
        {callOnInput}
        {disabled}
        {readonly}
        {buttons}
    />
    <datalist id={datalistID}>
        {#each optionValues as optionValue}
            <option value={optionValue}></option>
        {/each}
    </datalist>
</div>
