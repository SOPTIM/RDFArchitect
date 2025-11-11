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

import DropdownMenuContent from "./DropdownMenuContent.svelte";
import DropdownMenuRoot from "./DropdownMenuRoot.svelte";
import DropdownMenuSeparator from "./DropdownMenuSeparator.svelte";
import DropdownMenuTrigger from "./DropdownMenuTrigger.svelte";
import DropdownMenuItemButton from "./item/DropdownMenuItemButton.svelte";
import DropdownMenuItemCheckBox from "./item/DropdownMenuItemCheckBox.svelte";
import DropdownMenuSubContent from "./sub/DropdownMenuSubContent.svelte";
import DropdownMenuSubMenu from "./sub/DropdownMenuSubMenu.svelte";
import DropdownMenuSubTrigger from "./sub/DropdownMenuSubTrigger.svelte";

export const DropdownMenu = {
    Root: DropdownMenuRoot,
    Content: DropdownMenuContent,
    Trigger: DropdownMenuTrigger,
    Separator: DropdownMenuSeparator,
    SubMenu: {
        Root: DropdownMenuSubMenu,
        Trigger: DropdownMenuSubTrigger,
        Content: DropdownMenuSubContent,
    },
    Item: {
        Button: DropdownMenuItemButton,
        CheckBox: DropdownMenuItemCheckBox,
    },
};
