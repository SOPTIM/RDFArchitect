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
 * The pending question which schema a class is copied from. It is asked from
 * inside a context menu, which is torn down when the menu closes, so the dialog
 * is rendered once outside of it and driven through this state.
 */
export const extendSourceRequest = $state({
    open: false,
    workspaceName: null,
    candidates: [],
    targetLabel: "",
    onPick: null,
});

export function askForExtendSource({
    workspaceName,
    candidates,
    targetLabel,
    onPick,
}) {
    extendSourceRequest.workspaceName = workspaceName;
    extendSourceRequest.candidates = candidates;
    extendSourceRequest.targetLabel = targetLabel;
    extendSourceRequest.onPick = onPick;
    extendSourceRequest.open = true;
}
