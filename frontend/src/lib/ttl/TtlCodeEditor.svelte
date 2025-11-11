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
    import { EditorState } from "@codemirror/state";
    import { EditorView, basicSetup } from "codemirror";
    import { turtle } from "codemirror-lang-turtle";
    import { onDestroy, onMount } from "svelte";

    let { value = $bindable(), readOnly = false } = $props();
    let editorDiv;
    let editor;
    onMount(() => {
        const state = EditorState.create({
            doc: value, // Set initial value
            extensions: [
                basicSetup,
                //setup lang
                turtle(),
                //setup if the editor is readOnly
                EditorView.editable.of(!readOnly),
                //update value on change
                EditorView.updateListener.of(update => {
                    if (update.docChanged) {
                        value = update.state.doc.toString();
                    }
                }),
            ],
        });

        // Create CodeMirror editor instance
        editor = new EditorView({
            state,
            parent: editorDiv,
        });
    });

    onDestroy(() => {
        editor?.destroy();
    });
</script>

<div bind:this={editorDiv} class="w-full"></div>
