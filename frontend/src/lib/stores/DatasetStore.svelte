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
        listDatasets,
        deleteDataset,
        CimPrefixPair,
        replaceNamespaces,
    } from "../api/generated";

    export type DatasetInfo = {
        label: string;
        readonly: boolean | null;
        prefixes: CimPrefixPair[];
    };

    export const datasetStore = {
        get data() { return data; },
        get fetchedAt() { return fetchedAt; },
        get error() { return error; },
        get loading() { return pending !== null; },

        async load(force = false) {
            if (!force && data !== null) return;
            if (pending) return pending;

            pending = (async () => {
                try {
                    const { data: result, error: err } = await listDatasets();
                    if (err) { error = err; return; }

                    data = (result ?? []).map(d => ({
                        label: d.name ?? "",
                        readonly: d.readonly ?? null,
                        prefixes: d.prefixes ?? [],
                    }));
                    fetchedAt = Date.now();
                    error = null;
                } catch (err) {
                    error = err;
                } finally {
                    pending = null;
                }
            })();

            return pending;
        },

        async remove(datasetName: string) {
            const { error: err } = await deleteDataset({ path: { datasetName } });
            if (err) return { error: err };

            data = data?.filter(d => d.label !== datasetName) ?? null;
            return { error: null };
        },

        invalidate() {
            this.load(true);
        },

        isReadOnly(datasetName: string): boolean | null {
            const dataset = data?.find(d => d.label === datasetName);
            if (!dataset) {
                console.warn(`isReadOnly called before dataset "${datasetName}" was loaded`);
                return null;
            }
            return dataset.readonly;
        },

        getNamespaces(datasetName: string): CimPrefixPair[] {
            const dataset = data?.find(d => d.label === datasetName);
            if (!dataset) {
                console.warn(`getNamespaces called before dataset "${datasetName}" was loaded`);
                return [];
            }
            return dataset.prefixes;
        },

        async saveNamespaces(datasetName: string, namespaces: CimPrefixPair[]) {
            const { error: err } = await replaceNamespaces({
                path: { datasetName },
                body: namespaces,
            });
            if (err) return { error: err };

            data = data?.map(d =>
                d.label === datasetName ? { ...d, prefixes: namespaces } : d
            ) ?? null;
            return { error: null };
        },

        updateReadonly(datasetName: string, readonly: boolean) {
            data = data?.map(d =>
                d.label === datasetName ? { ...d, readonly } : d
            ) ?? null;
        },
    };

    let data = $state<DatasetInfo[] | null>(null);
    let fetchedAt = $state<number | null>(null);
    let error = $state<unknown>(null);
    let pending: Promise<void> | null = null;
</script>
