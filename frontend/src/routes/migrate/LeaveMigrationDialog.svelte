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
    import {
        faArrowRightFromBracket,
        faXmark,
    } from "@fortawesome/free-solid-svg-icons";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import AlertDialog from "$lib/dialog/AlertDialog.svelte";

    let {
        showDialog = $bindable(),
        onLeave = () => {},
        ...restProps
    } = $props();

    function leave() {
        showDialog = false;
        onLeave();
    }
</script>

<AlertDialog bind:showDialog {...restProps} size="w-full max-w-md">
    {#snippet title()}
        <span class="text-default-text text-lg leading-9 font-semibold">
            Nothing downloaded yet
        </span>
    {/snippet}

    {#snippet description()}
        <div class="text-text-subtle space-y-1 pt-2 pb-1">
            <p class="text-sm leading-relaxed">
                You have not downloaded any migration artifacts yet. Leaving now
                discards this migration, and the schemas have to be compared
                again to recreate it.
            </p>
            <p class="text-sm leading-relaxed">Do you want to leave anyway?</p>
        </div>
    {/snippet}
    <div class="flex flex-row justify-end gap-2 px-2 pb-2">
        <div>
            <FaIconButton
                callOnClick={() => (showDialog = false)}
                icon={faXmark}
                text="Stay"
                title="Continue the migration"
            />
        </div>

        <div>
            <FaIconButton
                callOnClick={leave}
                icon={faArrowRightFromBracket}
                variant="danger"
                text="Leave anyway"
                title="Discard the migration and leave"
            />
        </div>
    </div>
</AlertDialog>
