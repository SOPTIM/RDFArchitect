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
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import GraphExport from "$lib/GraphExport.svelte";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();

    let disablePrimary = $state(false);
    let onPrimary = $state(() => {});
</script>

<ActionDialog
    bind:showDialog
    primaryLabel="Export"
    {disablePrimary}
    {onPrimary}
    title="Export Graph"
>
    <GraphExport
        bind:showDialog
        bind:disablePrimary
        bind:onSubmit={onPrimary}
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
</ActionDialog>
