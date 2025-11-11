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
        faCaretRight,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let {
        label,
        level = 1,
        isSelected = false,
        disabled = false,
        hasChildren = false,
        expanded = false,
        icon = null,
        secondaryLabel = "",
        badgeText = "",
        badgeVariant = "default",
        highlightLabel = "",
        title,
        onToggle,
        onclick,
        ...restProps
    } = $props();

    const badgeClassMap = {
        default: "",
        external: "nav-entry__badge--external",
        readonly: "nav-entry__badge--readonly",
    };

    function handleClick(event) {
        if (disabled) {
            event.preventDefault();
            return;
        }
        onclick?.(event);
        if (onToggle && event?.detail % 2 === 0) {
            onToggle(event);
        }
    }

    function handleToggle(event) {
        if (disabled || !onToggle) {
            event.preventDefault();
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        onToggle(event);
    }

    function badgeClass(variant) {
        return badgeClassMap[variant] ?? "";
    }
</script>

<button
    type="button"
    class={`nav-entry nav-entry--level-${level} ${isSelected ? "is-selected" : ""} ${disabled ? "is-disabled" : ""}`}
    {disabled}
    title={highlightLabel || title}
    onclick={handleClick}
    {...restProps}
>
    <span
        class={`nav-entry__chevron ${hasChildren ? "" : "nav-entry__chevron--empty"}`}
        onclick={handleToggle}
    >
        {#if hasChildren}
            <Fa icon={expanded ? faCaretDown : faCaretRight} />
        {/if}
    </span>
    {#if icon}
        <span class="nav-entry__icon">
            <Fa {icon} />
        </span>
    {/if}
    <span class="nav-entry__labels">
        <span class="nav-entry__label">{label}</span>
        {#if secondaryLabel}
            <span class="nav-entry__secondary">{secondaryLabel}</span>
        {/if}
    </span>
    {#if badgeText}
        <span class={`nav-entry__badge ${badgeClass(badgeVariant)}`}>
            {badgeText}
        </span>
    {/if}
</button>

<style>
    @import "./navigation.css";
</style>
