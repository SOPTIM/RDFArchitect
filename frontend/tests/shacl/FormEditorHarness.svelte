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
    /** Owns the buffer the way the workbench page does, which a test file cannot: no runes there. */
    import { untrack } from "svelte";

    import FormEditor from "../../src/routes/shacl/workbench/FormEditor.svelte";

    let { form, initial, log = [] } = $props();

    let turtle = $state(untrack(() => initial));
</script>

<FormEditor
    {form}
    {turtle}
    onturtle={(next, base) => {
        if (base !== undefined && base !== turtle) {
            return false;
        }
        log.push(next);
        turtle = next;
        return true;
    }}
/>
