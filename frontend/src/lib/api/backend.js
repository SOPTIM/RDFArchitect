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

import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";

export class BackendConnection {
    fetch;
    url;

    constructor(fetch, url) {
        this.fetch = fetch;
        this.url = url;
    }

    async getCrossProfileID(datasetName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/crossprofilediagramID`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async restoreVersion(datasetName, graphURI, version) {
        console.log(`Restoring version ${version}`);
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/restore`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: version,
            credentials: "include",
        });
    }

    async updateClassPositions(
        datasetName,
        graphURI,
        packageUUID,
        classPositionDTOList,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/layout/${encodeURIComponent(packageUUID)}/classes`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            mode: "cors",
            body: JSON.stringify(classPositionDTOList),
            credentials: "include",
        });
    }

    async updateGlobalClassPositions(
        datasetName,
        packageUUID,
        classPositionDTOList,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/layout/${encodeURIComponent(packageUUID)}/classes`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            mode: "cors",
            body: JSON.stringify(classPositionDTOList),
            credentials: "include",
        });
    }

    async postPasteClasses(targetDatasetName, targetGraphURI, pasteRequest) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(targetDatasetName)}/graphs/${encodeURIComponent(targetGraphURI)}/paste`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(pasteRequest),
            credentials: "include",
        });
    }

    async getCrossProfileDiagramRenderingDataForDataset(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/crossprofilediagramRendering`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCrossProfileDiagramForDataset(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/crossprofilediagram`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCrossProfileColorData(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/crossprofilediagramColors`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async putCrossProfileColorData(datasetName, colorData) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/crossprofilediagramColors`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(colorData),
            credentials: "include",
        });
    }
}
