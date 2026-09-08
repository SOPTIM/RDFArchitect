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
    import { BaseEdge, getStraightPath, useInternalNode } from "@xyflow/svelte";

    import { renderOptions } from "$lib/renderOptions.svelte.js";

    import { getEdgeParams } from "./edgeUtils.ts";
    import { labelHighlight } from "../interaction/labelHighlight.svelte.js";

    let { id, source, target, data } = $props();

    /**
     * The highlight rises quickly and decays slowly, which is what makes a short press read as a
     * pulse rather than as a state the edge sits in. The transition of the state being entered is
     * the one that runs, so these are not interchangeable.
     */
    const HIGHLIGHT_IN_TRANSITION =
        "transition: stroke 120ms ease-out, stroke-width 120ms ease-out;";
    const HIGHLIGHT_OUT_TRANSITION =
        "transition: stroke 450ms ease-out, stroke-width 450ms ease-out;";

    /** The widths an edge swells between while one of its labels is pressed. */
    const BASE_STROKE_WIDTH = "2px";
    const HIGHLIGHT_STROKE_WIDTH = "3.2px";

    let markerEnd = data.useToAssociation ? "url(#associationTo)" : "";
    let markerStart = data.useFromAssociation ? "url(#associationFrom)" : "";
    let sourceNode = useInternalNode(source);
    let targetNode = useInternalNode(target);

    let held = $derived(labelHighlight.isHeld(data.labels));

    let style = $derived.by(() => {
        const stroke =
            renderOptions.get("useColoredPropertiesInMergedView") && data.color
                ? data.color
                : "#000";
        return held
            ? `${HIGHLIGHT_IN_TRANSITION} stroke-width: ${HIGHLIGHT_STROKE_WIDTH};` +
                  " stroke: var(--color-class-node-highlighted);"
            : `${HIGHLIGHT_OUT_TRANSITION} stroke-width: ${BASE_STROKE_WIDTH}; stroke: ${stroke};`;
    });

    let edgeParams = $derived.by(() => {
        if (sourceNode.current && targetNode.current) {
            return getEdgeParams(sourceNode.current, targetNode.current);
        }
    });

    let path = $derived.by(() => {
        if (!edgeParams) return "";
        return target === source && sourceNode.current
            ? getSelfConnectingPath()
            : getStraightPath({
                  sourceX: edgeParams.sx,
                  sourceY: edgeParams.sy,
                  targetX: edgeParams.tx,
                  targetY: edgeParams.ty,
              })[0];
    });

    function getSelfConnectingPath() {
        const pos = sourceNode.current.internals.positionAbsolute ?? {
            x: 0,
            y: 0,
        };
        const w = sourceNode.current.measured.width ?? 100;

        const x1 = pos.x + w * 0.25;
        const y1 = pos.y;
        const x2 = pos.x + w * 0.75;
        const y2 = pos.y;

        const loopHeight = 70;
        return `M ${x1} ${y1} C ${x1} ${y1 - loopHeight}, ${x2} ${y2 - loopHeight}, ${x2} ${y2}`;
    }
</script>

<BaseEdge {id} {path} {markerStart} {markerEnd} {style} />
