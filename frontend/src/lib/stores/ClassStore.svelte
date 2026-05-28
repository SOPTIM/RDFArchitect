<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script module lang="ts">
    import {
        getClassList,
        getClassInformation,
        type ClassUmlAdaptedDto,
        type ClassListItemDto, replaceClass, addClass
    } from "../api/generated";

    type GraphKey = `${string}::${string}::${"internal" | "all"}`;
    type ClassKey = `${string}::${string}::${string}`;

    type DataState<T> = {
        data: T | null;
        fetchedAt: number | null;
        error: unknown;
    };
    const pendingList: Record<string, Promise<void>> = {};
    const pendingDetail: Record<string, Promise<void>> = {};

    export const classStore = {
        // ─── List ────────────────────────────────────────────────────────────

        getClasses(datasetName: string, graphURI: string, includeExternal = false) {
            const key = makeGraphKey(datasetName, graphURI, includeExternal);
            const state = getOrInitList(key);
            if (state.data === null && !pendingList[key]) {
                void this.loadList(datasetName, graphURI, includeExternal);
            }
            return {
                get data() {
                    return getOrInitList(key).data;
                },
                get error() {
                    return getOrInitList(key).error;
                },
                get loading() {
                    return key in pendingList;
                }
            };
        },

        async loadList(datasetName: string, graphURI: string, includeExternal = false, force = false) {
            if (!datasetName || !graphURI) return;

            const key = makeGraphKey(datasetName, graphURI, includeExternal);
            const state = getOrInitList(key);

            if (!force && state.data !== null) return;
            if (pendingList[key]) return pendingList[key];

            pendingList[key] = (async () => {
                try {
                    const { data, error } = await getClassList({
                        path: { datasetName, graphURI },
                        query: { includeExternalClasses: includeExternal }
                    });

                    if (error) {
                        console.error(`[ClassStore] Failed to load class list for ${key}`, error);
                        listCache[key] = { ...listCache[key], error };
                    } else {
                        listCache[key] = { data: data ?? [], fetchedAt: Date.now(), error: null };
                    }
                } catch (err) {
                    console.error(`[ClassStore] Failed to load class list for ${key}`, err);
                    listCache[key] = { ...listCache[key], error: err };
                } finally {
                    delete pendingList[key];
                }
            })();

            return pendingList[key];
        },

        getClassesForPackage(datasetName: string, graphURI: string, packageUUID: string | null, includeExternal = false): ClassListItemDto[] {
            const classes = this.getClasses(datasetName, graphURI, includeExternal).data ?? [];
            return classes.filter(c => (c.package?.uuid ?? null) === packageUUID);
        },

        // ─── Detail ──────────────────────────────────────────────────────────

        getClassDetail(datasetName: string, graphURI: string, classUUID: string) {
            const key = makeClassKey(datasetName, graphURI, classUUID);
            const state = getOrInitDetail(key);
            if (state.data === null && !pendingDetail[key]) {
                void this.loadDetail(datasetName, graphURI, classUUID);
            }
            return {
                get data() {
                    return getOrInitDetail(key).data;
                },
                get error() {
                    return getOrInitDetail(key).error;
                },
                get loading() {
                    return key in pendingDetail;
                }
            };
        },

        async loadDetail(datasetName: string, graphURI: string, classUUID: string, force = false) {
            if (!datasetName || !graphURI || !classUUID) return;

            const key = makeClassKey(datasetName, graphURI, classUUID);
            const state = getOrInitDetail(key);

            if (!force && state.data !== null) return;
            if (pendingDetail[key]) return pendingDetail[key];

            pendingDetail[key] = (async () => {
                try {
                    const { data, error } = await getClassInformation({
                        path: { datasetName, graphURI, classUUID }
                    });

                    if (error) {
                        console.error(`[ClassStore] Failed to load class detail for ${key}`, error);
                        detailCache[key] = { ...detailCache[key], error };
                    } else {
                        detailCache[key] = { data: data ?? null, fetchedAt: Date.now(), error: null };
                    }
                } catch (err) {
                    console.error(`[ClassStore] Failed to load class detail for ${key}`, err);
                    detailCache[key] = { ...detailCache[key], error: err };
                } finally {
                    delete pendingDetail[key];
                }
            })();

            return pendingDetail[key];
        },

        async fetchClassDetail(datasetName: string, graphURI: string, classUUID: string): Promise<ClassUmlAdaptedDto | null> {
            await this.loadDetail(datasetName, graphURI, classUUID);
            return this.getClassDetail(datasetName, graphURI, classUUID).data;
        },

        // ─── Update ────────────────────────────────────────────────────

        async replaceClass(
            datasetName: string,
            graphURI: string,
            classUUID: string,
            updatedClass: ClassUmlAdaptedDto,
        ): Promise<{ error: unknown }> {
            const { error } = await replaceClass({
                path: { datasetName, graphURI, classUUID },
                body: updatedClass,
            });

            if (error) {
                console.error(`[ClassStore] Failed to replace class ${classUUID}`, error);
                return { error };
            }

            // Update detail cache directly — no re-fetch needed
            const detailKey = makeClassKey(datasetName, graphURI, classUUID);
            detailCache[detailKey] = {
                data: updatedClass,
                fetchedAt: Date.now(),
                error: null,
            };

            // Invalidate list — label/package may have changed
            this.invalidateGraph(datasetName, graphURI);

            return { error: null };
        },

        async addClass(
            datasetName: string,
            graphURI: string,
            classData: never,
        ): Promise<{ error: unknown; data: string | null }> {
            const { data, error } = await addClass({
                path: { datasetName, graphURI },
                body: classData,
            });

            if (error) {
                console.error(`[ClassStore] Failed to add class`, error);
                return { error, data: null };
            }

            // Invalidate list so new class appears
            this.invalidateGraph(datasetName, graphURI);

            return { error: null, data: data ?? null };
        },

        // ─── Invalidation ────────────────────────────────────────────────────

        invalidateClass(datasetName: string, graphURI: string, classUUID: string) {
            delete detailCache[makeClassKey(datasetName, graphURI, classUUID)];
        },

        invalidateGraph(datasetName: string, graphURI: string) {
            const graphPrefix = `${datasetName}::${graphURI}::`;
            for (const key of Object.keys(listCache)) {
                if (key.startsWith(graphPrefix)) delete listCache[key];
            }
            for (const key of Object.keys(detailCache)) {
                if (key.startsWith(graphPrefix)) delete detailCache[key];
            }
        },

        invalidateDataset(datasetName: string) {
            const prefix = `${datasetName}::`;
            for (const key of Object.keys(listCache)) {
                if (key.startsWith(prefix)) delete listCache[key];
            }
            for (const key of Object.keys(detailCache)) {
                if (key.startsWith(prefix)) delete detailCache[key];
            }
        },

        invalidateAll() {
            for (const key of Object.keys(listCache)) delete listCache[key];
            for (const key of Object.keys(detailCache)) delete detailCache[key];
        }
    };

    const listCache = $state<Record<string, DataState<ClassListItemDto[]>>>({});
    const detailCache = $state<Record<string, DataState<ClassUmlAdaptedDto>>>({});

    function makeGraphKey(datasetName: string, graphURI: string, includeExternal = false): GraphKey {
        return `${datasetName}::${graphURI}::${includeExternal ? "all" : "internal"}`;
    }

    function makeClassKey(datasetName: string, graphURI: string, classUUID: string): ClassKey {
        return `${datasetName}::${graphURI}::${classUUID}`;
    }

    function getOrInitList(key: string): DataState<ClassListItemDto[]> {
        if (!listCache[key]) listCache[key] = { data: null, fetchedAt: null, error: null };
        return listCache[key];
    }

    function getOrInitDetail(key: string): DataState<ClassUmlAdaptedDto> {
        if (!detailCache[key]) detailCache[key] = { data: null, fetchedAt: null, error: null };
        return detailCache[key];
    }
</script>