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

    import ItemBase from "./ItemBase.svelte";

    let { center, altText, disabled, value = $bindable() } = $props();
</script>

<ItemBase {disabled}>
    <!-- Left -->
    <div class="flex w-6 items-center justify-center">
        <CheckBoxEditControl bind:value {disabled} />
    </div>

    <!-- Center -->
    <div class="flex-1 px-1">
        {@render center?.()}
    </div>

    <!-- Right -->
    <div
        class="text-secondary text-border-disabled ml-2 pr-1 text-sm whitespace-nowrap"
    >
        {altText}
    </div>
</ItemBase>
