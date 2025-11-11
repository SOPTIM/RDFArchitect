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

export class Attribute {
    constructor({
        uuid = null,
        prefix = null,
        label = null,
        domain = null,
        multiplicity = null,
        dataType = null,
        comment = null,
        fixedValue = null,
        defaultValue = null,
    }: {
        uuid?: string | null;
        prefix?: string | null;
        label?: string | null;
        domain?: string | null;
        multiplicity?: string | null;
        dataType?: DataType | null;
        comment?: string | null;
        fixedValue?: string | null;
        defaultValue?: string | null;
    } = {}) {
        this.uuid = uuid;
        this.prefix = prefix;
        this.label = label;
        this.domain = domain;
        this.multiplicity = isValidMultiplicity(multiplicity)
            ? multiplicity
            : null;
        this.dataType = dataType == null ? null : new DataType(dataType);
        this.comment = comment;
        this.fixedValue = fixedValue;
        this.defaultValue = defaultValue;
    }

    uuid: string | null;

    prefix: string | null;

    label: string | null;

    domain: string | null;

    multiplicity: string | null;

    dataType: DataType | null;

    comment: string | null;

    fixedValue: string | null;

    defaultValue: string | null;

    updateUUID(uuid: string) {
        this.uuid = uuid;
    }

    updatePrefix(prefix: string): void {
        this.prefix = prefix;
    }

    updateLabel(newLabel: string): void {
        this.label = newLabel;
    }

    updateMultiplicity(multiplicity: string) {
        if (!isValidMultiplicity(multiplicity)) {
            return false;
        }
        this.multiplicity = multiplicity;
        return true;
    }

    updateDataType(newDataType: DataType) {
        this.dataType = newDataType;
        return true;
    }

    updateComment(comment: string) {
        this.comment = comment;
        return true;
    }

    updateFixedValue(fixedValue: string) {
        this.fixedValue = fixedValue;
        return true;
    }

    updateDefaultValue(defaultValue: string) {
        this.defaultValue = defaultValue;
        return true;
    }

    equals(other: Attribute) {
        if (!(other instanceof Attribute)) {
            return false;
        }
        const dataTypeEquals = (() => {
            if (this.dataType === null && other.dataType === null) {
                return true;
            }
            if (this.dataType === null || other.dataType === null) {
                return false;
            }
            return this.dataType.equals(other.dataType);
        })();
        return (
            this.uuid === other.uuid &&
            this.prefix === other.prefix &&
            this.label === other.label &&
            this.domain === other.domain &&
            this.multiplicity === other.multiplicity &&
            this.comment === other.comment &&
            this.fixedValue === other.fixedValue &&
            this.defaultValue === other.defaultValue &&
            dataTypeEquals
        );
    }
}
