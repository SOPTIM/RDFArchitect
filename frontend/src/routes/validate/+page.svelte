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
    import ValidationSection from "$lib/components/ValidationSection.svelte";
    import {
        validationState,
        ValidationKind,
    } from "$lib/sharedState.svelte.js";

    let result = $state(null);
    let context = $state(null);

    const isWorkspaceCheck = $derived(
        context?.kind === ValidationKind.WORKSPACE,
    );

    const title = $derived.by(() => {
        if (!isWorkspaceCheck) {
            return "Validation Result";
        }
        return context.graph
            ? `Workspace Validation Result: ${context.workspace} (${context.schemaLabel ?? context.graph})`
            : `Workspace Validation Result: ${context.workspace}`;
    });

    const subject = $derived.by(() => {
        if (!isWorkspaceCheck) {
            return "Schema";
        }
        return context.graph ? "Schema in workspace context" : "Workspace";
    });

    $effect(() => {
        validationState.result.subscribe();
        validationState.context.subscribe();
        result = validationState.result.getValue();
        context = validationState.context.getValue();
    });
</script>

<div class="bg-window-background flex h-full flex-col overflow-y-auto p-6">
    <ValidationSection
        {title}
        {result}
        {subject}
        workspace={context?.workspace}
        errorMessage="Use the menu to start a new validation."
    />
</div>
