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
    import { convert } from "asciidoctor";

    const { comment } = $props();
    let convertedClassComment = $state("");

    $effect(() => {
        if (!comment) {
            convertedClassComment = "";
            return;
        }
        let cancelled = false;
        convert(comment, { safe: "secure" }).then(result => {
            if (!cancelled) {
                convertedClassComment = result;
            }
        });
        return () => {
            cancelled = true;
        };
    });
</script>

<div id="comment">
    <!-- eslint-disable-next-line svelte/no-at-html-tags -->
    {@html convertedClassComment}
</div>

<style>
    @import "./asciidoctor.css";
</style>
