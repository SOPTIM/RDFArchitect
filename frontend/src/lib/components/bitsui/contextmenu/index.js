/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

import ContextMenuContent from "./ContextMenuContent.svelte";
import ContextMenuRoot from "./ContextMenuRoot.svelte";
import ContextMenuSeparator from "./ContextMenuSeparator.svelte";
import ContextMenuTriggerArea from "./ContextMenuTriggerArea.svelte";
import ContextMenuItemButton from "./item/ContextMenuItemButton.svelte";
import ContextMenuItemCheckBox from "./item/ContextMenuItemCheckBox.svelte";
import ContextMenuItemCounter from "./item/ContextMenuItemCounter.svelte";
import ContextMenuSubContent from "./sub/ContextMenuSubContent.svelte";
import ContextMenuSubMenu from "./sub/ContextMenuSubMenu.svelte";
import ContextMenuSubTrigger from "./sub/ContextMenuSubTrigger.svelte";

export const ContextMenu = {
    Root: ContextMenuRoot,
    TriggerArea: ContextMenuTriggerArea,
    Content: ContextMenuContent,
    Separator: ContextMenuSeparator,
    SubMenu: {
        Root: ContextMenuSubMenu,
        Content: ContextMenuSubContent,
        Trigger: ContextMenuSubTrigger,
    },
    Item: {
        Button: ContextMenuItemButton,
        CheckBox: ContextMenuItemCheckBox,
        Counter: ContextMenuItemCounter,
    },
};
