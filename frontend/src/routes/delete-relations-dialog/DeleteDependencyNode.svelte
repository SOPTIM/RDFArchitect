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
        faTrash,
        faShieldHalved,
        faLinkSlash,
        faArrowRight,
        faChevronRight,
    } from "@fortawesome/free-solid-svg-icons";
    import { slide } from "svelte/transition";
    import { Fa } from "svelte-fa";

    import FaIconButton from "$lib/components/FaIconButton.svelte";

    import DeleteDependencyNode from "./DeleteDependencyNode.svelte";

    let {
        node,
        selectedActions,
        onSelectAction,
        availableActions = ["DELETE", "KEEP", "REMOVE_REFERENCE"],
        depth = 0,
        isRoot = false,
        disabled = false,
    } = $props();

    const reasonLabels = {
        DELETION_REQUESTED_BY_USER: "Requested by user",
        REFENCES_DELETED_CLASS_VIA_ASSOCIATION: "References deleted class",
        CHILD_OF: "Child of deleted class",
        CONTAINED_IN_PACKAGE: "Contained in package",
        USES_DELETED_CLASS_AS_DATATYPE: "Uses deleted class as datatype",
    };

    const actionConfig = {
        DELETE: {
            icon: faTrash,
            text: "Delete",
            variant: "danger",
            width: "w-24",
            tooltip: "Permanently delete this resource and all its data",
        },
        KEEP: {
            icon: faShieldHalved,
            text: "Keep ref",
            variant: "default",
            width: "w-24",
            tooltip:
                "Keep this resource and its reference, even if the referenced target is deleted",
        },
        REMOVE_REFERENCE: {
            icon: faLinkSlash,
            text: "Remove ref",
            variant: "default",
            width: "w-30",
            tooltip:
                "Keep this resource but remove its reference to the deleted target",
        },
    };

    let expanded = $state(isRoot);

    let hasChildren = $derived(node.children?.length > 0);
    let currentAction = $derived(
        selectedActions.get(node.resourceIdentifier.uuid) ?? node.actions[0],
    );
    let typeBadge = $derived(node.type);
    let isAssociation = $derived(typeBadge === "ASSOCIATION");

    // Children are disabled if this node is not set to DELETE
    let childrenDisabled = $derived(disabled || currentAction !== "DELETE");

    function toggleExpand() {
        if (hasChildren) {
            expanded = !expanded;
        }
    }

    function selectAction(action) {
        onSelectAction(node.resourceIdentifier.uuid, action);
    }
</script>

<div>
    <!-- Node header -->
    <div
        class="flex items-center gap-2 py-2 pr-3 transition-colors"
        class:bg-background-subtle={depth === 0}
        class:hover:bg-background-subtle={depth > 0 && !disabled}
        class:opacity-40={disabled}
        style="padding-left: {depth * 1.25 + 0.75}rem;"
    >
        <!-- Expand/collapse toggle -->
        <button
            class="flex size-5 shrink-0 items-center justify-center rounded"
            class:text-text-subtle={hasChildren}
            class:text-transparent={!hasChildren}
            class:cursor-pointer={hasChildren && !disabled}
            class:cursor-default={!hasChildren || disabled}
            onclick={toggleExpand}
            disabled={!hasChildren || disabled}
            aria-label={expanded ? "Collapse" : "Expand"}
        >
            <span
                class="inline-flex text-xs transition-transform duration-150"
                class:rotate-90={expanded && hasChildren}
            >
                <Fa icon={faChevronRight} />
            </span>
        </button>

        <!-- Type badge -->
        <span
            class="shrink-0 rounded px-1.5 py-0.5 text-xs font-semibold tracking-wide uppercase"
            class:bg-lightblue={isAssociation}
            class:text-blue={isAssociation}
            class:bg-default-background={!isAssociation}
            class:text-gray={!isAssociation}
        >
            {typeBadge}
        </span>

        <!-- Label and reason -->
        <div class="flex min-w-0 flex-1 flex-col">
            {#if isAssociation && node.domain && node.target}
                <span
                    class="text-default-text flex items-center gap-1.5 truncate text-sm font-medium"
                >
                    <span>{node.target.label}</span>
                    <span class="text-text-subtle text-xs">
                        <Fa icon={faArrowRight} />
                    </span>
                    <span>{node.domain.label}</span>
                </span>
            {:else}
                <span class="text-default-text truncate text-sm font-medium">
                    {node.resourceIdentifier.label}
                </span>
            {/if}
            {#if node.reason && !isRoot}
                <span class="text-text-subtle truncate text-xs">
                    {reasonLabels[node.reason] ?? node.reason}
                </span>
            {/if}
        </div>

        <!-- Action buttons - fixed-width columns for alignment -->
        <div class="ml-auto flex shrink-0 gap-1.5">
            {#each availableActions as action}
                {@const config = actionConfig[action]}
                <div class={config.width}>
                    {#if node.actions.includes(action)}
                        <div
                            class="transition-opacity"
                            class:opacity-40={currentAction !== action &&
                                !disabled}
                            class:hover:opacity-70={currentAction !== action &&
                                !disabled}
                        >
                            <FaIconButton
                                callOnClick={() => selectAction(action)}
                                icon={config.icon}
                                text={config.text}
                                variant={currentAction === action
                                    ? config.variant
                                    : "default"}
                                title={config.tooltip}
                                disabled={disabled || isRoot}
                            />
                        </div>
                    {/if}
                </div>
            {/each}
        </div>
    </div>

    <!-- Children (recursive) -->
    {#if hasChildren && expanded}
        <div transition:slide={{ duration: 150 }}>
            {#each node.children as child, i (child.resourceIdentifier.uuid)}
                <!-- Separator line between sibling groups when a child itself has children -->
                {#if i > 0 && (node.children[i - 1]?.children?.length > 0 || child.children?.length > 0)}
                    <div
                        class="border-border-strong my-1 border-t"
                        style="margin-left: {(depth + 1) * 1.25 + 0.75}rem;"
                    ></div>
                {/if}
                <DeleteDependencyNode
                    node={child}
                    {selectedActions}
                    {onSelectAction}
                    {availableActions}
                    depth={depth + 1}
                    disabled={childrenDisabled}
                />
            {/each}
        </div>
    {/if}
</div>
