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
    import { faMinus } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";

    let {
        value,
        disabled = false,
        onValueChange = () => {},
        onRemove,
    } = $props();
</script>

<div class="border-border flex items-center space-x-3 rounded border px-3 py-2">
    <div class="flex-1">
        <input
            class="border-border bg-window-background focus:border-blue ring-none w-full rounded border-2 p-2 text-sm outline-none"
            type="text"
            {value}
            {disabled}
            oninput={event => onValueChange(event.target.value)}
        />
    </div>
    <div class="flex size-10 items-center justify-center p-0">
        <ButtonControl height={10} callOnClick={onRemove} title="Remove file">
            <Fa icon={faMinus} ariaLabel="Remove file" />
        </ButtonControl>
    </div>
</div>
