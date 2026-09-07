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
    import { labelHighlight } from "../interaction/labelHighlight.svelte.js";

    let { id, data, draggable } = $props();

    /**
     * Lights the association up while the label is pressed. The release is taken from the window
     * because the pointer is let go wherever it happens to be, which after a drag is rarely over
     * the label itself.
     */
    function handlePointerDown() {
        labelHighlight.press(id);
        const release = () => {
            labelHighlight.release();
            window.removeEventListener("pointerup", release);
            window.removeEventListener("pointercancel", release);
        };
        window.addEventListener("pointerup", release);
        window.addEventListener("pointercancel", release);
    }
</script>

<div
    class="rounded bg-white/80 px-2 py-0.5 text-xs font-medium whitespace-nowrap text-[#303030] shadow-sm select-none"
    class:cursor-move={draggable}
    onpointerdown={handlePointerDown}
    role="presentation"
>
    {data.text}
</div>
