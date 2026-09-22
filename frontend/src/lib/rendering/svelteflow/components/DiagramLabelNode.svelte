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
    import { getContext } from "svelte";

    import {
        FocusField,
        propertyEditorRequest,
        PropertyKind,
    } from "$lib/propertyEditorRequest.svelte.js";

    import { MULTIPLICITY_KIND } from "../diagram/labelNodes.js";
    import { DIAGRAM_SELECTION_CONTEXT } from "../interaction/diagramSelection.svelte.js";
    import { labelHighlight } from "../interaction/labelHighlight.svelte.js";
    import { bypassesProperties } from "../interaction/modifierKeys.svelte.js";
    import { propertyContextMenu } from "../interaction/propertyInteraction.svelte.js";

    let { id, data, draggable, dragging } = $props();

    const selection = getContext(DIAGRAM_SELECTION_CONTEXT);

    const target = $derived(
        data.associationEndUUID && data.ownerClassId
            ? {
                  classUuid: data.ownerClassId,
                  graphUri: data.graphUri,
                  kind: PropertyKind.ASSOCIATION,
                  propertyUuid: data.associationEndUUID,
                  focus:
                      data.kind === MULTIPLICITY_KIND
                          ? FocusField.MULTIPLICITY
                          : FocusField.LABEL,
              }
            : null,
    );

    function openAssociationEditor(event) {
        if (bypassesProperties(event)) {
            return;
        }
        event.stopPropagation();
        labelHighlight.clear();
        propertyEditorRequest.open(target);
    }

    /**
     * Lights the association up while the label is pressed. The release is taken from the window
     * because the pointer is let go wherever it happens to be, which after a drag is rarely over
     * the label itself.
     */
    function handlePointerDown(event) {
        labelHighlight.press(id);
        const holdsSelection = event.button === 0 && !!selection;
        if (holdsSelection) {
            selection.notifyLabelPress();
        }
        const release = () => {
            labelHighlight.release();
            if (holdsSelection) {
                selection.notifyLabelRelease();
            }
            window.removeEventListener("pointerup", release);
            window.removeEventListener("pointercancel", release);
        };
        window.addEventListener("pointerup", release);
        window.addEventListener("pointercancel", release);
    }
</script>

<div
    class="rounded bg-white/80 px-2 py-0.5 text-xs font-medium whitespace-nowrap text-[#303030] shadow-sm select-none"
    class:cursor-grabbing={dragging}
    class:cursor-grab={draggable && !dragging}
    class:cursor-pointer={!draggable && !!target}
    onpointerdown={handlePointerDown}
    ondblclick={target ? openAssociationEditor : undefined}
    oncontextmenu={target
        ? event => propertyContextMenu.open(event, target)
        : undefined}
    role="presentation"
>
    {data.text}
</div>
