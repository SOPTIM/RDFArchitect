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
    import { faTrash } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let {
        isOpen = false,
        position = { x: 0, y: 0 },
        disabled = false,
        onDeleteClass = () => {},
        onClose = () => {},
    } = $props();

    function handleOverlayClick() {
        onClose();
    }

    function handleOverlayContextMenu(event) {
        event.preventDefault();
        onClose();
    }

    function handleItemContextMenu(event) {
        event.preventDefault();
    }

    function handleDeleteClass(event) {
        event.stopPropagation();
        if (disabled) {
            return;
        }
        onDeleteClass();
    }
</script>

{#if isOpen}
    <div
        class="fixed inset-0"
        style="z-index: 1198;"
        role="presentation"
        onclick={handleOverlayClick}
        oncontextmenu={handleOverlayContextMenu}
    ></div>
    <div
        class="menu-surface menu-surface--context fixed"
        style={`left: ${position.x}px; top: ${position.y}px; z-index: 1199;`}
        data-svelteflow-context-menu
        data-state="open"
    >
        <button
            type="button"
            class={`menu-item w-full border-0 bg-transparent text-left ${
                disabled ? "menu-item--disabled" : "menu-item--interactive"
            } svelte-flow-menu-item--danger`}
            onclick={handleDeleteClass}
            oncontextmenu={handleItemContextMenu}
            {disabled}
        >
            <span class="menu-icon">
                <Fa icon={faTrash} />
            </span>
            <span class="menu-label">Delete class</span>
            <span class="menu-shortcut"></span>
        </button>
    </div>
{/if}

<style>
    .svelte-flow-menu-item--danger {
        color: var(--color-button-red-background);
    }

    .svelte-flow-menu-item--danger.menu-item--disabled {
        color: var(--color-border-disabled);
    }

    .svelte-flow-menu-item--danger.menu-item--interactive:hover {
        background: var(--color-button-red-background);
        color: var(--color-button-hover-text);
    }
</style>
