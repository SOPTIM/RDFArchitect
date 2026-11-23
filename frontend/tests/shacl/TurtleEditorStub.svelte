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
    let {
        value = $bindable(""),
        readOnly = false,
        placeholder = undefined,
        onSave = undefined,
    } = $props();

    /** The editor's API the page calls; a test reads where it was asked to go. */
    export function reveal(line, column = 1) {
        globalThis.revealedInTurtleEditor = { line, column };
    }
</script>

<!-- Stands in for the Monaco editor, which does not load under jsdom. -->

<textarea
    aria-label="Turtle"
    bind:value
    readonly={readOnly}
    {placeholder}
    onkeydown={event => {
        if (event.key === "s" && event.ctrlKey && onSave) {
            event.preventDefault();
            onSave();
        }
    }}></textarea>
