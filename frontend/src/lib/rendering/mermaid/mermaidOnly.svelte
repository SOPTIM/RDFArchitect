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
    import mermaid from "mermaid";
    import { onMount } from "svelte";

    let {
        /** @type {string | undefined} */
        mermaidString,
        isLoading = $bindable(false),
    } = $props();
    let validSyntax = $state(true);

    onMount(async () => {
        mermaid.initialize({
            maxTextSize: 999999,
            securityLevel: "loose",
        });

        if (mermaidString) {
            try {
                await mermaid.run();
                console.log("MERMAID END TIME: ", performance.now());
            } catch (err) {
                validSyntax = false;
                console.error(
                    "couldn't render mermaidString\n err: ",
                    err,
                    mermaidString,
                );
            }
        }
        isLoading = false;
    });
</script>

{#if validSyntax}
    {#if mermaidString}
        <div class="mermaid">
            {mermaidString}
        </div>
    {/if}
{:else}
    <div class="bg-button-red-background text-button-default-text text-2xl">
        invalid mermaid syntax:
    </div>
    <br />
    <pre class="text-default-text break-words whitespace-pre-wrap">
        {mermaidString}
    </pre>
{/if}
