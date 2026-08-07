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
    import { faCircleInfo } from "@fortawesome/free-solid-svg-icons";
    import { Tooltip } from "bits-ui";
    import { Fa } from "svelte-fa";

    let { text, side = "top", label = "More information" } = $props();
</script>

<Tooltip.Root>
    <Tooltip.Trigger
        type="button"
        aria-label={label}
        class="text-text-subtle hover:text-blue inline-flex cursor-help items-center border-none bg-transparent p-0"
    >
        <Fa icon={faCircleInfo} />
    </Tooltip.Trigger>
    <Tooltip.Portal>
        <Tooltip.Content
            {side}
            sideOffset={6}
            class="border-border bg-window-background text-default-text z-[80] max-w-xs rounded border px-2 py-1 text-xs shadow"
        >
            {text}
        </Tooltip.Content>
    </Tooltip.Portal>
</Tooltip.Root>
