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

    let {
        label,
        placeholder = "",
        value = $bindable(),
        callOnInput = () => {},
        optionValues = [],
        getOptionIsDisabled = () => false,
        id = crypto.randomUUID(),
        highlight = false,
        warn = false,
        disabled = false,
        readonly = false,
        buttons = [],
    } = $props();

    const listId = crypto.randomUUID();

    let containerRef = $state(null);
    let listPosition = $state("");
    let listRequested = $state(false);
    let highlightedIndex = $state(-1);

    let searchTerm = $derived(
        String(value ?? "")
            .trim()
            .toLowerCase(),
    );
    let suggestions = $derived(
        optionValues.filter(option =>
            String(option).toLowerCase().includes(searchTerm),
        ),
    );
    let inputIsOnlySuggestion = $derived(
        suggestions.length === 1 &&
            String(suggestions[0]).toLowerCase() === searchTerm,
    );
    let listOpen = $derived(
        listRequested && suggestions.length > 0 && !inputIsOnlySuggestion,
    );

    $effect(() => {
        if (!listOpen) {
            return;
        }
        const reposition = () => positionList();
        document.addEventListener("scroll", reposition, {
            capture: true,
            passive: true,
        });
        return () =>
            document.removeEventListener("scroll", reposition, {
                capture: true,
            });
    });

    function positionList() {
        const rect = containerRef?.getBoundingClientRect();
        if (!rect) {
            return;
        }
        listPosition = `left: ${rect.left}px; top: ${rect.bottom + 4}px; width: ${rect.width}px;`;
    }

    function openList() {
        if (disabled || readonly) {
            return;
        }
        positionList();
        listRequested = true;
    }

    function closeList() {
        listRequested = false;
        highlightedIndex = -1;
    }

    function selectSuggestion(option) {
        if (getOptionIsDisabled(option)) {
            return;
        }
        value = option;
        callOnInput(option);
        closeList();
    }

    function moveHighlight(step) {
        if (!listOpen) {
            openList();
            return;
        }
        const count = suggestions.length;
        let index =
            highlightedIndex === -1
                ? step > 0
                    ? 0
                    : count - 1
                : (highlightedIndex + step + count) % count;

        for (let tries = 0; tries < count; tries++) {
            if (!getOptionIsDisabled(suggestions[index])) {
                highlightedIndex = index;
                return;
            }
            index = (index + step + count) % count;
        }
        highlightedIndex = -1;
    }

    function handleInput(newValue) {
        openList();
        highlightedIndex = -1;
        callOnInput(newValue);
    }

    function handleKeyDown(event) {
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            moveHighlight(event.key === "ArrowDown" ? 1 : -1);
            return;
        }
        if (!listOpen) {
            return;
        }
        if (event.key === "Enter" && highlightedIndex >= 0) {
            event.preventDefault();
            selectSuggestion(suggestions[highlightedIndex]);
            return;
        }
        if (event.key === "Escape") {
            event.preventDefault();
            event.stopPropagation();
            closeList();
        }
    }

    function handleFocusOut(event) {
        if (!containerRef?.contains(event.relatedTarget)) {
            closeList();
        }
    }

    function portalToBody(node) {
        document.body.appendChild(node);
        return {
            destroy: () => node.remove(),
        };
    }
</script>

<svelte:window onresize={() => listOpen && positionList()} />

<div class="block h-full w-full">
    <label for={id} class="text-default-text w-full font-semibold">
        {#if label}
            {label}
        {/if}
    </label>
    <div
        bind:this={containerRef}
        onfocusin={openList}
        onfocusout={handleFocusOut}
        onkeydown={handleKeyDown}
        role="none"
    >
        <InputWithButtonsControl
            type="text"
            {id}
            {highlight}
            {warn}
            {placeholder}
            bind:value
            callOnInput={handleInput}
            {disabled}
            {readonly}
            {buttons}
            role="combobox"
            aria-autocomplete="list"
            aria-expanded={listOpen}
            aria-controls={listId}
            aria-activedescendant={highlightedIndex >= 0
                ? `${listId}-${highlightedIndex}`
                : undefined}
        />
    </div>
</div>

{#if listOpen}
    <ul
        use:portalToBody
        id={listId}
        role="listbox"
        data-dialog-layer
        class="menu-surface pointer-events-auto fixed max-h-60 overflow-y-auto"
        style={listPosition}
        data-state="open"
        onmousedown={event => event.preventDefault()}
    >
        {#each suggestions as option, index}
            <li
                id="{listId}-{index}"
                role="option"
                aria-selected={index === highlightedIndex}
                aria-disabled={getOptionIsDisabled(option)}
                class="menu-item truncate"
                class:menu-item--interactive={!getOptionIsDisabled(option)}
                class:menu-item--disabled={getOptionIsDisabled(option)}
                class:menu-item--highlighted={index === highlightedIndex}
                onclick={() => selectSuggestion(option)}
            >
                {option}
            </li>
        {/each}
    </ul>
{/if}

<style>
    .menu-item--highlighted {
        background: var(--color-button-hover-background);
        color: var(--color-button-hover-text);
    }
</style>
