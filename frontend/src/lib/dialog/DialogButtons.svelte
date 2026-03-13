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
    import { faXmark } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import FaIconButton from "$lib/components/FaIconButton.svelte";

    let {
        showDialog = $bindable(),
        readonly,
        title,
        titleIcon,
        titleIconStyle,
        secondaryLabel,
        secondaryVariant = "contrast",
        disableSecondary,
        onSecondary = () => {},
        secondaryIcon,
        primaryLabel = "Submit",
        primaryIcon,
        primaryVariant,
        disablePrimary,
        onPrimary = () => {},
        onCloseButton = () => {},
        children,
    } = $props();

    let secondaryButtonExists = $derived(secondaryLabel || secondaryIcon);
    let primaryButtonExists = $derived(primaryLabel || primaryIcon);
    let boundToDialog = $derived(showDialog !== undefined);

    function closeDialog() {
        if (boundToDialog) {
            showDialog = false;
        }
    }

    function handleConfirmShortcut(event) {
        if (!showDialog) {
            return;
        }

        if (
            event.defaultPrevented ||
            event.key !== "Enter" ||
            event.repeat ||
            event.isComposing
        ) {
            return;
        }

        if (event.metaKey || event.ctrlKey || event.altKey) {
            return;
        }

        const target = event.target;
        if (
            target instanceof HTMLTextAreaElement ||
            target instanceof HTMLButtonElement ||
            target instanceof HTMLAnchorElement ||
            (target instanceof HTMLElement &&
                (target.getAttribute?.("role") === "button" ||
                    target.isContentEditable))
        ) {
            return;
        }

        if (disablePrimary) {
            return;
        }

        event.preventDefault();
        onPrimary();
        closeDialog();
    }
</script>

<svelte:window onkeydown|capture={handleConfirmShortcut} />
<div class="h-full max-h-full w-full">
    <div class="mb-1 flex items-center justify-between">
        <div class="flex items-center space-x-2">
            <p class="text-default-text flex items-center gap-2 text-lg">
                {#if titleIcon}
                    <Fa class={titleIconStyle} icon={titleIcon} />
                {/if}
                {#if title}
                    {title}
                {/if}
            </p>
        </div>
        <div class="size-8">
            <FaIconButton
                variant="danger"
                callOnClick={() => {
                    closeDialog();
                    onCloseButton();
                }}
                icon={faXmark}
            />
        </div>
    </div>

    {@render children?.()}

    <div class="mx-2 my-1 mt-4 flex justify-end space-x-2">
        {#if readonly}
            <!-- In readonly mode, only show the cancel button -->
            <div>
                <ButtonControl
                    variant="contrast"
                    callOnClick={() => {
                        closeDialog();
                    }}
                >
                    Close
                </ButtonControl>
            </div>
        {:else}
            {#if secondaryButtonExists}
                <div>
                    <FaIconButton
                        callOnClick={() => {
                            if (!readonly) onSecondary();
                            closeDialog();
                        }}
                        variant={secondaryVariant}
                        disabled={disableSecondary}
                        text={secondaryLabel}
                        icon={secondaryIcon}
                    />
                </div>
            {/if}
            {#if primaryButtonExists}
                <div>
                    <FaIconButton
                        callOnClick={() => {
                            if (!readonly) onPrimary();
                            closeDialog();
                        }}
                        variant={primaryVariant}
                        disabled={disablePrimary}
                        text={primaryLabel}
                        icon={primaryIcon}
                    />
                </div>
            {/if}
        {/if}
    </div>
</div>
