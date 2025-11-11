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

import { DataType } from "./data-type";
import { isValidMultiplicity } from "./utils";

export class Association {
    constructor({
        uuid = null,
        prefix = null,
        label = null,
        comment = null,
        multiplicity = null,
        domain = null,
        range = null,
        associationUsed = false,
    }: {
        uuid?: string | null;
        prefix?: string | null;
        label?: string | null;
        comment?: string | null;
        multiplicity?: string | null;
        domain?: string | null;
        range?: DataType | null;
        associationUsed?: boolean;
    } = {}) {
        this.uuid = uuid;
        this.prefix = prefix;
        this.label = label;
        this.comment = comment;
        this.multiplicity = isValidMultiplicity(multiplicity)
            ? multiplicity
            : null;
        this.domain = domain;
        this.range = range == null ? null : new DataType(range);
        this.associationUsed = associationUsed ?? false;
    }

    uuid: string | null;

    prefix: string | null;

    label: string | null;

    comment: string | null;

    multiplicity: string | null;

    domain: string | null;

    range: DataType | null;

    associationUsed: boolean;

    updateUUID(uuid: string) {
        this.uuid = uuid;
    }

    updateLabel(newLabel: string): void {
        this.label = newLabel;
    }

    updateComment(comment: string) {
        this.comment = comment;
        return true;
    }

    updateMultiplicity(multiplicity: string) {
        this.multiplicity = multiplicity;
        return true;
    }

    updateAssociationUsed(associationUsed: boolean) {
        this.associationUsed = associationUsed;
        return true;
    }

    equals(other: Association) {
        if (!(other instanceof Association)) {
            return false;
        }
        const rangeEquals = (() => {
            if (this.range === null && other.range === null) {
                return true;
            }
            if (this.range === null || other.range === null) {
                return false;
            }
            return this.range.equals(other.range);
        })();
        return (
            this.uuid === other.uuid &&
            this.prefix === other.prefix &&
            this.label === other.label &&
            this.comment === other.comment &&
            this.multiplicity === other.multiplicity &&
            this.domain === other.domain &&
            rangeEquals &&
            this.associationUsed === other.associationUsed
        );
    }
}
