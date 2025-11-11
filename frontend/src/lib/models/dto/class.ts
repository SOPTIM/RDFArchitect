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

import { AssociationPair } from "./association-pair";
import { Attribute } from "./attribute";
import { EnumEntry } from "./enum-entry";
import { Package } from "./package";
import { SuperClass } from "./super-class";

export class Class {
    constructor({
        uuid = null,
        prefix = null,
        label = null,
        superClass = null,
        comment = null,
        package: pkg = null,
        stereotypes = [],
        attributes = [],
        associationPairs = [],
        enumEntries = [],
    }: {
        uuid?: string | null;
        prefix?: string | null;
        label?: string | null;
        superClass?;
        comment?: string | null;
        package?;
        stereotypes?: string[];
        attributes?: [];
        associationPairs?: [];
        enumEntries?: [];
    } = {}) {
        this.uuid = uuid;
        this.prefix = prefix;
        this.label = label;
        this.superClass =
            superClass == null ? null : new SuperClass(superClass);
        this.comment = comment;
        this.package = pkg == null ? null : new Package(pkg);
        this.stereotypes = stereotypes;
        this.attributes =
            attributes == null
                ? []
                : attributes.map(attr => new Attribute(attr));
        this.associationPairs =
            associationPairs == null
                ? []
                : associationPairs.map(pair => new AssociationPair(pair));
        this.enumEntries =
            enumEntries == null
                ? []
                : enumEntries.map(entry => new EnumEntry(entry));
    }

    uuid: string | null;

    prefix: string | null;

    label: string | null;

    superClass: SuperClass | null;

    comment: string | null;

    package: Package | null;

    stereotypes: string[];

    attributes: Attribute[];

    associationPairs: AssociationPair[];

    enumEntries: EnumEntry[];

    updateLabel(newLabel: string) {
        this.label = newLabel;
        for (const attribute of this.attributes) {
            attribute.domain = newLabel;
        }
        for (const associationPair of this.associationPairs) {
            associationPair.updateDomain(newLabel);
        }

        return true;
    }

    updateSuperClass(superClass: SuperClass) {
        this.superClass = superClass;
        return true;
    }

    updateComment(comment: string) {
        this.comment = comment;
        return true;
    }

    updatePackage(pkg: Package) {
        this.package = pkg;
        return true;
    }

    updateStereotypes(stereotypes: string[]) {
        this.stereotypes = stereotypes;
        return true;
    }

    replaceStereotype(oldStereotype: string, newStereotype: string) {
        const index = this.stereotypes.indexOf(oldStereotype);
        if (index < 0) {
            return false;
        }
        this.stereotypes[index] = newStereotype;
        return true;
    }

    appendStereotype(stereotype: string) {
        this.stereotypes.push(stereotype);
        return true;
    }

    updateAttributes(attributes: Attribute[]) {
        this.attributes = attributes;
        return true;
    }

    appendAttribute(attribute: Attribute) {
        this.attributes.push(attribute);
        return true;
    }

    updateAssociationPairs(associationPairs: AssociationPair[]) {
        this.associationPairs = associationPairs;
        return true;
    }

    appendAssociationPair(associationPair: AssociationPair) {
        this.associationPairs.push(associationPair);
        return true;
    }

    updateEnumEntries(enumEntries: EnumEntry[]) {
        this.enumEntries = enumEntries;
        return true;
    }

    appendEnumEntry(enumEntry: EnumEntry) {
        this.enumEntries ??= [];
        this.enumEntries.push(enumEntry);
        return true;
    }
}
