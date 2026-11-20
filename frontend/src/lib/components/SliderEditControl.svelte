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
    import { Slider as BitsUiSlider } from "bits-ui";

    import InputWithButtonsControl from "$lib/components/InputWithButtonsControl.svelte";
    import {
        parseRangeInput,
        roundToDecimals,
        sliderScale,
        snapToStep,
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
    let sliderStep = $derived(options ? 1 : (step ?? 10 ** -decimals));
    let stepped = $derived(Boolean(options || step));
    let selectedOption = $derived(
        options?.find(option => option.value === value),
    );

    /**
     * The step the thumb stands on. The slider only takes values on its steps and would overwrite
     * any other value with the nearest step, so it is handed the step nearest to the value, while
     * the text field keeps a typed value between two steps.
     */
    let position = $derived.by(() => {
        if (options) {
            return Math.max(0, options.indexOf(selectedOption));
        }
        const parsed = parseRangeInput(value);
        const bounded =
            parsed === null ? lower : Math.min(upper, Math.max(lower, parsed));
        return snapToStep(bounded, lower, upper, sliderStep);
    });

    $effect(() => {
        if (value !== shownValue) {
            shownValue = value;
            text = String(value ?? "");
        }
    });

    function show(nextValue) {
        shownValue = value = nextValue;
        text = String(nextValue);
    }

    function slide(nextPosition) {
        show(options ? options[nextPosition].value : nextPosition);
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
    <label for={options ? undefined : id} {title}>
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
        <div class="flex-1 px-2" class:opacity-60={disabled || readonly}>
            <BitsUiSlider.Root
                type="single"
                value={position}
                onValueChange={slide}
                min={lower}
                max={upper}
                step={sliderStep}
                thumbPositioning="exact"
                disabled={disabled || readonly}
                class="group relative mb-2 block h-8 cursor-pointer data-[disabled]:cursor-default"
                {title}
            >
                {#snippet children({ tickItems })}
                    {@const scale = sliderScale(tickItems.length, stepped)}
                    <span
                        class="bg-border-strong absolute inset-x-0 top-1/2 -mt-0.75 h-1.5"
                    ></span>
                    <BitsUiSlider.Range
                        class="bg-blue group-data-[disabled]:bg-button-disabled-background top-1/2 -mt-0.75 h-1.5"
                    />
                    {#each tickItems as tick (tick.index)}
                        {#if tick.index === 0 || tick.index === tickItems.length - 1}
                            <BitsUiSlider.Tick
                                index={tick.index}
                                class="bg-text-subtle top-1/2 -mt-2.25 h-4.5 w-0.5 rounded-[1px]"
                            />
                        {:else if scale.innerTicks}
                            <BitsUiSlider.Tick
                                index={tick.index}
                                class="bg-button-darkgray-background top-1/2 -mt-1.75 h-3.5 w-px"
                            />
                        {/if}
                    {/each}
                    {#each scale.labelIndices as index (index)}
                        <BitsUiSlider.TickLabel
                            {index}
                            position="bottom"
                            class="text-default-text -mt-1.5 text-xs/3.5 whitespace-nowrap"
                        >
                            {tickItems[index].value}
                        </BitsUiSlider.TickLabel>
                    {/each}
                    <BitsUiSlider.Thumb
                        index={0}
                        class="bg-blue group-data-[disabled]:bg-button-disabled-background top-1/2 -mt-2 size-4 rounded-full border-2 border-white shadow-[0_1px_2px_rgb(0_0_0/0.35)] outline-none focus-visible:shadow-[0_0_0_0.2rem_var(--color-button-hover-background)]"
                        aria-label={label}
                        aria-valuetext={selectedOption?.label}
                    />
                {/snippet}
            </BitsUiSlider.Root>
        </div>
    </div>
</div>
