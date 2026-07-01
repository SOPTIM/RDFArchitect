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
    import { v4 as uuidv4 } from "uuid";

    import ButtonControl from "$lib/components/ButtonControl.svelte";

    let { file = $bindable(), id = uuidv4() } = $props();
</script>

<input
    class="hidden"
    type="file"
    {id}
    onchange={event => (file = event.target.files[0])}
/>

<div class="flex h-9 w-full space-x-4">
    <div class="h-9 w-32">
        <ButtonControl
            height={9}
            callOnClick={() => document.getElementById(id).click()}
        >
            Select file
        </ButtonControl>
    </div>
    <div class="h-9 w-full content-center">
        <p class="break-all">
            {file ? file.name : "no file selected"}
        </p>
    </div>
</div>
