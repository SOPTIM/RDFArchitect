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
    import { faChevronDown } from "@fortawesome/free-solid-svg-icons";
    import { DropdownMenu as BitsUiDropdownMenu } from "bits-ui";
    import { Fa } from "svelte-fa";

    let {
        label,
        options = [],
        getOptionValue = o => o,
        getOptionLabel = o => o,
        value = null,
        onChange,
    } = $props();

    let displayLabel = $derived(
        value == null
            ? "Select..."
            : getOptionLabel(
                  options.find(o => getOptionValue(o) === value) ?? value,
              ),
    );
</script>

<div class="flex items-center gap-2">
    <span class="text-default-text">{label}</span>

    <BitsUiDropdownMenu.Root>
        <BitsUiDropdownMenu.Trigger
            class="border-button-border bg-window-background text-default-text hover:border-blue flex h-9 min-w-32 items-center justify-between rounded border px-2 text-sm shadow-xs transition-colors outline-none"
        >
            <span class="truncate">{displayLabel}</span>
            <Fa icon={faChevronDown} class="ml-2 shrink-0 opacity-50" />
        </BitsUiDropdownMenu.Trigger>

        <BitsUiDropdownMenu.Portal>
            <BitsUiDropdownMenu.Content
                class="menu-surface menu-surface--dropdown"
                align="start"
                collisionPadding={8}
                preventScroll={false}
            >
                <BitsUiDropdownMenu.RadioGroup
                    {value}
                    onValueChange={v => onChange?.(v)}
                >
                    {#each options as option (getOptionValue(option))}
                        <BitsUiDropdownMenu.RadioItem
                            class="group w-full"
                            value={getOptionValue(option)}
                            onpointerenter={e => e.currentTarget.focus()}
                        >
                            {getOptionLabel(option)}
                        </BitsUiDropdownMenu.RadioItem>
                    {/each}
                </BitsUiDropdownMenu.RadioGroup>
            </BitsUiDropdownMenu.Content>
        </BitsUiDropdownMenu.Portal>
    </BitsUiDropdownMenu.Root>
</div>
