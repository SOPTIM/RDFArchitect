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
        options = [],
        getOptionValue = o => o,
        getOptionLabel = o => o,
        value = null,
        onChange,
    } = $props();

    let selectId = crypto.randomUUID();
</script>

<div class="flex items-center gap-2">
    <label for={selectId} class="text-default-text">{label}</label>
    <select
        id={selectId}
        class="border-border bg-window-background focus:border-blue h-9 w-fit rounded border-2 p-2"
        {value}
        onchange={e => onChange?.(e.currentTarget.value)}
    >
        {#each options as option (getOptionValue(option))}
            <option value={getOptionValue(option)}>
                {getOptionLabel(option)}
            </option>
        {/each}
    </select>
</div>
