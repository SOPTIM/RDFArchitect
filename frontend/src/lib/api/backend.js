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

    async fetchMermaidUMLFiltered(datasetName, graphURI, graphFilter) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/mermaid/UMLDiagram`;
        return fetch(url, {
            method: "Post",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(graphFilter),
            credentials: "include",
        });
    }

    async getDatasetNames() {
        const url = `${PUBLIC_BACKEND_URL}/datasets`;
        return fetch(url, {
            method: "GET",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getGraphNames(datasetName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async deleteDataset(datasetName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}`;
        return fetch(url, {
            method: "DELETE",
            credentials: "include",
        });
    }

    async getClassInfo(datasetName, graphURI, classUUID) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async replaceClass(datasetName, graphURI, classUUID, newClass) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}`;
        return fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newClass),
            credentials: "include",
        });
    }

    async getPackages(datasetName, graphURI) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/packages`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getClasses(datasetName, graphURI) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
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

    async putAttribute(datasetName, graphURI, classUUID, attribute) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/attributes/${encodeURIComponent(attribute.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(attribute),
            credentials: "include",
        });
    }

    async postAttribute(datasetName, graphURI, classUUID, attribute) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/attributes`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(attribute),
            credentials: "include",
        });
    }

    async putAssociationPair(
        datasetName,
        graphURI,
        classUUID,
        associationPair,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/associations/${encodeURIComponent(associationPair.from.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(associationPair),
            credentials: "include",
        });
    }

    async postAssociationPair(datasetName, graphURI, classUUID, association) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/associations`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(association),
            credentials: "include",
        });
    }

    async deleteClass(datasetName, graphURI, classUUID) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}`;
        return await fetch(url, {
            method: "DELETE",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async postEnumEntry(datasetName, graphURI, classUUID, enumEntry) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/enumentries`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(enumEntry),
            credentials: "include",
        });
    }

    async putEnumEntry(datasetName, graphURI, classUUID, enumEntry) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/enumentries/${encodeURIComponent(enumEntry.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(enumEntry),
            credentials: "include",
        });
    }

    async getNamespaces(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/namespaces`;
        return await fetch(url, {
            method: "GET",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async replaceNamespaces(datasetName, namespaces) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/namespaces`;
        return fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(namespaces),
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

    async isReadOnly(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/readonly`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            credentials: "include",
        });
    }

    async enableEditing(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/readonly`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            credentials: "include",
        });
    }

    async disableEditing(datasetName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/readonly`;
        return await fetch(url, {
            method: "DELETE",
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

    async putPackage(datasetName, graphURI, pack) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/packages/${encodeURIComponent(pack.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            mode: "cors",
            body: JSON.stringify(pack),
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

    async deleteOntology(datasetName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "DELETE",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
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
}
