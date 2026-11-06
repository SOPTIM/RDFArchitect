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
    /**
     * What a shape or a rule says that the form has no field for at all.
     *
     * Shown rather than hidden, because these are the lines that used to make the whole shape
     * read-only, and a user who has been told "Turtle only" for a year should be able to see what
     * the form is now working around. Written as the document writes them, which is also what will
     * still be there after an edit.
     */
    import { abbreviate } from "$lib/shacl/turtleTerms.js";

    let { clauses = [], prefixes = {} } = $props();
</script>

{#if clauses.length}
    <div class="text-text-subtle text-xs">
        <p>Kept as written, because the form has no field for it:</p>
        <ul class="mt-1 space-y-0.5 font-mono">
            {#each clauses as clause, index (index)}
                <li class="truncate">
                    {`${abbreviate(clause.predicate, prefixes)} ${clause.value}`}
                </li>
            {/each}
        </ul>
    </div>
{/if}
