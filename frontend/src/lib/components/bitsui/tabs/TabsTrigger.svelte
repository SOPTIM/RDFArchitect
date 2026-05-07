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
    import { Tabs as BitsUiTabs } from "bits-ui";
    import { Fa } from "svelte-fa";

    let {
        value,
        icon,
        text,
        disabled = false,
        variant = "default",
        title,
        class: className = "",
        ...restProps
    } = $props();
</script>

<BitsUiTabs.Trigger
    {value}
    {disabled}
    {title}
    data-variant={variant}
    class={`text-text-subtle data-[variant=danger]:text-text-subtle data-[state=inactive]:hover:bg-lightblue data-[state=inactive]:hover:text-blue data-[state=inactive]:data-[variant=danger]:hover:bg-red-background data-[state=inactive]:data-[variant=danger]:hover:text-red-text
        data-[state=active]:text-blue data-[state=active]:border-button-border data-[state=active]:data-[variant=danger]:bg-red-background data-[state=active]:data-[variant=danger]:text-red-text data-[state=active]:data-[variant=danger]:border-red-border
        inline-flex h-8 w-full cursor-pointer
        items-center
        justify-center
        rounded
        border
        border-transparent
        bg-transparent
        px-2
        text-sm
        leading-none
        whitespace-nowrap
        transition-colors
        data-disabled:cursor-not-allowed
        data-disabled:opacity-50
        data-[state=active]:bg-white
        data-[state=active]:shadow-sm
        ${className}`}
    {...restProps}
>
    <span class="inline-flex items-center justify-center gap-2">
        {#if text}
            <span>{text}</span>
        {/if}
        {#if icon}
            <Fa {icon} />
        {/if}
    </span>
</BitsUiTabs.Trigger>
