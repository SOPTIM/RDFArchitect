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
     * One field the form shows with the value the document holds, and does not offer to change.
     *
     * In place of the field's own control, not beside it: an empty box where the document says
     * `sh:order 0.1` reads as "no order stated", and the form would then be lying about a value it
     * is deliberately leaving alone. The value is shown as written, because that is exactly what
     * will still be there afterwards.
     */
    import { faLock } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let { label, clauses = [] } = $props();

    const reason = $derived(
        clauses.find(clause => clause.reason)?.reason ?? "",
    );
</script>

<div>
    <span class="text-default-text text-sm">{label}</span>
    <div
        class="border-border bg-background-subtle flex items-center gap-2 rounded border px-2 py-1"
        title={reason}
    >
        <Fa icon={faLock} class="text-text-subtle shrink-0 text-xs" />
        <span
            class="text-text-subtle min-w-0 flex-1 truncate font-mono text-sm"
        >
            {clauses.map(clause => clause.value).join(" · ")}
        </span>
    </div>
</div>
