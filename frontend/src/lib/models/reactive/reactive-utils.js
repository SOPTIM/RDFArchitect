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

import { faRotateLeft, faTrash } from "@fortawesome/free-solid-svg-icons";

/**
 * Get control button objects for InputControls
 * @param obj a Reactive Object
 * @param readonly if the value the buttons control is readonly
 * @param callOnChange A function called when the value is updated
 * @returns a list containing objects defining control buttons for reactive Objects
 */
export function getControlButtonsForReactiveObject(obj, readonly, callOnChange = () => {}) {
    if (readonly) {
        return [];
    }
    return [
        {
            callOnClick: () => {
                callOnChange(null);
                obj.value = null;
            },
            title: "Clear Value",
            icon: faTrash,
            disabled:
                obj.value === null ||
                obj.value === undefined ||
                obj.value === "",
        },
        {
            callOnClick: () => {
                callOnChange(obj.value);
                obj.reset();
            },
            title: "Revert Changes",
            icon: faRotateLeft,
            disabled: !obj.isModified,
        },
    ];
}
