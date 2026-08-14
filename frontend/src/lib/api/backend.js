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
import { CGMESVersion } from "$lib/models/cgmes-constants.js";

export class BackendConnection {
    fetch;
    url;

    constructor(fetch, url) {
        this.fetch = fetch;
        this.url = url;
    }

    async fetchFilteredRenderingData(
        workspaceName,
        graphURI,
        graphFilter,
        signal,
    ) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/rendering`;
        return fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(graphFilter),
            credentials: "include",
            signal,
        });
    }

    async getWorkspaceNames() {
        const url = `${PUBLIC_BACKEND_URL}/datasets`;
        return fetch(url, {
            method: "GET",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCrossProfileID(workspaceName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/crossprofilediagramID`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getGraphs(workspaceName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async createWorkspace(workspaceName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}`;
        return fetch(url, {
            method: "PUT",
            credentials: "include",
        });
    }

    async deleteWorkspace(workspaceName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}`;
        return fetch(url, {
            method: "DELETE",
            credentials: "include",
        });
    }

    async renameWorkspace(workspaceName, newWorkspaceName) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/rename?newDatasetName=${encodeURIComponent(newWorkspaceName)}`;
        return fetch(url, {
            method: "POST",
            credentials: "include",
        });
    }

    async renameGraph(workspaceName, graphURI, newGraphURI) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/rename?newGraphURI=${encodeURIComponent(newGraphURI)}`;
        return fetch(url, {
            method: "POST",
            credentials: "include",
        });
    }

    async getClassInfo(
        workspaceName,
        graphURI,
        classUUID,
        includeSuperClasses = false,
    ) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}${includeSuperClasses ? "?includeSuperClasses=true" : ""}`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async replaceClass(workspaceName, graphURI, classUUID, newClass) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}`;
        return fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newClass),
            credentials: "include",
        });
    }

    async postClass(workspaceName, graphURI, newClass) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes`;
        return fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newClass),
            credentials: "include",
        });
    }

    async getPackages(workspaceName, graphURI, signal) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/packages`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
            signal,
        });
    }

    async getPackage(workspaceName, graphURI, packageUUID) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/packages/${encodeURIComponent(packageUUID)}`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getClasses(workspaceName, graphURI, includeExternalClasses = false) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes?includeExternalClasses=${includeExternalClasses}`;
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

    async getPrimitives(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/primitives`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getDataTypes(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/datatypes`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getStereotypes(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/stereotypes`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async putAttribute(workspaceName, graphURI, classUUID, attribute) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/attributes/${encodeURIComponent(attribute.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(attribute),
            credentials: "include",
        });
    }

    async postAttribute(workspaceName, graphURI, classUUID, attribute) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/attributes`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(attribute),
            credentials: "include",
        });
    }

    async putAssociationPair(
        workspaceName,
        graphURI,
        classUUID,
        associationPair,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/associations/${encodeURIComponent(associationPair.from.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(associationPair),
            credentials: "include",
        });
    }

    async postAssociationPair(workspaceName, graphURI, classUUID, association) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/associations`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(association),
            credentials: "include",
        });
    }

    async getDeletionImpact(workspaceName, graphURI, resourceUuids) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/deletion-impact`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(resourceUuids),
            credentials: "include",
        });
    }

    async deleteClass(workspaceName, graphURI, classUUID) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}`;
        return await fetch(url, {
            method: "DELETE",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async deleteResources(workspaceName, graphURI, deleteRequests) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/delete-requests`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(deleteRequests),
            credentials: "include",
        });
    }

    async postEnumEntry(workspaceName, graphURI, classUUID, enumEntry) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/enumentries`;
        return await fetch(url, {
            method: "POST",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(enumEntry),
            credentials: "include",
        });
    }

    async putEnumEntry(workspaceName, graphURI, classUUID, enumEntry) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/enumentries/${encodeURIComponent(enumEntry.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(enumEntry),
            credentials: "include",
        });
    }

    async getNamespaces(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/namespaces`;
        return await fetch(url, {
            method: "GET",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async replaceNamespaces(workspaceName, namespaces) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/namespaces`;
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

    async validateSchema(
        workspaceName,
        graphURI,
        cgmesVersion = CGMESVersion.V3_0,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/validate/${encodeURIComponent(cgmesVersion)}`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            credentials: "include",
        });
    }

    async validateFile(file, cgmesVersion = CGMESVersion.V3_0) {
        const url = `${PUBLIC_BACKEND_URL}/validate/${encodeURIComponent(cgmesVersion)}`;

        const formData = new FormData();
        formData.append("file", file);

        return fetch(url, {
            method: "POST",
            mode: "cors",
            body: formData,
            credentials: "include",
        });
    }

    async compareSchemas(workspaceName, graphURI, file) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/compare`;
        const formData = new FormData();
        formData.append("file", file);
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            body: formData,
            credentials: "include",
        });
    }

    async compareWorkspaceSchemas(
        workspaceName,
        graphURI,
        otherWorkspaceName,
        otherGraphURI,
    ) {
        const url =
            `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}` +
            `/graphs/${encodeURIComponent(graphURI)}/compare` +
            `?otherDataset=${encodeURIComponent(otherWorkspaceName)}` +
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

    async createSnapshot(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/snapshots`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            body: workspaceName,
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

    async isReadOnly(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/readonly`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            credentials: "include",
        });
    }

    async enableEditing(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/readonly`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            credentials: "include",
        });
    }

    async disableEditing(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/readonly`;
        return await fetch(url, {
            method: "DELETE",
            mode: "cors",
            credentials: "include",
        });
    }

    async getChangelog(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/changes`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async restoreVersion(workspaceName, graphURI, version) {
        console.log(`Restoring version ${version}`);
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/restore`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: version,
            credentials: "include",
        });
    }

    async putPackage(workspaceName, graphURI, pack) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/packages/${encodeURIComponent(pack.uuid)}`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            mode: "cors",
            body: JSON.stringify(pack),
            credentials: "include",
        });
    }

    async updateClassPositions(
        workspaceName,
        graphURI,
        packageUUID,
        classPositionDTOList,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/layout/${encodeURIComponent(packageUUID)}/classes`;
        return await fetch(url, {
            method: "PUT",
            headers: new Headers({ "Content-Type": "application/json" }),
            mode: "cors",
            body: JSON.stringify(classPositionDTOList),
            credentials: "include",
        });
    }

    async updateGlobalClassPositions(
        workspaceName,
        packageUUID,
        classPositionDTOList,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/layout/${encodeURIComponent(packageUUID)}/classes`;
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

    async getOntology(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async postOntology(workspaceName, graphURI, newOntology) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newOntology),
            credentials: "include",
        });
    }

    async putOntology(workspaceName, graphURI, newOntology) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newOntology),
            credentials: "include",
        });
    }

    async deleteOntology(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/ontology`;
        return await fetch(url, {
            method: "DELETE",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async generateOntologyEntries(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/ontology/generate`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async postPasteClasses(targetWorkspaceName, targetGraphURI, pasteRequest) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(targetWorkspaceName)}/graphs/${encodeURIComponent(targetGraphURI)}/paste`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(pasteRequest),
            credentials: "include",
        });
    }

    async postPastePreview(
        targetWorkspaceName,
        targetGraphURI,
        previewRequest,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(targetWorkspaceName)}/graphs/${encodeURIComponent(targetGraphURI)}/paste/preview`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(previewRequest),
            credentials: "include",
        });
    }

    async extendClass(workspaceName, graphURI, classUUID, body) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}/extend`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(body),
            credentials: "include",
        });
    }

    async getCustomDiagramsForGraph(workspaceName, graphURI) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/diagrams`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCustomDiagramsForWorkspace(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/diagrams`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCustomGraphDiagramRenderingData(
        workspaceName,
        graphURI,
        diagramId,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCustomWorkspaceDiagramRenderingData(workspaceName, diagramId) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCrossProfileDiagramRenderingDataForWorkspace(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/crossprofilediagramRendering`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCrossProfileDiagramForWorkspace(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/crossprofilediagram`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async getCrossProfileColorData(workspaceName) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/crossprofilediagramColors`;
        return await fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }

    async putCrossProfileColorData(workspaceName, colorData) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/crossprofilediagramColors`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(colorData),
            credentials: "include",
        });
    }

    async putCustomDiagram(workspaceName, graphURI, diagramId, newDiagram) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newDiagram),
            credentials: "include",
        });
    }

    async putCustomWorkspaceDiagram(workspaceName, diagramId, newDiagram) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "PUT",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(newDiagram),
            credentials: "include",
        });
    }

    async addToCustomGraphDiagram(workspaceName, graphURI, diagramId, classes) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/diagrams/${encodeURIComponent(diagramId)}/classes`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(classes),
            credentials: "include",
        });
    }

    async addToCustomWorkspaceDiagram(workspaceName, diagramId, classes) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/diagrams/${encodeURIComponent(diagramId)}/classes`;
        return await fetch(url, {
            method: "POST",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(classes),
            credentials: "include",
        });
    }

    async removeFromCustomGraphDiagram(
        workspaceName,
        graphURI,
        diagramId,
        classIds,
    ) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/diagrams/${encodeURIComponent(diagramId)}/classes`;
        return await fetch(url, {
            method: "DELETE",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(classIds),
            credentials: "include",
        });
    }

    async removeFromCustomWorkspaceDiagram(workspaceName, diagramId, classIds) {
        let url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/diagrams/${encodeURIComponent(diagramId)}/classes`;
        return await fetch(url, {
            method: "DELETE",
            headers: new Headers({ "Content-Type": "application/json" }),
            body: JSON.stringify(classIds),
            credentials: "include",
        });
    }

    async deleteCustomWorkspaceDiagram(workspaceName, diagramId) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "DELETE",
            credentials: "include",
        });
    }

    async deleteCustomGraphDiagram(workspaceName, graphUri, diagramId) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphUri)}/diagrams/${encodeURIComponent(diagramId)}`;
        return await fetch(url, {
            method: "DELETE",
            credentials: "include",
        });
    }

    async resetSession() {
        const url = `${PUBLIC_BACKEND_URL}/session`;
        return fetch(url, {
            method: "DELETE",
            credentials: "include",
        });
    }

    async getHTMLExport(
        workspaceName,
        graphURI,
        fileEnding,
        embedDiagrams = false,
        signal,
    ) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/htmlexport/${encodeURIComponent(fileEnding)}?embedDiagrams=${embedDiagrams}`;
        return await fetch(url, {
            method: "GET",
            headers: new Headers({ "Content-Type": "application/json" }),
            mode: "cors",
            credentials: "include",
            signal,
        });
    }

    async getAsciiDocExport(
        workspaceName,
        graphURI,
        fileEnding,
        embedDiagrams = false,
        signal,
    ) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(workspaceName)}/graphs/${encodeURIComponent(graphURI)}/asciidocexport/${encodeURIComponent(fileEnding)}?embedDiagrams=${embedDiagrams}`;
        return await fetch(url, {
            method: "GET",
            headers: new Headers({ "Content-Type": "application/json" }),
            mode: "cors",
            credentials: "include",
            signal,
        });
    }
}
