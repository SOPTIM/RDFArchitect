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
    const {
        callOnClick = () => {},
        disabled = false,
        variant = "default",
        title,
        children,
        height = 8,
        ...restProps
    } = $props();

    const variants = {
        default: "default-ButtonControl-variant",
        contrast: "contrast-ButtonControl-variant",
        inline: "inline-ButtonControl-variant",
        inlineContrast: "inline-contrast-ButtonControl-variant",
        danger: "danger-ButtonControl-variant",
    };

    let variantClass = $derived(variants[variant] ?? variants.default);

    let xPadding = $derived(Math.floor((height - 1) / 2));
</script>

<button
    class={`group ${variantClass}
        flex w-full
        cursor-pointer
        items-center
        justify-center
        overflow-hidden
        rounded
        px-${xPadding} font-normal
        whitespace-nowrap
        shadow-xs transition-colors disabled:cursor-not-allowed h-${height}
    `}
    onclick={callOnClick}
    {disabled}
    {title}
    {...restProps}
>
    <span
        class="flex scale-90 items-center
               justify-center transition-transform
               group-hover:scale-100
               group-disabled:group-hover:scale-90"
    >
        {@render children?.()}
    </span>
</button>

<style>
    /* DEFAULT variant */
    button.default-ButtonControl-variant {
        background: var(--color-white);
        color: var(--color-blue);
        border: 1px solid var(--color-button-border);
    }

    button.default-ButtonControl-variant:hover:not(:disabled) {
        background: var(--color-lightblue);
        color: var(--color-blue);
        border-color: var(--color-blue);
    }

    button.default-ButtonControl-variant:disabled {
        background: var(--color-white);
        color: var(--color-button-hover-background);
        border-color: var(--color-lightblue);
        cursor: not-allowed;
        box-shadow: none;
    }

    /* CONTRAST variant */
    button.contrast-ButtonControl-variant {
        background: var(--color-white);
        color: var(--color-default-text);
        border: 1px solid var(--color-border);
    }
    button.contrast-ButtonControl-variant:hover:not(:disabled) {
        background: var(--color-lightblue);
        color: var(--color-default-text);
        border-color: var(--color-default-text);
    }
    button.contrast-ButtonControl-variant:disabled {
        background: var(--color-lightgray);
        color: var(--color-default-text);
        border-color: var(--color-border);
    }

    /* INLINE variant */
    button.inline-ButtonControl-variant {
        background: transparent;
        color: var(--color-blue);
        border: none;
    }

    button.inline-ButtonControl-variant:hover:not(:disabled) {
        background: var(--color-lightblue);
        color: var(--color-blue);
    }

    button.inline-ButtonControl-variant:disabled {
        color: var(--color-button-hover-background);
        cursor: not-allowed;
        box-shadow: none;
    }

    /* INLINE CONTRAST variant */
    button.inline-contrast-ButtonControl-variant {
        background: transparent;
        color: var(--color-blue);
        border: none;
    }

    button.inline-contrast-ButtonControl-variant:hover:not(:disabled) {
        background: var(--color-white);
        color: var(--color-blue);
    }

    button.inline-contrast-ButtonControl-variant:disabled {
        color: var(--color-button-hover-background);
        cursor: not-allowed;
        box-shadow: none;
    }

    /* DANGER variant */
    button.danger-ButtonControl-variant {
        background: var(--color-red-background);
        color: var(--color-red-text);
        border: 1px solid var(--color-red-border);
    }

    button.danger-ButtonControl-variant:hover:not(:disabled) {
        background: var(--color-red-hover-background);
        color: var(--color-red-text);
        border-color: var(--color-red-text);
    }

    button.danger-ButtonControl-variant:disabled {
        background: var(--color-lightgray);
        color: var(--color-text-subtle);
        border-color: var(--color-border);
        cursor: not-allowed;
        box-shadow: none;
    }
</style>
