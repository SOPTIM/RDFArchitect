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

    let { children, class: className = "", disabled = false } = $props();

    function handlePointerEnter(event) {
        if (disabled) return;
        if (event.pointerType && event.pointerType !== "mouse") return;
        if (event.buttons) return;

        const trigger = event.currentTarget;
        if (!trigger) return;
        if (trigger.getAttribute("data-state") === "open") return;

        const openTrigger = document?.querySelector(
            ".menu-trigger[data-state='open']",
        );
        if (openTrigger) return;

        trigger.dispatchEvent(
            new PointerEvent("pointerdown", {
                bubbles: true,
                button: 0,
                pointerType: event.pointerType ?? "mouse",
            }),
        );
    }
</script>

<BitsUiMenubar.Trigger
    class={"menu-trigger " + className}
    {disabled}
    onpointerenter={handlePointerEnter}
>
    {@render children?.()}
</BitsUiMenubar.Trigger>
