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
    import ItemButtonBase from "$lib/components/bitsui/base/menu/item/ItemButtonBase.svelte";

    import ContextMenuItem from "./ContextMenuItem.svelte";
    let {
        children,
        faIcon,
        altText,
        disabled = false,
        onSelect,
        variant = "default",
        ...other
    } = $props();
</script>

<ContextMenuItem {onSelect} {disabled} {...other}>
    <ItemButtonBase {faIcon} {disabled} {altText} {variant} center={children} />
</ContextMenuItem>
