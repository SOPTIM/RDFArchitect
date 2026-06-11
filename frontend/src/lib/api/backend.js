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

    async fetchFilteredRenderingData(datasetName, graphURI, graphFilter) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/rendering`;
        return fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(graphFilter),
            credentials: "include",
        });
    }

    async getXSDPrimitives() {
        const url = `${PUBLIC_BACKEND_URL}/primitiveDatatypes`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getPrimitives(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/primitives`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getDataTypes(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/datatypes`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getStereotypes(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/stereotypes`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getDeleteRelation(datasetName, graphURI, resourceUuid) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/uuid/${encodeURIComponent(resourceUuid)}/deletion-impact`;
        return await fetch(url, {
            method: "GET",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async deleteResources(datasetName, graphURI, deleteRequests) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/delete-requests`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(deleteRequests),
            credentials: "include",
        });
    }

    async getSearchResults(query, body) {
        let url = `${PUBLIC_BACKEND_URL}/search?query=${encodeURIComponent(query)}`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(body),
            credentials: "include",
        });
    }

    async compareSchemas(datasetName, graphURI, file) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/compare`;
        const formData = new FormData();
        formData.append("file", file);
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            body: formData,
            credentials: "include",
        });
    }

    async compareDatasetSchemas(
        datasetName,
        graphURI,
        otherDatasetName,
        otherGraphURI,
    ) {
        const url =
            `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}` +
            `/graphs/${encodeURIComponent(graphURI)}/compare` +
            `?otherDataset=${encodeURIComponent(otherDatasetName)}` +
            `&otherGraph=${encodeURIComponent(otherGraphURI)}`;

        return fetch(url, {
            method: "GET",
            mode: "cors",
            credentials: "include",
        });
    }

    async compareSchemasFromFiles(fileA, fileB) {
        const url = `${PUBLIC_BACKEND_URL}/compare`;

        const formData = new FormData();
        formData.append("fileA", fileA);
        formData.append("fileB", fileB);

        return fetch(url, {
            method: "POST",
            mode: "cors",
            body: formData,
            credentials: "include",
        });
    }

    async createSnapshot(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/snapshots`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            body: datasetName,
            credentials: "include",
        });
    }

    async loadSnapshot(base64Token) {
        let url = `${PUBLIC_BACKEND_URL}/snapshots/${encodeURIComponent(base64Token)}`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            credentials: "include",
        });
    }

    async getChangelog(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/changes`;
        return await fetch(url, {
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

    async getKnownOntologyFields() {
        let url = `${PUBLIC_BACKEND_URL}/ontology-fields`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getOntology(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async postOntology(datasetName, graphURI, newOntology) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newOntology),
            credentials: "include",
        });
    }

    async putOntology(datasetName, graphURI, newOntology) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newOntology),
            credentials: "include",
        });
    }

    async generateOntologyEntries(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/ontology/generate`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async postCopyClass(datasetName, graphURI, classUUID, targetInfo) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/copy`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(targetInfo),
            credentials: "include",
        });
    }

    async extendClass(datasetName, graphURI, classUUID, body) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/extend`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(body),
            credentials: "include",
        });
    }

    async getCustomDiagramsForGraph(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/diagrams`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCustomDiagramsForDataset(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/diagrams`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCustomGraphDiagramRenderingData(datasetName, graphURI, diagramId) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCustomDatasetDiagramRenderingData(datasetName, diagramId) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }
}
