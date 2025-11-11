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
    import { Menubar as BitsUiMenubar } from "bits-ui";

    import ItemSubMenuTriggerBase from "$lib/components/bitsui/base/menu/item/ItemSubMenuTriggerBase.svelte";

    let {
        children,
        faIcon,
        variant = "default",
        disabled = false,
        ...otherProps
    } = $props();

    function handlePointerEnter(event) {
        if (disabled) return;
        if (event.pointerType && event.pointerType !== "mouse") return;
        if (event.buttons) return;

        const trigger = event.currentTarget;
        if (!trigger) return;
        if (trigger.getAttribute("data-state") === "open") return;

        trigger.focus();
        trigger.click();
    }
</script>

<BitsUiMenubar.SubTrigger
    class="group w-full"
    onpointerenter={handlePointerEnter}
    {disabled}
    {...otherProps}
>
    <ItemSubMenuTriggerBase {faIcon} {children} {variant} />
</BitsUiMenubar.SubTrigger>
