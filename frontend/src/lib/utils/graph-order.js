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
 * Order the schemas of a workspace are listed in: by the short name they are
 * shown with, and by the graph they are kept in where two of them share that name.
 *
 * @param {{label: string, name: string}} a the label and graph name of a schema
 * @param {{label: string, name: string}} b the label and graph name of another schema
 */
export function compareGraphs(a, b) {
    const byLabel = (a?.label ?? "").localeCompare(b?.label ?? "", undefined, {
        sensitivity: "base",
    });
    if (byLabel !== 0) {
        return byLabel;
    }
    return (a?.name ?? "").localeCompare(b?.name ?? "");
}
