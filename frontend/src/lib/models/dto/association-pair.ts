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

import { Association } from "./association";
import { Class } from "./class";
import { DataType, DataTypeTypes } from "./data-type";

export class AssociationPair {
    constructor({ from, to }) {
        this.from = new Association(from);
        this.to = new Association(to);
    }

    static createWithDerivedValuesFromClass(cls: Class) {
        return new AssociationPair({
            from: new Association({
                prefix: cls.prefix,
                domain: cls.label,
                associationUsed: true,
            }),
            to: new Association({
                prefix: cls.prefix,
                label: cls.label,
                range: new DataType({
                    type: DataTypeTypes.RANGE,
                    label: cls.label,
                    prefix: cls.prefix,
                }),
                multiplicity: "M:1",
            }),
        });
    }

    static readonly direction = {
        FROM: "from",
        TO: "to",
    } as const;

    from: Association;
    to: Association;

    getInverseAssociation(association: Association) {
        if (association === this.from) return this.to;
        else if (association === this.to) return this.from;
        else throw new Error("AssociationPair is not part of this pair");
    }

    updateUUIDs(fromUUID: string, toUUID: string) {
        this.from.uuid = fromUUID;
        this.to.uuid = toUUID;
    }

    updatePrefixes(newURIPrefix: string) {
        this.from.prefix = newURIPrefix;
        this.to.range.prefix = newURIPrefix;
    }

    updateDomain(newDomain: string) {
        this.from.domain = newDomain;
        this.to.range.label = newDomain;
        this.to.label = newDomain;
    }

    updateRange(
        association: Association,
        newRange: DataType,
        newPrefix: string,
    ) {
        const inverseAssociation = this.getInverseAssociation(association);
        association.range = newRange;
        association.label = newRange?.label;
        inverseAssociation.domain = newRange?.label;
        inverseAssociation.prefix = newPrefix;
    }

    equals(other: AssociationPair): boolean {
        return (
            other instanceof AssociationPair &&
            this.from.equals(other.from) &&
            this.to.equals(other.to)
        );
    }
}
