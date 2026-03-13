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
    import { faXmark } from "@fortawesome/free-solid-svg-icons";

    import FaIconButton from "$lib/components/FaIconButton.svelte";

    let {
        showDialog = $bindable(),
        title,
        icon,
        onClose = () => {},
    } = $props();

    let boundToDialog = $derived(showDialog !== undefined);

    function closeDialog() {
        onClose();
        if (boundToDialog) {
            showDialog = false;
        }
    }
</script>

<div class="mb-1 flex items-center justify-between">
    <div class="flex items-center space-x-2">
        {#if icon}
            <i class="fa-solid {icon}"></i>
        {/if}
        {#if title}
            <h2 class="text-lg font-semibold">{title}</h2>
        {/if}
    </div>
    <div class="size-8">
        <FaIconButton
            variant="danger"
            callOnClick={closeDialog}
            icon={faXmark}
        />
    </div>
</div>
