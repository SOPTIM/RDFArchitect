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
    import { faPlus, faMinus } from "@fortawesome/free-solid-svg-icons";

    import InputWithButtonsControl from "$lib/components/InputWithButtonsControl.svelte";

    import ItemBase from "./ItemBase.svelte";

    let {
        center,
        disabled = false,
        value = $bindable(),
        min = undefined,
        max = undefined,
        onchange = () => {},
        onpersist = () => {},
    } = $props();

    let debounceTimer = null;

    let buttons = $derived([
        {
            faIcon: faMinus,
            onClick: decrement,
            disabled: disabled || (min !== undefined && value <= min),
        },
        {
            faIcon: faPlus,
            onClick: increment,
            disabled: disabled || (max !== undefined && value >= max),
        },
    ]);

    function clamp(v) {
        if (min !== undefined && v < min) return min;
        if (max !== undefined && v > max) return max;
        return v;
    }

    function update(next) {
        if (next === value) return;
        value = next;
        onchange(value);
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            onpersist(value);
        }, 1000);
    }

    function increment() {
        update(clamp(value + 1));
    }

    function decrement() {
        update(clamp(value - 1));
    }

    function handleWheel(e) {
        e.preventDefault();
        if (disabled) return;
        if (e.deltaY < 0) {
            increment();
        } else if (e.deltaY > 0) {
            decrement();
        }
    }
</script>

<ItemBase {disabled}>
    <!-- Center -->
    <div class="flex-1 px-1">
        {@render center?.()}
    </div>

    <!-- Right: Counter control -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <button
        class="ml-2 pr-1"
        onclick={e => e.stopPropagation()}
        onwheel={handleWheel}
    >
        <InputWithButtonsControl
            type="number"
            bind:value
            {disabled}
            readonly
            {buttons}
        />
    </button>
</ItemBase>
