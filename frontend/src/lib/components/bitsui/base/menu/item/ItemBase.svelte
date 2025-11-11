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
    let { children, disabled, variant = "default" } = $props();

    const baseCss = "menu-item";
    const disabledCss = "menu-item--disabled";
    const enabledCss = "menu-item--interactive";
</script>

<div
    class="{baseCss} {disabled ? disabledCss : enabledCss}"
    data-disabled={disabled}
    data-variant={variant}
>
    {@render children?.()}
</div>

<style>
    .menu-item[data-variant="danger"] {
        color: var(--color-button-red-background);
    }

    .menu-item[data-variant="danger"].menu-item--interactive:hover,
    :global(.group[data-highlighted]) .menu-item[data-variant="danger"],
    :global(.group[data-state="open"]) .menu-item[data-variant="danger"] {
        background: var(--color-button-red-background);
        color: var(--color-button-hover-text);
    }
</style>
