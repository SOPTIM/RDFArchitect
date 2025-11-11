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

export const LIFECYCLE_NAMES = new Set([
    "onMount",
    "beforeUpdate",
    "afterUpdate",
    "onDestroy",
    "tick",
]);

export const PROPS_RUNE_NAMES = new Set(["$props"]);
export const DERIVED_CALL_NAMES = new Set(["$derived", "derived"]);
export const EFFECT_CALL_NAMES = new Set(["$effect"]);
export const STATE_CALL_NAMES = new Set(["$state", "writable", "readable"]);

export const CATEGORY_LABELS = {
    [-1]: 'directive prologues (e.g. "use strict")',
    0: "imports or re-exports",
    1: "type definitions and file directives",
    2: "component API (props, context)",
    3: "constants and configuration",
    4: "mutable state",
    5: "derived state",
    6: "reactive effects",
    7: "lifecycle hooks",
    8: "functions and handlers",
    9: "other statements",
};
