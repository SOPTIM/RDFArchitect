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

/**
 * What a workspace may be called, and what to call one made from a file.
 *
 * A workspace name becomes part of a URL, so the characters that would end it early or start a
 * fragment are the ones it cannot hold.
 */

const INVALID_CHARACTERS = /[/\\?#]/;

/** Whether the name holds a character a workspace cannot be called by. */
export function hasInvalidCharacters(name) {
    return INVALID_CHARACTERS.test(name ?? "");
}

/**
 * The workspace name a file suggests: its name without the path and without the extension, with
 * anything a workspace cannot be called by replaced.
 */
export function workspaceNameFrom(fileName) {
    const lastSegment = (fileName ?? "").split(/[\\/]/).pop() ?? "";
    const lastDot = lastSegment.lastIndexOf(".");
    const base = lastDot > 0 ? lastSegment.slice(0, lastDot) : lastSegment;
    return base.replace(/[/\\?#]/g, "_").trim();
}
