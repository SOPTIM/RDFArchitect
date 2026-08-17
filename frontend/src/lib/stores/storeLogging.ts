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

// Try to coerce whatever the SDK gives us into a readable string for logging.
export async function describeError(error: unknown): Promise<string> {
    if (error == null) return "unknown error";
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    if (
        typeof (error as { text?: () => Promise<string> }).text === "function"
    ) {
        try {
            return await (error as { text: () => Promise<string> }).text();
        } catch {
            /* ignore */
        }
    }
    try {
        return JSON.stringify(error);
    } catch {
        return String(error);
    }
}
