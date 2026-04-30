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
    import { Label, Switch as BitsUiSwitch, useId } from "bits-ui";

    let {
        id = useId(),
        checked = $bindable(false),
        disabled = false,
        onCheckedChange,
        onLabel,
        offLabel,
        ...restProps
    } = $props();
</script>

<div class="flex items-center gap-2">
    <BitsUiSwitch.Root
        bind:checked
        {id}
        {disabled}
        {onCheckedChange}
        {...restProps}
        class="bg-button-disabled-background data-[state=checked]:bg-button-default-background relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50"
    >
        <BitsUiSwitch.Thumb
            class="bg-white inline-block h-4 w-4 transform rounded-full shadow transition-transform duration-200 data-[state=checked]:translate-x-[12px] data-[state=unchecked]:translate-x-[4px]"
        />
    </BitsUiSwitch.Root>
    {#if onLabel || offLabel}
        <Label.Root for={id} class="text-default-text text-sm font-medium">
            {checked ? onLabel : offLabel}
        </Label.Root>
    {/if}
</div>
