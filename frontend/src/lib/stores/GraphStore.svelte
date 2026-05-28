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

    import { listGraphs, deleteGraph, Uri } from "../api/generated";

    type GraphURIState = {
        data: Uri[] | null;
        fetchedAt: number | null;
        error: unknown;
    };
    const pending: Record<string, Promise<void>> = {};

    export const graphStore = {
        getGraphs(datasetName: string) {
            return {
                get data() {
                    return getOrInit(datasetName).data;
                },
                get error() {
                    return getOrInit(datasetName).error;
                },
                get loading() {
                    return datasetName in pending;
                }
            };
        },

        async load(datasetName: string, force = false) {
            if (!datasetName) return;

            const state = getOrInit(datasetName);
            if (!force && state.data !== null) return;
            if (pending[datasetName]) return pending[datasetName];

            pending[datasetName] = (async () => {
                try {
                    const { data, error } = await listGraphs({ path: { datasetName } });

                    if (error) {
                        cache[datasetName] = { ...cache[datasetName], error };
                    } else {
                        cache[datasetName] = {
                            data: data ?? [],
                            fetchedAt: Date.now(),
                            error: null
                        };
                    }
                } catch (err) {
                    cache[datasetName] = { ...cache[datasetName], error: err };
                } finally {
                    delete pending[datasetName];
                }
            })();

            return pending[datasetName];
        },

        async remove(datasetName: string, graphURI: string) {
            const { error } = await deleteGraph({ path: { datasetName, graphURI } });
            if (error) return { error };

            const state = cache[datasetName];
            if (state?.data) {
                cache[datasetName] = {
                    ...state,
                    data: state.data.filter(g => `${g.prefix ?? ""}${g.suffix}` !== graphURI)
                };
            }

            return { error: null };
        },

        invalidateDataset(datasetName: string) {
            delete cache[datasetName];
        },

        invalidateAll() {
            for (const key of Object.keys(cache)) {
                delete cache[key];
            }
        }
    };

    const cache = $state<Record<string, GraphURIState>>({});

    function getOrInit(datasetName: string): GraphURIState {
        if (!cache[datasetName]) {
            cache[datasetName] = { data: null, fetchedAt: null, error: null };
        }
        return cache[datasetName];
    }
</script>
    