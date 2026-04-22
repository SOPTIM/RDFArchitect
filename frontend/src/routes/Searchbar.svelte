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
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    const backend = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    const filters = [
        { name: "All Datasets", value: "all" },
        { name: "Current Dataset", value: "dataset" },
        { name: "Current Schema", value: "graph" },
        { name: "Current Package", value: "package" },
    ];

    let showScopeDropdown = $state(false);
    let selectedFilter = $state({ name: "All Datasets", value: "all" });
    let queryString = $state("");
    let searchResults = $state([]);

    function selectSubject(searchResult) {
        editorState.selectedDataset.updateValue(searchResult.datasetName);
        editorState.selectedGraph.updateValue(searchResult.graphUri);
        editorState.selectedPackageUUID.updateValue(
            searchResult.packageUUID ?? "default",
        );
        editorState.selectedCustomDiagramUUID.updateValue(null);

        if (searchResult.type === "CLASS") {
            editorState.selectedClassDataset.updateValue(
                searchResult.datasetName,
            );
            editorState.selectedClassGraph.updateValue(searchResult.graphUri);
            editorState.selectedClassUUID.updateValue(searchResult.uuid);
            editorState.focusedClassUUID.updateValue(searchResult.uuid);
        } else if (searchResult.type === "PACKAGE") {
            editorState.selectedClassUUID.updateValue(null);
            editorState.focusedClassUUID.updateValue(null);
            editorState.selectedPackageUUID.updateValue(searchResult.uuid);
        } else {
            editorState.selectedClassDataset.updateValue(
                searchResult.datasetName,
            );
            editorState.selectedClassGraph.updateValue(searchResult.graphUri);
            editorState.selectedClassUUID.updateValue(
                searchResult.parentClassUUID,
            );
            editorState.focusedClassUUID.updateValue(
                searchResult.parentClassUUID,
            );
        }
        editorState.selectedDataset.trigger();
        editorState.selectedGraph.trigger();
        editorState.selectedPackageUUID.trigger();
        forceReloadTrigger.trigger();
    }

    function submitQuery(event) {
        event?.preventDefault?.();

        const trimmedQuery = queryString.trim();
        if (trimmedQuery.length < 3) {
            searchResults = [];
            return;
        }

        const body = {
            datasetName:
                selectedFilter.value !== "all"
                    ? editorState.selectedDataset.getValue()
                    : null,
            graphUri:
                selectedFilter.value !== "dataset" &&
                selectedFilter.value !== "all"
                    ? editorState.selectedGraph.getValue()
                    : null,
            packageUUID:
                selectedFilter.value === "package"
                    ? editorState.selectedPackageUUID.getValue()
                    : null,
        };
        fetchSearchResults(trimmedQuery, body);
    }

    async function fetchSearchResults(query, body) {
        const tempSearchResults = [];
        const response = await backend.getSearchResults(query, body);
        if (response.ok) {
            const res = await response.json();
            if (
                res.internalSearchResults.length === 0 &&
                res.externalSearchResults.length === 0
            ) {
                tempSearchResults.push({
                    message: "No results found",
                });
            } else {
                for (const searchResult of res.internalSearchResults) {
                    tempSearchResults.push({
                        ...searchResult,
                        external: false,
                    });
                }
                for (const searchResult of res.externalSearchResults) {
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
                return a.label.value.localeCompare(b.label.value);
            });
            searchResults = tempSearchResults;
        } else {
            console.error(
                "Error fetching search results:",
                response.statusText,
            );
        }
    }

    function formatSearchResult(result) {
        let path = `${result.datasetName}/${getSuffix(result.graphUri)}/`;
        if (result.type !== "PACKAGE") {
            if (result.packageLabel) {
                path += `${result.packageLabel.value}/`;
            } else {
                path += `default/`;
            }
        }
        if (result.parentClassUri) {
            path += `${result.parentClassUri.suffix}/`;
        }
        return path;
    }

    function getSuffix(uri) {
        const parts = uri.split("#");
        return parts[parts.length - 1];
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
</script>

<form onsubmit={submitQuery}>
    <div class="flex h-9 space-x-1.5">
        <div
            class="relative"
            use:clickOutside
            onoutclick={() => {
                showScopeDropdown = false;
            }}
        >
            <div class="w-44">
                <ButtonControl
                    height={9}
                    callOnClick={() => (showScopeDropdown = !showScopeDropdown)}
                >
                    <span class="relative flex w-40 items-center">
                        <span class="mx-auto text-center">
                            {selectedFilter.name}
                        </span>
                        <Fa
                            icon={faCaretDown}
                            class={`absolute right-0 transform transition-transform duration-200 ${showScopeDropdown ? "rotate-180" : ""}`}
                        />
                    </span>
                </ButtonControl>
            </div>
            {#if showScopeDropdown}
                <div
                    class="border-border bg-window-background absolute z-50 mt-0.5 flex w-44 flex-col rounded border text-sm shadow"
                >
                    {#each filters as filter}
                        <button
                            class="text-blue border-window-background hover:border-blue hover:bg-lightblue disabled:text-button-disabled-background border px-4 py-2 first:rounded-t last:rounded-b hover:cursor-pointer disabled:cursor-not-allowed"
                            onclick={() => {
                                selectedFilter = filter;
                                showScopeDropdown = false;
                            }}
                        >
                            {filter.name}
                        </button>
                    {/each}
                </div>
            {/if}
        </div>
        <div class="relative h-full w-full flex-col">
            <input
                type="text"
                id="query-string"
                autocomplete="off"
                bind:value={queryString}
                placeholder="Search..."
                class="bg-input-default-background text-default-text focus:border-blue border-input-default-background disabled:bg-button-disabled-background read-only:bg-default-background h-full w-full rounded border p-1 px-2 font-[350] outline-none"
                oninput={submitQuery}
            />

            <div
                class="bg-window-background border-border absolute top-full right-0 left-0 z-50 mt-0.5 max-h-72 overflow-y-auto rounded border shadow {searchResults.length ===
                0
                    ? 'hidden'
                    : ''}"
                use:clickOutside
                onoutclick={() => {
                    searchResults = [];
                }}
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
                                selectSubject(result);
                                searchResults = [];
                            }}
                            onclick={() => {
                                selectSubject(result);
                                searchResults = [];
                            }}
                        >
                            <div class="overflow-auto">
                                <p class="text-xs font-light">
                                    {formatSearchResult(result)}
                                </p>
                                <p
                                    title={result.label.value}
                                    class="flex items-center gap-2"
                                >
                                    {#if result.external}
                                        <span class="font-bold">
                                            [external]
                                        </span>
                                    {/if}
                                    {result.label.value}
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

        <div class="relative">
            <ButtonControl
                type="submit"
                aria-labelledby="search"
                disabled={queryString.trim().length < 3}
                height={9}
            >
                <Fa icon={faMagnifyingGlass} />
            </ButtonControl>
        </div>
    </div>
</form>
