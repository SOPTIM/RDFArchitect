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

import { CATEGORY_LABELS } from "./constants.js";
import {
    isConstVar,
    isDerivedDeclaration,
    isDirectivePrologue,
    isExportedProp,
    isFunctionDecl,
    isImport,
    isLifecycleCall,
    isPropsRune,
    isReactiveAssignment,
    isReactiveEffect,
    isReExport,
    isStateVar,
    isTypeDecl,
} from "./predicates.js";

export function categoryIndex(node) {
    if (isDirectivePrologue(node)) return -1;
    if (isImport(node) || isReExport(node)) return 0;
    if (isTypeDecl(node)) return 1;
    if (isExportedProp(node) || isPropsRune(node)) return 2;
    if (isConstVar(node)) return 3;
    if (isStateVar(node)) return 4;
    if (isReactiveAssignment(node) || isDerivedDeclaration(node)) return 5;
    if (isReactiveEffect(node)) return 6;
    if (isLifecycleCall(node)) return 7;
    if (isFunctionDecl(node)) return 8;
    return 9;
}

export function categoryLabel(idx) {
    return CATEGORY_LABELS[idx] ?? `category ${idx}`;
}
