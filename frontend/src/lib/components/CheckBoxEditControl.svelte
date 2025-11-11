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
    let {
        label,
        value = $bindable(),
        callOnInputTrue = () => {},
        callOnInputFalse = () => {},
        disabled = false,
        labelFirst = true,
        readonly = false,
    } = $props();

    let uuid = crypto.randomUUID();
</script>

{#if labelFirst && label}
    <label for={uuid} class=" text-default-text">
        {label}
    </label>
{/if}
<input
    type="checkbox"
    id={uuid}
    class="text-button-default-text bg-default-background checked:bg-button-default-background h-4 w-4
           rounded
           border-none disabled:cursor-not-allowed"
    bind:checked={value}
    onchange={() => {
        if (value) {
            callOnInputTrue();
        } else {
            callOnInputFalse();
        }
    }}
    disabled={disabled || readonly}
/>
{#if !labelFirst && label}
    <label for={uuid} class="">
        {label}
    </label>
{/if}
