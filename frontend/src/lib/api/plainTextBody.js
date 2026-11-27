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
 * Returns the request options that send `text` unchanged as a `text/plain` body.
 *
 * For an endpoint whose OpenAPI body is a JSON string, the generated client would send the text
 * JSON-encoded, in quotes, while the backend reads the body verbatim.
 *
 * @param {string} text the request body
 * @returns {{ body: string, bodySerializer: null, headers: { "Content-Type": string } }} options
 *     to spread into a call of the generated client
 */
export function plainTextBody(text) {
    return {
        body: text,
        bodySerializer: null,
        headers: { "Content-Type": "text/plain" },
    };
}
