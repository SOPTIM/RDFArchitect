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
    import { Tabs as BitsUiTabs } from "bits-ui";

    let { children, class: className = "" } = $props();
</script>

<BitsUiTabs.List
    class={"border-border bg-window-background inline-flex items-stretch gap-0.5 rounded-md border p-0.5 " +
        className}
>
    {@render children?.()}
</BitsUiTabs.List>
