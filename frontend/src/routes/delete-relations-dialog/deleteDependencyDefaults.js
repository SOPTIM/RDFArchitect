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

/**
 * Default action rules per reason (and optionally type).
 * Evaluated top-to-bottom, first match wins.
 * Add, remove, or reorder entries to adjust defaults.
 */
const defaultActionRules = [
    { reason: "CONTAINED_IN_PACKAGE", action: "DELETE" },
    {
        reason: "REFENCES_DELETED_CLASS_VIA_ASSOCIATION",
        type: "ASSOCIATION",
        action: "KEEP",
    },
    { reason: "USES_DELETED_CLASS_AS_DATATYPE", action: "KEEP" },
    { reason: "CHILD_OF", action: "KEEP" },
];

/**
 * Resolves the default action for a node based on the rules.
 * Falls back to the first available action if no rule matches.
 */
export function getDefaultAction(node) {
    for (const rule of defaultActionRules) {
        if (node.reason !== rule.reason) continue;
        if (rule.type && node.type !== rule.type) continue;
        if (node.actions.includes(rule.action)) {
            return rule.action;
        }
    }
    return node.actions[0];
}
