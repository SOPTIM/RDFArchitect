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
    import { SvelteFlowProvider } from "@xyflow/svelte";

    import PackageSnapshotCanvas from "./PackageSnapshotCanvas.svelte";

    let {
        nodes: inputNodes,
        edges: inputEdges,
        ready = $bindable(false),
        size = $bindable(),
    } = $props();
</script>

<SvelteFlowProvider>
    <PackageSnapshotCanvas {inputNodes} {inputEdges} bind:ready bind:size />
</SvelteFlowProvider>
