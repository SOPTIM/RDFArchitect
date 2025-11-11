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

export const States = Object.freeze({
    saved: Symbol("saved"),
    modified: Symbol("modified"),
});

function equals(a, b) {
    if (a === b) return true;
    if (a == null || b == null) return a === b;
    if (typeof a !== typeof b) return false;
    if (typeof a === "object") {
        return a.equals(b);
    }
    return a === b;
}

export class Data {
    data;
    modifiedData = $state();
    dataState = $state();

    constructor(data, modifiedData, dataState) {
        if (
            data === undefined ||
            modifiedData === undefined ||
            dataState === undefined
        ) {
            throw new Error("Missing required arguments");
        }

        this.data = data;
        this.modifiedData = modifiedData;
        this.dataState = dataState;
    }

    updateDataState() {
        const areEqual = equals(this.data, this.modifiedData);

        if (areEqual) {
            this.dataState = States.saved;
        } else {
            this.dataState = States.modified;
        }
        return this.dataState;
    }
}
