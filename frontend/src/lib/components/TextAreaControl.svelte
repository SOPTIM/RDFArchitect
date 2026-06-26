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
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";

    let {
        rowcount = 5,
        placeholder = "",
        value = $bindable(),
        callOnInput = () => {},
        callOnChange = () => {},
        legend,
        disabled = false,
        readonly = false,
        id = crypto.randomUUID(),
        highlight = false,
        warn = false,
        buttons = [],
    } = $props();

    let groupFocus = $state(false);
    let isHover = $state(false);
    let ctrlPressed = $state(false);

    let showButtons = $derived(isHover && !ctrlPressed);
</script>

<svelte:window
    onkeydown={e => (ctrlPressed = e.ctrlKey)}
    onkeyup={e => (ctrlPressed = e.ctrlKey)}
/>

<div class="text-default-text flex h-full w-full flex-col">
    {#if legend}
        <label class="block" for={id}>{legend}</label>
    {/if}

    <!-- nimmt verfügbare Fläche ein -->
    <div class="min-h-0 flex-1">
        <div
            class="input-group {showButtons ? 'input-group-hover' : ''}

                   text-default-text
                   h-full w-full rounded-xs border
                   border-solid font-[350] shadow-xs
                   transition-colors
                   {warn
                ? 'border-red'
                : highlight
                  ? 'border-blue'
                  : 'border-button-border'}
                   {readonly || disabled
                ? 'border-default-background focus-within:border-lightblue'
                : 'focus-within:border-blue'}"
            onmouseenter={() => (isHover = true)}
            onmouseleave={() => (isHover = false)}
            onfocusin={() => (groupFocus = true)}
            onfocusout={() => (groupFocus = false)}
            role="none"
        >
            <div
                class="focus-within:bg-lightblue relative flex h-full w-full items-stretch rounded-[px] border border-solid px-1
                       transition-colors
                       {warn
                    ? 'border-red'
                    : highlight
                      ? 'border-blue'
                      : 'border-transparent'}
                       {readonly || disabled
                    ? 'bg-default-background border-default-background'
                    : 'bg-input-default-background'}"
            >
                <textarea
                    {id}
                    class="h-full min-h-0 w-full flex-1 resize-none bg-transparent p-1 pr-16 outline-none
                           read-only:bg-transparent disabled:bg-transparent"
                    {placeholder}
                    bind:value
                    rows={rowcount}
                    oninput={() => callOnInput(value)}
                    onchange={() => callOnChange(value)}
                    {disabled}
                    {readonly}
                ></textarea>

                <!-- unten rechts -->
                <div
                    class="buttons absolute right-1 bottom-1 flex items-center gap-1 overflow-hidden"
                    style="
                        opacity: {showButtons ? 1 : 0};
                        width: {showButtons ? 'auto' : '0'};
                        pointer-events: {showButtons ? 'auto' : 'none'};
                    "
                >
                    {#each buttons as button}
                        <div class="flex size-8 items-center">
                            <ButtonControl
                                callOnClick={button.callOnClick}
                                title={button.title}
                                disabled={button.disabled}
                                height={8}
                                variant={groupFocus
                                    ? "inlineContrast"
                                    : "inline"}
                            >
                                <div
                                    class="flex size-full items-center justify-center"
                                >
                                    <Fa icon={button.icon} />
                                </div>
                            </ButtonControl>
                        </div>
                    {/each}
                </div>
            </div>
        </div>
    </div>
</div>

<style>
    /* Buttons standardmäßig ausblenden */
    .input-group .buttons {
        opacity: 0;
        pointer-events: none;
        width: 0;
        transition:
            opacity 140ms ease,
            width 140ms ease;
        white-space: nowrap;
    }

    /* Einblenden beim Hover */
    .input-group-hover:hover .buttons {
        opacity: 1;
        pointer-events: auto;
        width: auto;
        min-width: fit-content;
    }
</style>
