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
    import { v4 as uuidv4 } from "uuid";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import FileDropZone from "$lib/components/FileDropZone.svelte";

    let {
        /** Called with the files picked or dropped, and the names of any dropped folders. */
        onFiles,
        /** What the file dialog offers, as a comma-separated list of extensions. */
        accept,
        /** Whether several files can be picked at once. */
        multiple = false,
        /** What is said beside the button, naming what is to be dropped. */
        prompt,
        /** What is said below both, which is where the detail belongs. */
        children,
    } = $props();

    const fileInputId = `file-input-${uuidv4()}`;

    let fileInputValue = $state("");
</script>

<input
    class="hidden"
    type="file"
    id={fileInputId}
    {multiple}
    {accept}
    onchange={event => {
        onFiles(event.target.files);
        event.target.value = "";
    }}
    bind:value={fileInputValue}
/>
<FileDropZone
    class="hover:border-blue flex w-full flex-col px-4 py-6"
    onFiles={items => onFiles(items.files, items.directoryNames)}
>
    <div
        class="flex flex-col items-start space-y-2 md:flex-row md:items-center md:space-y-0 md:space-x-3"
    >
        <div class="h-9 w-24">
            <ButtonControl
                height={9}
                callOnClick={() => {
                    document.getElementById(fileInputId).click();
                }}
            >
                Select File
            </ButtonControl>
        </div>
        <p class="text-font-secondary text-sm">{prompt}</p>
    </div>
    {@render children?.()}
</FileDropZone>
