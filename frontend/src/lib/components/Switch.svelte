<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script lang="ts">
    import { Switch, Label, useId, type WithoutChildrenOrChild } from "bits-ui";

    let {
        id = useId(),
        checked = $bindable(false),
        ref = $bindable(null),
        onLabel,
        offLabel,
        ...restProps
    }: WithoutChildrenOrChild<Switch.RootProps> & {
        onLabel: string;
        offLabel: string;
    } = $props();
</script>

<div class="flex items-center gap-2">
    <Switch.Root
        bind:checked
        bind:ref
        {id}
        {...restProps}
        class="
            bg-button-disabled-background data-[state=checked]:bg-button-default-background relative inline-flex h-7 w-13
            items-center rounded-full
            transition-colors
            duration-200
        "
    >
        <Switch.Thumb
            class="
                bg--white inline-block h-5 w-5 transform rounded-full shadow
                transition-transform duration-200
                data-[state=checked]:translate-x-[11px]
                data-[state=unchecked]:translate-x-[-11px]
            "
        />
    </Switch.Root>
    <Label.Root for={id} class="text-default-text text-sm font-medium">
        {checked ? onLabel : offLabel}
    </Label.Root>
</div>
