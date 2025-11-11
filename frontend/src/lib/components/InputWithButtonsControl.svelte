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
        placeholder = "",
        value = $bindable(),
        callOnInput = () => {},
        callOnChange = () => {},
        id = crypto.randomUUID(),
        disabled = false,
        readonly = false,
        highlight = false,
        warn = false,
        list,
        title,
        type,
        buttons = [],
        height = 8,
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
<div
    class="input-group {showButtons ? 'input-group-hover' : ''}
           text-default-text
           h-{height} w-full rounded border border-solid font-[350] shadow-xs transition-colors
           {warn
        ? 'border-red focus-within:border-red'
        : highlight
          ? 'border-blue focus-within:border-blue'
          : readonly || disabled
            ? 'border-default-background focus-within:border-lightblue'
            : 'border-button-border focus-within:border-blue'}"
    onmouseenter={() => (isHover = true)}
    onmouseleave={() => (isHover = false)}
    onfocusin={() => (groupFocus = true)}
    onfocusout={() => (groupFocus = false)}
    role="none"
>
    <div
        class="focus-within:bg-lightblue relative flex size-full items-center rounded-sm
               border border-solid
               px-1
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
        <input
            {type}
            {id}
            class="w-full flex-1 bg-transparent outline-none disabled:bg-transparent
                   {type === 'number'
                ? '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'
                : ''}"
            {placeholder}
            bind:value
            oninput={() => callOnInput(value)}
            onchange={() => callOnChange(value)}
            {disabled}
            {readonly}
            {list}
            {title}
        />

        <div
            class="buttons flex items-center gap-1 overflow-hidden transition-transform"
            style="
                opacity: {showButtons ? 1 : 0};
                width: {showButtons ? 'auto' : '0'};
                pointer-events: {showButtons ? 'auto' : 'none'};
            "
        >
            {#each buttons as button}
                <div class=" flex size-6 items-center first:pl-1">
                    <ButtonControl
                        callOnClick={button.callOnClick}
                        title={button.title}
                        disabled={button.disabled}
                        height={8}
                        variant={groupFocus ? "inlineContrast" : "inline"}
                    >
                        <div class="flex size-full items-center justify-center">
                            <Fa icon={button.icon} />
                        </div>
                    </ButtonControl>
                </div>
            {/each}
        </div>
    </div>
</div>

<style>
    /* HIDE BUTTONS BY DEFAULT */
    .input-group .buttons {
        opacity: 0;
        pointer-events: none;
        width: 0;
        overflow: hidden;
        transition:
            opacity 140ms ease,
            width 140ms ease;
    }

    /* SHOW ONLY ON HOVER */
    .input-group-hover:hover .buttons {
        opacity: 1;
        pointer-events: auto;
        width: auto;
        min-width: fit-content;
    }
</style>
