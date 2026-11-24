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
    import { faPowerOff } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";

    import { desktopSession } from "./desktopSession.svelte.js";
</script>

{#if desktopSession.stopped}
    <div
        class="bg-dialog-backlight fixed inset-0 z-[60] flex items-center justify-center"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="desktop-stopped-title"
        aria-describedby="desktop-stopped-text"
    >
        <div
            class="border-border bg-window-background text-default-text flex max-w-md flex-col gap-3 rounded border p-5 shadow"
        >
            <div class="flex items-center gap-3">
                <div
                    class="bg-red flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white"
                >
                    <Fa icon={faPowerOff} />
                </div>
                <h2 id="desktop-stopped-title" class="text-base font-semibold">
                    RDFArchitect has stopped
                </h2>
            </div>
            <p id="desktop-stopped-text" class="text-sm">
                This window has lost its connection to RDFArchitect, so it can
                no longer show or change your workspaces. Start RDFArchitect
                again, then reload this window.
            </p>
            <div class="flex justify-end">
                <div class="h-9">
                    <ButtonControl
                        height={9}
                        callOnClick={() => window.location.reload()}
                    >
                        Reload
                    </ButtonControl>
                </div>
            </div>
        </div>
    </div>
{/if}
