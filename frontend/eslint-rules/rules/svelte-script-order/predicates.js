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

import {
    DERIVED_CALL_NAMES,
    EFFECT_CALL_NAMES,
    LIFECYCLE_NAMES,
    PROPS_RUNE_NAMES,
    STATE_CALL_NAMES,
} from "./constants.js";

function unwrapDeclaration(node) {
    if (node.type === "ExportNamedDeclaration" && node.declaration)
        return node.declaration;
    return node;
}

export function isImport(node) {
    return node.type === "ImportDeclaration";
}

export function isReExport(node) {
    if (node.type === "ExportAllDeclaration") return true;
    if (node.type === "ExportNamedDeclaration" && node.source) return true;
    return false;
}

export function isTypeDecl(node) {
    const decl = unwrapDeclaration(node);
    if (!decl) return false;
    return (
        decl.type === "TSInterfaceDeclaration" ||
        decl.type === "TSTypeAliasDeclaration" ||
        decl.type === "TSEnumDeclaration"
    );
}

export function isDirectivePrologue(node) {
    if (node.type !== "ExpressionStatement") return false;
    const expr = node.expression;
    return expr && expr.type === "Literal" && typeof expr.value === "string";
}

function isCallToIdentifier(callExpression, names) {
    if (!callExpression || callExpression.type !== "CallExpression")
        return false;
    const callee = callExpression.callee;
    if (!callee) return false;
    if (callee.type === "Identifier") return names.has(callee.name);
    if (
        callee.type === "MemberExpression" &&
        !callee.computed &&
        callee.object?.type === "Identifier"
    ) {
        return names.has(callee.object.name);
    }
    return false;
}

export function isExportedProp(node) {
    if (node.type !== "ExportNamedDeclaration") return false;
    const decl = node.declaration;
    if (!decl || decl.type !== "VariableDeclaration") return false;
    return decl.kind === "let";
}

export function isPropsRune(node) {
    const decl = unwrapDeclaration(node);
    if (!decl || decl.type !== "VariableDeclaration") return false;
    return decl.declarations?.some(d =>
        isCallToIdentifier(d.init, PROPS_RUNE_NAMES),
    );
}

export function isConstVar(node) {
    const decl = unwrapDeclaration(node);
    if (!decl || decl.type !== "VariableDeclaration" || decl.kind !== "const")
        return false;
    return decl.declarations?.every(d => {
        const init = d.init;
        if (!init) return true;
        if (
            init.type === "ArrowFunctionExpression" ||
            init.type === "FunctionExpression"
        )
            return false;
        if (isCallToIdentifier(init, PROPS_RUNE_NAMES)) return false;
        if (isCallToIdentifier(init, STATE_CALL_NAMES)) return false;
        if (isCallToIdentifier(init, DERIVED_CALL_NAMES)) return false;
        if (isCallToIdentifier(init, EFFECT_CALL_NAMES)) return false;
        return true;
    });
}

export function isStateVar(node) {
    const decl = unwrapDeclaration(node);
    if (!decl || decl.type !== "VariableDeclaration") return false;
    if (isPropsRune(node)) return false;
    if (isDerivedDeclaration(node)) return false;
    if (decl.kind === "let" || decl.kind === "var") {
        return true;
    }
    if (decl.kind === "const") {
        return decl.declarations?.some(d =>
            isCallToIdentifier(d.init, STATE_CALL_NAMES),
        );
    }
    return false;
}

export function isSvelteReactive(node) {
    return node.type === "SvelteReactiveStatement";
}

export function isReactiveAssignment(node) {
    if (!isSvelteReactive(node)) return false;
    const body = node.body;
    if (!body) return false;
    if (
        body.type === "ExpressionStatement" &&
        body.expression?.type === "AssignmentExpression"
    )
        return true;
    return false;
}

export function isDerivedDeclaration(node) {
    const decl = unwrapDeclaration(node);
    if (!decl || decl.type !== "VariableDeclaration") return false;
    return decl.declarations?.some(d =>
        isCallToIdentifier(d.init, DERIVED_CALL_NAMES),
    );
}

export function isReactiveEffect(node) {
    if (isSvelteReactive(node) && !isReactiveAssignment(node)) return true;

    if (
        node.type === "ExpressionStatement" &&
        isCallToIdentifier(node.expression, EFFECT_CALL_NAMES)
    ) {
        return true;
    }

    const decl = unwrapDeclaration(node);
    if (!decl || decl.type !== "VariableDeclaration") return false;
    return decl.declarations?.some(d =>
        isCallToIdentifier(d.init, EFFECT_CALL_NAMES),
    );
}

export function isLifecycleCall(node) {
    const expr = node.type === "ExpressionStatement" ? node.expression : null;
    if (expr && expr.type === "CallExpression") {
        const callee = expr.callee;
        if (
            callee &&
            callee.type === "Identifier" &&
            LIFECYCLE_NAMES.has(callee.name)
        )
            return true;
    }

    const decl = unwrapDeclaration(node);
    if (!decl || decl.type !== "VariableDeclaration") return false;

    return decl.declarations?.some(d => {
        const init = d.init;
        if (!init || init.type !== "CallExpression") return false;
        const callee = init.callee;
        return (
            callee &&
            callee.type === "Identifier" &&
            LIFECYCLE_NAMES.has(callee.name)
        );
    });
}

export function isFunctionDecl(node) {
    if (node.type === "FunctionDeclaration") return true;
    if (
        node.type === "ExportNamedDeclaration" &&
        node.declaration?.type === "FunctionDeclaration"
    )
        return true;

    const decl = unwrapDeclaration(node);
    if (!decl || decl.type !== "VariableDeclaration" || decl.kind !== "const")
        return false;

    return decl.declarations?.some(d => {
        const init = d.init;
        return (
            init &&
            (init.type === "ArrowFunctionExpression" ||
                init.type === "FunctionExpression")
        );
    });
}
