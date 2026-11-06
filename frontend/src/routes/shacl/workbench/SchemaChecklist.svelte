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
    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";

    let {
        /** The workspace's schemas, as `{ uri, label }`. */
        schemas = [],
        /** The URIs ticked. Can hold URIs with no schema behind them, which stay as they are. */
        selected = [],
        readonly = false,
        onchange = () => {},
    } = $props();

    function set(uri, ticked) {
        onchange(
            ticked
                ? [...selected, uri]
                : selected.filter(candidate => candidate !== uri),
        );
    }
</script>

<!--
  @component
  Which schemas a constraints document applies to.
-->

{#if schemas.length === 0}
    <p class="text-text-subtle text-sm italic">
        This workspace has no schemas yet.
    </p>
{:else}
    <ul class="flex flex-col gap-1">
        {#each schemas as schema (schema.uri)}
            <li title={schema.uri}>
                <CheckBoxEditControl
                    label={schema.label}
                    labelFirst={false}
                    value={selected.includes(schema.uri)}
                    {readonly}
                    callOnInputTrue={() => set(schema.uri, true)}
                    callOnInputFalse={() => set(schema.uri, false)}
                />
            </li>
        {/each}
    </ul>
{/if}
