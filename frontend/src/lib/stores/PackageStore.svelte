<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script module lang="ts">
    import { listPackages, type ListPackagesResponse, PackageDto } from "../api/generated";

    type PackageListInfo = {
        internal: PackageDto[];
        external: PackageDto[];
    };

    type GraphPackageState = {
        data: PackageListInfo | null;
        fetchedAt: number | null;
        error: unknown;
    };

    type GraphKey = `${string}::${string}`;
    const pending: Record<string, Promise<void>> = {};

    export const packageStore = {
        getPackages(datasetName: string, graphURI: string) {
            const key = makeKey(datasetName, graphURI);
            return {
                get data() {
                    return getOrInit(key).data;
                },
                get error() {
                    return getOrInit(key).error;
                },
                get loading() {
                    return key in pending;
                }
            };
        },

        async load(datasetName: string, graphURI: string, force = false) {
            if (!datasetName || !graphURI) return;

            const key = makeKey(datasetName, graphURI);
            const state = getOrInit(key);

            if (!force && state.data !== null) return;
            if (pending[key]) return pending[key];

            pending[key] = (async () => {
                try {
                    const { data, error } = await listPackages({
                        path: { datasetName, graphURI }
                    });

                    if (error) {
                        console.error(`[PackageStore] Failed to load packages for ${key}`, error);
                        cache[key] = { ...cache[key], error };
                    } else {
                        const response = data as ListPackagesResponse | undefined;
                        cache[key] = {
                            data: {
                                internal: response?.internalPackageList ?? [],
                                external: response?.externalPackageList ?? []
                            },
                            fetchedAt: Date.now(),
                            error: null
                        };
                    }
                } catch (err) {
                    console.error(`[PackageStore] Failed to load packages for ${key}`, err);
                    cache[key] = { ...cache[key], error: err };
                } finally {
                    delete pending[key];
                }
            })();

            return pending[key];
        },

        async fetchPackages(datasetName: string, graphURI: string, force = false): Promise<PackageListInfo> {
            await this.load(datasetName, graphURI, force);
            return this.getPackages(datasetName, graphURI).data ?? { internal: [], external: [] };
        },

        invalidateGraph(datasetName: string, graphURI: string) {
            delete cache[makeKey(datasetName, graphURI)];
        },

        invalidateDataset(datasetName: string) {
            const prefix = `${datasetName}::`;
            for (const key of Object.keys(cache)) {
                if (key.startsWith(prefix)) delete cache[key];
            }
        },

        invalidateAll() {
            for (const key of Object.keys(cache)) {
                delete cache[key];
            }
        }
    };

    const cache = $state<Record<string, GraphPackageState>>({});

    function makeKey(datasetName: string, graphURI: string): GraphKey {
        return `${datasetName}::${graphURI}`;
    }

    function getOrInit(key: string): GraphPackageState {
        if (!cache[key]) {
            cache[key] = { data: null, fetchedAt: null, error: null };
        }
        return cache[key];
    }
</script>