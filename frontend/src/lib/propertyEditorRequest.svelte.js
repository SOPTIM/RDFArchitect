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

export const PropertyKind = {
    ATTRIBUTE: "attribute",
    ASSOCIATION: "association",
    ENUM_ENTRY: "enumEntry",
};

export const FocusField = {
    LABEL: "label",
    MULTIPLICITY: "multiplicity",
};

export const propertyEditorRequest = createPropertyEditorRequest();

/**
 * Asks the class editor of a class to focus its super class, e.g. after an inheritance edge was
 * opened from the diagram. Every request carries a new token, so the same class can be asked
 * again.
 */
export const superClassFocusRequest = createSuperClassFocusRequest();

function createPropertyEditorRequest() {
    let request = $state(null);

    return {
        get current() {
            return request;
        },

        open(target) {
            request = { ...target };
        },

        close() {
            request = null;
        },
    };
}

function createSuperClassFocusRequest() {
    let request = $state(null);
    let token = 0;

    return {
        get current() {
            return request;
        },
        open(classUuid) {
            request = { classUuid, token: ++token };
        },
        close() {
            request = null;
        },
    };
}
