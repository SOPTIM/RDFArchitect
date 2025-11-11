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
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import GraphExport from "$lib/GraphExport.svelte";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();
</script>

<Dialog bind:showDialog>
    {#key showDialog}
        <div class="mt-1 mb-2 ml-2">
            <h2 class="text-default-text text-xl leading-tight font-semibold">
                Export Graph
            </h2>
        </div>
        <GraphExport
            bind:showDialog
            {lockedDatasetName}
            {lockedGraphUri}
            getAPIRoute={(datasetName, graphURI) =>
                PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(datasetName) +
                "/graphs/" +
                encodeURIComponent(graphURI) +
                "/content"}
            generateOntologyEntries={true}
        />
    {/key}
</Dialog>
