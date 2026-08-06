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

export type AsyncSlot<T> = {
    data: T | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

export type AsyncListSlot<T> = {
    data: T[] | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

export type Result<T = void> = { error: unknown; data?: T };

export function createEmptySlot<T>(): AsyncSlot<T> {
    return { data: null, fetchedAt: null, pending: null, error: null };
}

export function createEmptyListSlot<T>(): AsyncSlot<T> {
    return { data: null, fetchedAt: null, pending: null, error: null };
}
