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
    import { onMount } from "svelte";

    import { editorState } from "$lib/sharedState.svelte.js";

    import MermaidOnly from "./mermaidOnly.svelte";

    /** @type {{mermaidString: any, rightInsetPercent?: number, scale?: number, offsetX?: number, offsetY?: number}} */
    let { mermaidString, isLoading = $bindable(false) } = $props();
    let container;

    let scale = $state(1);
    let offsetX = $state(0);
    let offsetY = $state(0);

    //zoom/drag logic
    let isMoving = $state(false);

    $effect(() => {
        isLoading = true;
        if (mermaidString) {
            resetTransform();
        }
    });

    onMount(() => {
        /*
			getClassInformation is the name of the function that is called, when clicking a class in the mermaid diagram.
			The name callbackFunction is defined when creating the mermaidString (in our case in the backend).
			So we have to coordinate the function names manually
		 */
        window.getClassInformation = nodeId => {
            console.log("selecting class: ", nodeId);
            editorState.selectedClassUUID.updateValue(nodeId);
        };
    });

    export function resetTransform() {
        scale = 1;
        offsetX = 0;
        offsetY = 0;
    }

    const handleWheel = event => {
        event.preventDefault();
        const rect = container.getBoundingClientRect();
        const mouseX = event.clientX - rect.left;
        const mouseY = event.clientY - rect.top;

        const direction = event.deltaY > 0 ? 1 : -1;
        let newScale = scale - direction * 0.2;
        newScale = Math.max(0.1, Math.min(newScale, 10));

        offsetX -= (mouseX * newScale) / scale - mouseX;
        offsetY -= (mouseY * newScale) / scale - mouseY;

        scale = newScale;
    };

    const drag = event => {
        if (!isMoving) return;
        offsetX += event.movementX;
        offsetY += event.movementY;
    };
</script>

<div
    class="relative h-full scroll-smooth"
    aria-hidden="true"
    onwheel={handleWheel}
    onmousedown={event => {
        event.preventDefault();
        isMoving = true;
    }}
    onmousemove={drag}
    onmouseup={() => (isMoving = false)}
    onmouseleave={() => (isMoving = false)}
>
    <div
        bind:this={container}
        class="transition-scale relative duration-75"
        style="transform-origin: 0 0; transform: translate({offsetX}px, {offsetY}px) scale({scale});"
    >
        {#key mermaidString}
            <MermaidOnly {mermaidString} bind:isLoading />
        {/key}
    </div>
</div>
