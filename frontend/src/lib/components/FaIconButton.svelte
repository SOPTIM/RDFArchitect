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
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    const {
        callOnClick = () => {},
        disabled = false,
        icon,
        text,
        ...restProps
    } = $props();
</script>

<div class="w-full">
    <ButtonControl {disabled} {callOnClick} {...restProps}>
        <div class="flex size-full items-center justify-center gap-2">
            {#if text}
                <p>
                    {text}
                </p>
            {/if}
            <Fa {icon} />
        </div>
    </ButtonControl>
</div>
