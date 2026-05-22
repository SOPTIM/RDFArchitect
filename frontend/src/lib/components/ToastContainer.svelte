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
  -->

<script>
    import Toast from "$lib/components/Toast.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

    /**
     * Global toast outlet. Mount exactly once near the root of the app
     * (see `src/routes/+layout.svelte`). Toasts are emitted through
     * {@link $lib/eventhandling/toastStore.svelte.js}.
     */

    const toasts = $derived(toastStore.getToasts());

    function dismiss(id) {
        toastStore.dismiss(id);
    }
</script>

<div
    class="pointer-events-none fixed right-4 bottom-4 z-50 flex flex-col-reverse gap-2"
    aria-label="Notifications"
>
    {#each toasts as toast (toast.id)}
        <Toast {toast} onDismiss={dismiss} />
    {/each}
</div>
