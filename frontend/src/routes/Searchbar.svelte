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
        faCaretDown,
        faMagnifyingGlass,
        faXmark,
    } from "@fortawesome/free-solid-svg-icons";
    import { onDestroy, onMount, tick } from "svelte";
    import { Fa } from "svelte-fa";

    import { search } from "$lib/api/generated/index.ts";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import {
        navigateToClass,
        navigateToPackage,
    } from "$lib/utils/model-navigation.js";
    import { getPackageDisplayLabel } from "$lib/utils/package-label.js";

    const filters = [
        { name: "All Workspaces", short: "All", value: "all" },
        { name: "Current Workspace", short: "Workspace", value: "workspace" },
        { name: "Current Schema", short: "Schema", value: "graph" },
        { name: "Current Package", short: "Package", value: "package" },
    ];

    const shortcutsUnregister = [];

    let showScopeDropdown = $state(false);
    let selectedFilter = $state(filters[0]);
    let queryString = $state("");
    let searchResults = $state([]);
    let inputElement = $state(null);
    let toggleWrapper = $state(null);
    // Below the md breakpoint the field folds into a button and opens below the toolbar; on wider
    // windows it is always shown and this stays false.
    let open = $state(false);
    // Both lists sit below the field, so the results give way while the scope list is open.
    let showResults = $derived(searchResults.length > 0 && !showScopeDropdown);

    onMount(() => {
        shortcutsUnregister.push(
            shortcutStore.register("focusSearch", ["ctrl", "f"], () =>
                openSearch(),
            ),
        );
    });

    onDestroy(() => {
        shortcutsUnregister.forEach(unregister => unregister());
    });

    function selectSubject(searchResult) {
        const target = {
            workspaceName: searchResult.workspaceName,
            schemaName: searchResult.schemaName,
            packageUUID: searchResult.packageUUID,
        };
        if (searchResult.type === "PACKAGE") {
            navigateToPackage({ ...target, packageUUID: searchResult.uuid });
        } else if (searchResult.type === "CLASS") {
            navigateToClass({ ...target, classUUID: searchResult.uuid });
        } else {
            // Attributes, associations, enum entries: select their owning class and reveal the row.
            navigateToClass({
                ...target,
                classUUID: searchResult.parentClassUUID,
                propertyUUID: searchResult.uuid,
            });
        }
    }

    function submitQuery(event) {
        event?.preventDefault?.();

        const trimmedQuery = queryString.trim();
        if (trimmedQuery.length < 3) {
            searchResults = [];
            return;
        }

        const body = {
            workspaceName:
                selectedFilter.value !== "all"
                    ? editorState.selectedWorkspace.getValue()
                    : null,
            schemaName:
                selectedFilter.value !== "workspace" &&
                selectedFilter.value !== "all"
                    ? editorState.selectedGraph.getValue()
                    : null,
            packageUUID:
                selectedFilter.value === "package"
                    ? editorState.selectedDiagram.getProperty("id")
                    : null,
        };
        fetchSearchResults(trimmedQuery, body);
    }

    async function fetchSearchResults(query, body) {
        const tempSearchResults = [];
        const { data, error } = await search({
            query: { query: query },
            body: body,
        });
        if (!error) {
            if (
                data.internalSearchResults.length === 0 &&
                data.externalSearchResults.length === 0
            ) {
                tempSearchResults.push({
                    message: "No results found",
                });
            } else {
                for (const searchResult of data.internalSearchResults) {
                    tempSearchResults.push({
                        ...searchResult,
                        external: false,
                    });
                }
                for (const searchResult of data.externalSearchResults) {
                    tempSearchResults.push({
                        ...searchResult,
                        external: true,
                    });
                }
            }
            tempSearchResults.sort((a, b) => {
                if (!("label" in a) || !("label" in b)) {
                    return 0;
                }
                return getSearchResultLabel(a).localeCompare(
                    getSearchResultLabel(b),
                );
            });
            searchResults = tempSearchResults;
        } else {
            console.error("Error fetching search results:", error);
            toastStore.error(
                "Search failed",
                "Could not fetch search results. Please try again.",
            );
        }
    }

    function formatSearchResult(result) {
        let path = `${result.workspaceName}/${result.schemaName}/`;
        if (result.type !== "PACKAGE") {
            if (result.packageLabel) {
                path += `${getPackageDisplayLabel(result.packageLabel.value)}/`;
            } else {
                path += `default/`;
            }
        }
        if (result.parentClassUri) {
            path += `${result.parentClassUri.suffix}/`;
        }
        return path;
    }

    function getSearchResultLabel(result) {
        if (result.type === "PACKAGE") {
            return getPackageDisplayLabel(result.label.value);
        }
        return result.label.value;
    }

    export function clickOutside(node) {
        const handleClick = event => {
            if (!node.contains(event.target)) {
                node.dispatchEvent(new CustomEvent("outclick"));
            }
        };

        document.addEventListener("click", handleClick, true);

        return {
            destroy() {
                document.removeEventListener("click", handleClick, true);
            },
        };
    }

    /** Whether the window is narrower than the md breakpoint, where the field folds away. */
    function isFolded() {
        return !window.matchMedia?.("(min-width: 48rem)").matches;
    }

    async function openSearch() {
        open = isFolded();
        await tick();
        inputElement?.focus();
        inputElement?.select();
    }

    function closeSearch({ returnFocus = false } = {}) {
        showScopeDropdown = false;
        if (!open) {
            return;
        }
        open = false;
        if (returnFocus) {
            toggleWrapper?.querySelector("button")?.focus();
        }
    }

    function chooseFilter(filter) {
        selectedFilter = filter;
        showScopeDropdown = false;
        inputElement?.focus();
        submitQuery();
    }

    function chooseResult(result) {
        selectSubject(result);
        searchResults = [];
        closeSearch();
    }

    /** Esc takes back one thing at a time: the scope list, then the results, then the field. */
    function handleKeydown(event) {
        if (event.key !== "Escape") {
            return;
        }
        if (showScopeDropdown) {
            showScopeDropdown = false;
        } else if (searchResults.length > 0) {
            searchResults = [];
        } else if (open && isFolded()) {
            closeSearch({ returnFocus: true });
        } else {
            return;
        }
        // Answered here, so the window does not also take it as Esc for the editor.
        event.preventDefault();
    }

    function handleFocusOut(event) {
        if (
            event.relatedTarget &&
            !event.currentTarget.contains(event.relatedTarget)
        ) {
            closeSearch();
        }
    }
</script>

<!-- The toolbar is positioned, so the folded field opens as a panel right below it. -->
<div
    class="flex justify-end"
    role="search"
    use:clickOutside
    onoutclick={() => {
        searchResults = [];
        closeSearch();
    }}
    onfocusout={handleFocusOut}
>
    <!-- Hidden through a wrapper: app.css forces the display of every button. -->
    <div class="shrink-0 md:hidden" bind:this={toggleWrapper}>
        <ButtonControl
            callOnClick={() => (open ? closeSearch() : openSearch())}
            height={9}
            title="Search (Ctrl+F)"
            aria-label="Search"
            aria-expanded={open}
            aria-controls="toolbar-search"
        >
            <Fa icon={faMagnifyingGlass} fw />
        </ButtonControl>
    </div>
    <!-- Esc is answered for the controls inside, all of which take the keyboard themselves. -->
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <form
        id="toolbar-search"
        class={`flex w-full items-center gap-2 ${
            open
                ? "max-md:border-border max-md:bg-window-background max-md:absolute max-md:inset-x-2 max-md:top-full max-md:z-50 max-md:mt-1 max-md:w-auto max-md:rounded-md max-md:border max-md:p-2 max-md:shadow-lg"
                : "max-md:hidden"
        }`}
        onsubmit={submitQuery}
        onkeydown={handleKeydown}
    >
        <div
            class="border-button-border focus-within:border-blue focus-within:bg-lightblue relative flex h-9 min-w-0 flex-1 items-stretch rounded border bg-white"
        >
            <div
                class="relative flex shrink-0"
                use:clickOutside
                onoutclick={() => {
                    showScopeDropdown = false;
                }}
            >
                <button
                    type="button"
                    class="text-blue hover:bg-lightblue focus-visible:outline-blue flex cursor-pointer items-center gap-1.5 rounded-l px-2.5 text-sm whitespace-nowrap focus-visible:outline-2 focus-visible:-outline-offset-2"
                    title={`Search in: ${selectedFilter.name}`}
                    aria-label={`Search in: ${selectedFilter.name}`}
                    aria-expanded={showScopeDropdown}
                    onclick={() => (showScopeDropdown = !showScopeDropdown)}
                >
                    {selectedFilter.short}
                    <Fa
                        icon={faCaretDown}
                        class={`transition-transform duration-200 ${showScopeDropdown ? "rotate-180" : ""}`}
                    />
                </button>
                {#if showScopeDropdown}
                    <div
                        class="border-border bg-window-background absolute top-full left-0 z-50 mt-0.5 flex w-44 flex-col rounded border text-sm shadow"
                    >
                        {#each filters as filter (filter.value)}
                            <button
                                type="button"
                                class="text-blue border-window-background hover:border-blue hover:bg-lightblue border px-4 py-2 first:rounded-t last:rounded-b hover:cursor-pointer"
                                aria-current={filter.value ===
                                    selectedFilter.value}
                                onclick={() => chooseFilter(filter)}
                            >
                                {filter.name}
                            </button>
                        {/each}
                    </div>
                {/if}
            </div>
            <span
                class="bg-button-border my-1.5 w-px shrink-0"
                aria-hidden="true"
            ></span>
            <input
                type="text"
                id="query-string"
                autocomplete="off"
                aria-label="Search"
                bind:this={inputElement}
                bind:value={queryString}
                placeholder="Search..."
                class="text-default-text h-full min-w-0 flex-1 bg-transparent px-2 font-[350] outline-none"
                oninput={submitQuery}
            />
            <button
                type="submit"
                class="text-blue disabled:text-button-hover-background flex shrink-0 cursor-pointer items-center px-2.5 disabled:cursor-not-allowed"
                aria-label="Run search"
                disabled={queryString.trim().length < 3}
            >
                <Fa icon={faMagnifyingGlass} />
            </button>

            <div
                class="bg-window-background border-border absolute top-full right-0 z-50 mt-0.5 max-h-72 w-full min-w-[min(26rem,calc(100vw-1rem))] overflow-y-auto rounded border shadow {showResults
                    ? ''
                    : 'hidden'}"
            >
                {#each searchResults as result}
                    {#if "message" in result}
                        <div
                            class="text-default-text px-4 py-2 text-sm font-[350]"
                        >
                            {result.message}
                        </div>
                    {:else}
                        <div
                            class="text-default-text hover:bg-button-default-background hover:text-button-default-text flex w-full justify-between px-4 py-2 text-sm font-[350] transition-colors"
                            role="button"
                            tabindex={0}
                            style="cursor: pointer"
                            onkeydown={e => {
                                if (e.key !== "Enter") {
                                    return;
                                }
                                chooseResult(result);
                            }}
                            onclick={() => chooseResult(result)}
                        >
                            <div class="overflow-auto">
                                <p class="text-xs font-light">
                                    {formatSearchResult(result)}
                                </p>
                                <p
                                    title={getSearchResultLabel(result)}
                                    class="flex items-center gap-2"
                                >
                                    {#if result.external}
                                        <span class="font-bold">
                                            [external]
                                        </span>
                                    {/if}
                                    {getSearchResultLabel(result)}
                                </p>
                            </div>
                            <div class="ml-5 flex items-center">
                                {result.type}
                            </div>
                        </div>
                    {/if}
                {/each}
            </div>
        </div>
        {#if open}
            <div class="shrink-0 md:hidden">
                <button
                    type="button"
                    class="text-default-text hover:bg-nav-hover-background flex h-9 w-9 cursor-pointer items-center justify-center rounded"
                    aria-label="Close search"
                    onclick={() => closeSearch({ returnFocus: true })}
                >
                    <Fa icon={faXmark} />
                </button>
            </div>
        {/if}
    </form>
</div>
