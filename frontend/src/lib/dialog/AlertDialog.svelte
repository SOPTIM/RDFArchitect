<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import { faExclamation } from "@fortawesome/free-solid-svg-icons";
    import { AlertDialog as BitsUiAlertDialog } from "bits-ui";
    import { Fa } from "svelte-fa";

    let {
        showDialog = $bindable(),
        size = "w-full max-w-md",
        onkeydown,
        title,
        description,
        children,
        ...restProps
    } = $props();
</script>

<BitsUiAlertDialog.Root bind:open={showDialog}>
    <BitsUiAlertDialog.Portal>
        <BitsUiAlertDialog.Overlay
            class="bg-dialog-backlight fixed inset-0 z-40"
        />
        <BitsUiAlertDialog.Content
            {...restProps}
            class="border-border bg-window-background fixed top-1/2 left-1/2 z-40 -translate-x-1/2 -translate-y-1/2 rounded border border-solid p-2 shadow outline-none {size}"
            {onkeydown}
        >
            <div class="flex items-start gap-3 p-2">
                <div
                    class="bg-red flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white"
                >
                    <Fa icon={faExclamation} />
                </div>
                <div class="min-w-0">
                    {#if title}
                        <BitsUiAlertDialog.Title>
                            {@render title()}
                        </BitsUiAlertDialog.Title>
                    {/if}
                    <BitsUiAlertDialog.Description>
                        {@render description()}
                    </BitsUiAlertDialog.Description>
                </div>
            </div>
            {@render children?.()}
        </BitsUiAlertDialog.Content>
    </BitsUiAlertDialog.Portal>
</BitsUiAlertDialog.Root>
