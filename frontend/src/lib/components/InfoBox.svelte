<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import { faCircleInfo } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let { children, type = "info" } = $props();

    const COLORS = {
        info: "text-blue",
        warn: "text-orange",
    };

    const colorClass = COLORS[type] ?? COLORS.info;
</script>

<div class="text-dark flex items-start space-x-2 rounded-lg border p-4">
    <Fa icon={faCircleInfo} class="{colorClass} mt-0.5" />
    <div class="text-sm leading-relaxed">
        {@render children?.()}
    </div>
</div>
