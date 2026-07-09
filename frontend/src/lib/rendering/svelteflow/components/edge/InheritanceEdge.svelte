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

    import { getEdgeParams } from "./edgeUtils.ts";

    let { id, source, target, data } = $props();

    const style = "stroke-width: 2px; stroke: var(--color-inheritance-edge);";

    const markerEnd = "url(#inheritance)";
    let sourceNode = useInternalNode(source);
    let targetNode = useInternalNode(target);
    let edgeTargetOffset = $derived(data?.offsetEdge ? 100 : 0);

    let edgeParams = $derived.by(() => {
        if (sourceNode.current && targetNode.current) {
            return getEdgeParams(
                sourceNode.current,
                targetNode.current,
                edgeTargetOffset,
            );
        }
    });

    let path = $derived(
        getStraightPath({
            sourceX: edgeParams.sx,
            sourceY: edgeParams.sy,
            targetX: edgeParams.tx,
            targetY: edgeParams.ty,
        })[0],
    );
</script>

<BaseEdge {id} {path} {markerEnd} {style} />
