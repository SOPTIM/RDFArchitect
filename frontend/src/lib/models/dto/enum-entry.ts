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

export class EnumEntry {
    constructor({
        uuid = null,
        prefix = null,
        label = null,
        comment = null,
        hasStereotype = false,
        type = null,
    }: {
        uuid?: string | null;
        prefix?: string | null;
        label?: string | null;
        comment?: string | null;
        hasStereotype?: boolean;
        type?: string | null;
    } = {}) {
        this.uuid = uuid;
        this.prefix = prefix;
        this.label = label;
        this.comment = comment;
        this.stereotype = hasStereotype ? "enum" : null;
        this.type = type;
    }

    uuid: string | null;

    prefix: string | null;

    label: string | null;

    comment: string | null;

    stereotype: "enum" | null;

    type: string;

    updateUUID(uuid: string) {
        this.uuid = uuid;
    }

    updatePrefix(newPrefix: string) {
        this.prefix = newPrefix;
    }

    updateLabel(newLabel: string) {
        this.label = newLabel;
        return true;
    }

    updateComment(newComment: string) {
        this.comment = newComment;
        return true;
    }

    addStereotype() {
        this.stereotype = "enum";
        return true;
    }

    equals(other: EnumEntry) {
        if (!(other instanceof EnumEntry)) {
            return false;
        }
        return (
            this.uuid === other.uuid &&
            this.prefix === other.prefix &&
            this.label === other.label &&
            this.comment === other.comment &&
            this.stereotype === other.stereotype &&
            this.type === other.type
        );
    }
}
