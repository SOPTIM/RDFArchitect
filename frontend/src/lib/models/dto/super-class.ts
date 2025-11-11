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

export class SuperClass {
    constructor({
        prefix = null,
        label = null,
    }: {
        prefix?: string | null;
        label?: string | null;
    } = {}) {
        this.prefix = prefix;
        this.label = label;
    }

    prefix: string | null;
    label: string | null;

    equals(other: SuperClass) {
        if (!(other instanceof SuperClass)) {
            return false;
        }
        return this.prefix === other.prefix && this.label === other.label;
    }
}
