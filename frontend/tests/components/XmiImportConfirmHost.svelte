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
    import { Tooltip } from "bits-ui";

    import XmiImportConfirm from "$lib/components/XmiImportConfirm.svelte";

    let {
        read,
        choice,
        modelNamespace = "",
        extensionNamespace = "",
    } = $props();
</script>

<!--
  - Holds the confirm step inside the tooltip provider the app's layout puts around everything.
  - Its info tooltips need one and refuse to mount without it.
  -->

<Tooltip.Provider>
    <XmiImportConfirm
        {read}
        {choice}
        bind:modelNamespace
        bind:extensionNamespace
    />
</Tooltip.Provider>
