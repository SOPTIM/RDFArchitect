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
        value,
        callOnValidChange = () => {},
        //accessDisplayData: how an option is displayed
        accessDisplayData = value => {
            return value;
        },
        //accessIdentifier: how an option is displayed in the dropdown menu
        accessIdentifier = value => {
            return value;
        },
        id = crypto.randomUUID(),
        optionObjectList,
        highlight = false,
        warn = false,
        disabled = false,
        readonly = false,
        tooltip = "",
        buttons = [],
    } = $props();

    let lastSavedValue = value;
    let datalistID = crypto.randomUUID();

    function verifyInput() {
        if (!value) {
            value = null;
            callOnValidChange(value);
            return;
        }
        for (let i = 0; i < optionObjectList.length; i++) {
            if (accessIdentifier(optionObjectList[i]) === value) {
                lastSavedValue = accessDisplayData(optionObjectList[i]);
                tooltip = value;
                value = lastSavedValue;
                callOnValidChange(optionObjectList[i]);
                return;
            }
        }
        value = lastSavedValue;
    }
</script>

<div class="text-default-text h-full w-full flex-col">
    <label for={id}>
        {#if label}
            {label}
        {/if}
    </label>
    <InputWithButtonsControl
        {placeholder}
        bind:value
        callOnChange={verifyInput}
        {id}
        {disabled}
        {readonly}
        {highlight}
        {warn}
        title={tooltip}
        type="text"
        {buttons}
        list={datalistID}
    />

    <datalist id={datalistID}>
        {#each optionObjectList as optionValue}
            <option
                class="tooltip-arrow"
                value={accessIdentifier(optionValue)}
            ></option>
        {/each}
    </datalist>
</div>
