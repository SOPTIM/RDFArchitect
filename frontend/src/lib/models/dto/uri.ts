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

import { validateIri } from "validate-iri";

export class URI {
    prefix: string | null = null;
    suffix: string = "";

    constructor(input: string | { prefix: string | null; suffix: string }) {
        if (typeof input === "string") {
            const { prefix, suffix } = URI.parse(input);
            this.prefix = prefix;
            this.suffix = suffix;
        } else {
            this.prefix = input.prefix ?? null;
            this.suffix = input.suffix ?? "";
        }
    }

    private static parse(input: string): {
        prefix: string | null;
        suffix: string;
    } {
        if (!input) {
            throw new Error("URI must not be null or empty");
        }

        const error = validateIri(input);
        if (error) {
            throw new Error(`Invalid IRI: ${input}`);
        }

        const hash = input.indexOf("#");
        const slash = input.lastIndexOf("/");

        let prefix: string | null;
        let suffix: string;
        if (hash >= 0) {
            prefix = input.substring(0, hash + 1);
            suffix = input.substring(hash + 1);
        } else if (slash >= 0) {
            prefix = input.substring(0, slash + 1);
            suffix = input.substring(slash + 1);
        } else {
            prefix = null;
            suffix = input;
        }

        if (!prefix) {
            throw new Error(`IRI must have a namespace: ${input}`);
        }

        return { prefix, suffix };
    }

    toString(): string {
        return (this.prefix ?? "") + this.suffix;
    }

    equals(other: URI): boolean {
        if (!(other instanceof URI)) {
            return false;
        }
        return this.toString() === other.toString();
    }
}
