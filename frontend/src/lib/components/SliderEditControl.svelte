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
    import InputWithButtonsControl from "$lib/components/InputWithButtonsControl.svelte";
    import {
        innerTickFractions,
        parseRangeInput,
        roundToDecimals,
        scaleLabels,
        snapToStep,
        valueAfterKey,
    } from "$lib/utils/rangeValue.js";

    let {
        label,
        value = $bindable(),
        min = 0,
        max = 100,
        step = undefined,
        decimals = 2,
        options = undefined,
        // TODO: clear and revert for sliders come with the user settings story. The buttons only reach the text field so far, not the slider and not the label of the options.
        buttons = [],
        id = crypto.randomUUID(),
        disabled = false,
        readonly = false,
        highlight = false,
        warn = false,
        title = undefined,
    } = $props();

    let text = $state("");
    let shownValue;

    let lower = $derived(options ? 0 : min);
    let upper = $derived(options ? options.length - 1 : max);
    let gridStep = $derived(options ? 1 : step);
    let ticks = $derived(innerTickFractions(lower, upper, gridStep));
    let labels = $derived(options ? [] : scaleLabels(lower, upper, gridStep));
    let selectedOption = $derived(
        options?.find(option => option.value === value),
    );

    let position = $derived.by(() => {
        if (options) {
            return Math.max(0, options.indexOf(selectedOption));
        }
        const parsed = parseRangeInput(value);
        return parsed === null
            ? lower
            : Math.min(upper, Math.max(lower, parsed));
    });

    let filled = $derived(
        upper > lower ? (position - lower) / (upper - lower) : 0,
    );

    $effect(() => {
        if (value !== shownValue) {
            shownValue = value;
            text = String(value ?? "");
        }
    });

    /**
     * Places an element at a fraction of the slider length, measured between the two thumb
     * centers at the ends.
     */
    function along(fraction) {
        return `calc(0.5rem + (100% - 1rem) * ${fraction})`;
    }

    function show(nextValue) {
        shownValue = value = nextValue;
        text = String(nextValue);
    }

    function showPosition(nextPosition) {
        show(options ? options[nextPosition].value : nextPosition);
    }

    function slide(event) {
        const raw = Number(event.currentTarget.value);
        const nextPosition = roundToDecimals(
            snapToStep(raw, lower, upper, gridStep),
            decimals,
        );
        event.currentTarget.value = String(nextPosition);
        showPosition(nextPosition);
    }

    function slideByKey(event) {
        const nextPosition = valueAfterKey(
            event.key,
            position,
            lower,
            upper,
            gridStep,
            decimals,
        );
        if (nextPosition === null) {
            return;
        }
        event.preventDefault();
        showPosition(nextPosition);
    }

    function type(nextText) {
        const parsed = parseRangeInput(nextText);
        shownValue = value =
            parsed === null ? nextText : roundToDecimals(parsed, decimals);
    }

    function commitTyped() {
        const parsed = parseRangeInput(text);
        if (parsed !== null) {
            show(roundToDecimals(parsed, decimals));
        }
    }
</script>

<div class="text-default-text flex h-full w-full flex-col">
    <label for={id} {title}>
        {#if label}
            {label}
        {/if}
    </label>
    <div class="flex items-start gap-3">
        <div class="w-24 shrink-0">
            {#if options}
                <span class="flex h-8 items-center truncate" {title}>
                    {selectedOption?.label ?? ""}
                </span>
            {:else}
                <InputWithButtonsControl
                    {id}
                    type="text"
                    inputmode="decimal"
                    bind:value={text}
                    callOnInput={type}
                    callOnChange={commitTyped}
                    {buttons}
                    {disabled}
                    {readonly}
                    {highlight}
                    {warn}
                    {title}
                />
            {/if}
        </div>
        <div class="flex-1" class:inactive={disabled || readonly}>
            <div class="relative h-8">
                {#each ticks as fraction}
                    <span class="tick" style:left={along(fraction)}></span>
                {/each}
                <span class="tick tick-end" style:left={along(0)}></span>
                <span class="tick tick-end" style:left={along(1)}></span>
                <div class="track"></div>
                <div
                    class="fill"
                    style:width="calc((100% - 1rem) * {filled})"
                ></div>
                <input
                    type="range"
                    class="slider"
                    id={options ? id : undefined}
                    min={lower}
                    max={upper}
                    step="any"
                    value={position}
                    oninput={slide}
                    onkeydown={slideByKey}
                    disabled={disabled || readonly}
                    aria-label={label}
                    aria-valuetext={selectedOption?.label}
                    {title}
                />
            </div>
            {#if labels.length > 0}
                <div class="relative -mt-1.5 h-3.5">
                    {#each labels as scaleLabel}
                        <span
                            class="scale-label"
                            style:left={along(scaleLabel.fraction)}
                        >
                            {scaleLabel.value}
                        </span>
                    {/each}
                </div>
            {/if}
        </div>
    </div>
</div>

<style>
    .inactive {
        opacity: 0.6;
    }

    .track,
    .fill {
        position: absolute;
        top: 50%;
        left: 0.5rem;
        height: 0.375rem;
        transform: translateY(-50%);
    }

    .track {
        right: 0.5rem;
        background: var(--color-border-strong);
    }

    .fill {
        background: var(--color-blue);
    }

    .inactive .fill {
        background: var(--color-button-disabled-background);
    }

    .tick {
        position: absolute;
        top: 50%;
        width: 1px;
        height: 0.875rem;
        transform: translate(-50%, -50%);
        background: var(--color-button-darkgray-background);
    }

    .tick-end {
        width: 2px;
        height: 1.125rem;
        border-radius: 1px;
        background: var(--color-text-subtle);
    }

    .scale-label {
        position: absolute;
        top: 0;
        transform: translateX(-50%);
        font-size: 0.75rem;
        line-height: 0.875rem;
        white-space: nowrap;
        color: var(--color-default-text);
    }

    .slider {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        margin: 0;
        appearance: none;
        background: transparent;
        cursor: pointer;
        outline: none;
    }

    .slider:disabled {
        cursor: default;
    }

    .slider::-moz-range-track {
        background: transparent;
    }

    .slider::-webkit-slider-thumb {
        box-sizing: border-box;
        width: 1rem;
        height: 1rem;
        border: 2px solid var(--color-white);
        border-radius: 9999px;
        appearance: none;
        background: var(--color-blue);
        box-shadow: 0 1px 2px rgb(0 0 0 / 0.35);
    }

    .slider::-moz-range-thumb {
        box-sizing: border-box;
        width: 1rem;
        height: 1rem;
        border: 2px solid var(--color-white);
        border-radius: 9999px;
        background: var(--color-blue);
        box-shadow: 0 1px 2px rgb(0 0 0 / 0.35);
    }

    .slider:focus-visible::-webkit-slider-thumb {
        box-shadow: 0 0 0 0.2rem var(--color-button-hover-background);
    }

    .slider:focus-visible::-moz-range-thumb {
        box-shadow: 0 0 0 0.2rem var(--color-button-hover-background);
    }

    .slider:disabled::-webkit-slider-thumb {
        background: var(--color-button-disabled-background);
    }

    .slider:disabled::-moz-range-thumb {
        background: var(--color-button-disabled-background);
    }
</style>
