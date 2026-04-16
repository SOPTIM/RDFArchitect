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
        faArrowRightFromBracket,
        faChevronRight,
    } from "@fortawesome/free-solid-svg-icons";
    import { slide } from "svelte/transition";
    import { Fa } from "svelte-fa";

    import Tabs from "$lib/components/bitsui/tabs/Tabs.svelte";
    import TabsList from "$lib/components/bitsui/tabs/TabsList.svelte";
    import TabsTrigger from "$lib/components/bitsui/tabs/TabsTrigger.svelte";

    import DeleteDependencyNode from "./DeleteDependencyNode.svelte";

    let {
        node,
        selectedActions,
        onSelectAction,
        onBulkApplyToChildren = () => {},
        availableActions = [
            ACTIONS.DELETE,
            ACTIONS.KEEP,
            ACTIONS.REMOVE_PACKAGE_REFERENCE,
            ACTIONS.REMOVE_SUBCLASS_REFERENCE,
        ],
        depth = 0,
        isRoot = false,
        disabled = false,
    } = $props();

    const ACTIONS = {
        DELETE: "DELETE",
        KEEP: "KEEP",
        REMOVE_PACKAGE_REFERENCE: "REMOVE_PACKAGE_REFERENCE",
        REMOVE_SUBCLASS_REFERENCE: "REMOVE_SUBCLASS_REFERENCE",
    };

    const reasonLabels = {
        DELETION_REQUESTED_BY_USER: "Requested by user",
        REFENCES_DELETED_CLASS_VIA_ASSOCIATION: "References deleted class",
        CHILD_OF: "Child of deleted class",
        CONTAINED_IN_PACKAGE: "Contained in package",
        USES_DELETED_CLASS_AS_DATATYPE: "Uses deleted class as datatype",
    };

    const actionConfig = {
        [ACTIONS.DELETE]: {
            icon: faTrash,
            text: "Delete",
            variant: "danger",
            width: "w-24",
            tooltip: "Permanently delete this resource and all its data",
            bulkTooltip: "Delete all direct children",
        },
        [ACTIONS.KEEP]: {
            icon: faShieldHalved,
            text: "Keep",
            variant: "default",
            width: "w-24",
            tooltip:
                "Keep this resource and its reference, even if the referenced target is deleted",
            bulkTooltip: "Keep all direct children",
        },
        [ACTIONS.REMOVE_PACKAGE_REFERENCE]: {
            icon: faArrowRightFromBracket,
            text: "Move to default",
            variant: "default",
            width: "w-36",
            tooltip: "Keep this resource but remove its package reference",
            bulkTooltip:
                "Move all direct children out of the package reference",
        },
        [ACTIONS.REMOVE_SUBCLASS_REFERENCE]: {
            icon: faLinkSlash,
            text: "Remove parent",
            variant: "default",
            width: "w-36",
            tooltip: "Keep this resource but remove its inheritance reference",
            bulkTooltip:
                "Remove inheritance reference from all direct children",
        },
    };

    let expanded = $state(isRoot);

    let bulkValue = $state(null);

    let hasChildren = $derived(node.children?.length > 0);
    let actionKey = $derived(`${node.resourceIdentifier.uuid}::${node.reason}`);
    let currentAction = $derived(
        selectedActions.get(actionKey) ?? node.actions[0],
    );
    let typeBadge = $derived(node.type);
    let isAssociation = $derived(typeBadge === "ASSOCIATION");
    let singleAction = $derived(node.actions.length === 1);
    // Children are disabled if this node is not set to DELETE
    let childrenDisabled = $derived(
        disabled || currentAction !== ACTIONS.DELETE,
    );

    // Tabs are disabled when:
    //   - the node itself is disabled by its parent, OR
    //   - it is the root AND there is more than one action to choose from
    // (a root node with only a single action stays active so it isn't grayed out)
    let tabsDisabled = $derived(disabled || (isRoot && !singleAction));

    /**
     * For root: collect actions supported by at least one direct child,
     * so the bulk toggle only shows applicable actions.
     */
    let bulkApplicableActions = $derived.by(() => {
        if (!isRoot || !hasChildren) return new Set();
        const set = new Set();
        for (const child of node.children) {
            for (const a of child.actions) {
                set.add(a);
            }
        }
        return set;
    });

    function toggleExpand() {
        if (hasChildren) {
            expanded = !expanded;
        }
    }

    function handleValueChange(next) {
        if (next && next !== currentAction) {
            onSelectAction(actionKey, next);
        }
    }

    function handleBulkChange(next) {
        if (next) {
            onBulkApplyToChildren(node, next);
        }
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
                    {#if typeBadge === "ATTRIBUTE" && node.domain}
                        <span class="text-text-subtle text-xs font-normal">
                            ({node.domain.label})
                        </span>
                    {/if}
                </span>
            {/if}
            {#if node.reason && !isRoot}
                <span class="text-text-subtle truncate text-xs">
                    {reasonLabels[node.reason] ?? node.reason}
                </span>
            {/if}
        </div>

        <!-- Action tabs - fixed-width columns for alignment -->
        <div class="ml-auto shrink-0">
            <Tabs
                value={currentAction}
                onValueChange={handleValueChange}
                disabled={tabsDisabled}
            >
                <TabsList>
                    {#each availableActions as action}
                        {@const config = actionConfig[action]}
                        {#if action === ACTIONS.REMOVE_SUBCLASS_REFERENCE && availableActions.includes(ACTIONS.REMOVE_PACKAGE_REFERENCE)}
                            <!-- Skip, shared slot -->
                        {:else if action === ACTIONS.REMOVE_PACKAGE_REFERENCE}
                            {@const refAction = node.actions.find(
                                a =>
                                    a === ACTIONS.REMOVE_PACKAGE_REFERENCE ||
                                    a === ACTIONS.REMOVE_SUBCLASS_REFERENCE,
                            )}
                            <div class="w-36">
                                {#if refAction}
                                    {@const refConfig = actionConfig[refAction]}
                                    <TabsTrigger
                                        value={refAction}
                                        icon={refConfig.icon}
                                        text={refConfig.text}
                                        variant={refConfig.variant}
                                        title={refConfig.tooltip}
                                        disabled={tabsDisabled}
                                    />
                                {/if}
                            </div>
                        {:else}
                            <div class={config.width}>
                                {#if node.actions.includes(action)}
                                    <TabsTrigger
                                        value={action}
                                        icon={config.icon}
                                        text={config.text}
                                        variant={config.variant}
                                        title={config.tooltip}
                                        disabled={tabsDisabled}
                                    />
                                {/if}
                            </div>
                        {/if}
                    {/each}
                </TabsList>
            </Tabs>
        </div>
    </div>

    <!-- Children (recursive) + bulk-apply row -->
    {#if hasChildren && expanded}
        <div transition:slide={{ duration: 150 }}>
            {#if isRoot}
                <!-- Bulk-apply row (compact, only when expanded) -->
                <div
                    class="bg-background-subtle border-border flex items-center gap-3 border-t py-1 pr-3"
                    style="padding-left: {depth * 1.25 + 0.75 + 1.5}rem;"
                >
                    <span class="text-text-subtle shrink-0 text-xs">
                        Set all:
                    </span>
                    <div class="ml-auto shrink-0">
                        <Tabs
                            bind:value={bulkValue}
                            onValueChange={() => (bulkValue = null)}
                            {disabled}
                        >
                            <TabsList>
                                {#each availableActions as action}
                                    {@const config = actionConfig[action]}
                                    {#if action === ACTIONS.REMOVE_SUBCLASS_REFERENCE && availableActions.includes(ACTIONS.REMOVE_PACKAGE_REFERENCE)}
                                        <!-- Skip, shared slot -->
                                    {:else if action === ACTIONS.REMOVE_PACKAGE_REFERENCE}
                                        {@const refAction = [
                                            ACTIONS.REMOVE_PACKAGE_REFERENCE,
                                            ACTIONS.REMOVE_SUBCLASS_REFERENCE,
                                        ].find(a =>
                                            bulkApplicableActions.has(a),
                                        )}
                                        <div class="w-36">
                                            {#if refAction}
                                                {@const refConfig =
                                                    actionConfig[refAction]}
                                                <TabsTrigger
                                                    value={refAction}
                                                    icon={refConfig.icon}
                                                    text={refConfig.text}
                                                    variant={refConfig.variant}
                                                    title={refConfig.bulkTooltip}
                                                    {disabled}
                                                    onclick={() =>
                                                        handleBulkChange(
                                                            refAction,
                                                        )}
                                                />
                                            {/if}
                                        </div>
                                    {:else}
                                        <div class={config.width}>
                                            {#if bulkApplicableActions.has(action)}
                                                <TabsTrigger
                                                    value={action}
                                                    icon={config.icon}
                                                    text={config.text}
                                                    variant={config.variant}
                                                    title={config.bulkTooltip}
                                                    {disabled}
                                                    onclick={() =>
                                                        handleBulkChange(
                                                            action,
                                                        )}
                                                />
                                            {/if}
                                        </div>
                                    {/if}
                                {/each}
                            </TabsList>
                        </Tabs>
                    </div>
                </div>
            {/if}

            {#each node.children as child, i (child.resourceIdentifier.uuid)}
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
                    {onBulkApplyToChildren}
                    {availableActions}
                    depth={depth + 1}
                    disabled={childrenDisabled}
                />
            {/each}
        </div>
    {/if}
</div>
