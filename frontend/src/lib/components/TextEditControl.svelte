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
        id = crypto.randomUUID(),
        disabled = false,
        readonly = false,
        highlight = false,
        warn = false,
        buttons = [],
    } = $props();
</script>

<div class="text-default-text flex h-full w-full flex-col">
    <label for={id}>
        {#if label}
            {label}
        {/if}
    </label>
    <InputWithButtonsControl
        {placeholder}
        bind:value
        {callOnInput}
        {id}
        {disabled}
        {readonly}
        {highlight}
        {warn}
        type="text"
        {buttons}
    />
</div>
