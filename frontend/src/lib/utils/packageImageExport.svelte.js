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

import * as htmlToImage from "html-to-image";
import { mount, unmount, tick } from "svelte";

import { BackendConnection } from "$lib/api/backend.js";
import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import PackageSnapshotRenderer from "$lib/rendering/svelteflow/PackageSnapshotRenderer.svelte";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

/**
 * Filename the backend embeds in the exported document for a package diagram
 * (see ExportHTMLService/ExportAsciiDocService#buildPackageReference). Kept in
 * sync with the backend so the generated images can be found again by name.
 */
function sourceFilenameOf(packageUUID, fileEnding) {
    return `${packageUUID}.${fileEnding}`;
}

/** Turns a package label into a filesystem-safe, human-readable slug. */
function slugifyLabel(label) {
    const slug = (label ?? "")
        .trim()
        .replace(/[^A-Za-z0-9_-]+/g, "_")
        .replace(/_+/g, "_")
        .replace(/^_|_$/g, "");
    return slug || "package";
}

/**
 * Human-readable filename for a package diagram. The uuid is always appended
 * so that two packages with the same (or similarly sanitized) label never
 * collide; "default" - the catch-all bucket for classes without a package -
 * has no uuid and is left as is.
 */
function displayFilenameOf(packageUUID, label, fileEnding) {
    if (packageUUID === "default") {
        return sourceFilenameOf(packageUUID, fileEnding);
    }
    return `${slugifyLabel(label)}-${packageUUID}.${fileEnding}`;
}

async function getAllPackages(datasetName, graphURI) {
    const res = await bec.getPackages(datasetName, graphURI);
    const json = await res.json();
    return [
        ...(json.internalPackageList ?? []),
        ...(json.externalPackageList ?? []),
    ];
}

async function getPackageDiagram(datasetName, graphURI, packageUUID) {
    const res = await bec.fetchFilteredRenderingData(datasetName, graphURI, {
        packageUUID,
        includeEnumEntries: true,
        includeAttributes: true,
        includeAssociations: true,
        includeInheritance: true,
        includeRelationsToExternalPackages: true,
    });
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

async function renderPackage(nodes, edges, fileType) {
    const clipper = document.createElement("div");
    clipper.style.position = "fixed";
    clipper.style.top = "0";
    clipper.style.left = "0";
    clipper.style.width = "0";
    clipper.style.height = "0";
    clipper.style.overflow = "hidden";
    clipper.style.zIndex = "-1";
    clipper.setAttribute("aria-hidden", "true");

    const container = document.createElement("div");
    container.style.display = "inline-block";
    container.style.background = "#ffffff";

    clipper.appendChild(container);
    document.body.appendChild(clipper);

    const props = $state({ nodes, edges, ready: false, size: null });
    const app = mount(PackageSnapshotRenderer, { target: container, props });

    try {
        for (let i = 0; i < 400 && !props.ready; i++) {
            await new Promise(r => setTimeout(r, 25));
        }
        if (!props.ready) {
            toastStore.error(
                "Rendering Issue during Export",
                "Rendering the package diagram took too long. The image may be incomplete.",
                { duration: 10000 },
            );
        }
        await tick();
        await document.fonts.ready;
        await new Promise(r => requestAnimationFrame(r));

        const { width, height } = props.size ?? { width: 1600, height: 1000 };
        if (fileType.ending === "png") {
            return await htmlToImage.toBlob(container, {
                backgroundColor: "#ffffff",
                pixelRatio: 3,
                width,
                height,
                cacheBust: true,
            });
        } else {
            const dataUrl = await htmlToImage.toSvg(container, {
                backgroundColor: "#ffffff",
                width,
                height,
                cacheBust: true,
            });

            const res = await fetch(dataUrl);
            return await res.blob();
        }
    } finally {
        unmount(app);
        clipper.remove();
    }
}

export async function generatePackageImages(datasetName, graphURI, fileType) {
    const images = [];
    const packages = await getAllPackages(datasetName, graphURI);

    for (const pkg of packages) {
        const packageUUID = pkg.uuid ?? "default";
        const label = pkg.label ?? "default";
        try {
            const diagram = await getPackageDiagram(
                datasetName,
                graphURI,
                packageUUID,
            );
            if (
                !diagram ||
                diagram.format !== "SVELTEFLOW" ||
                !diagram.nodes?.length
            ) {
                continue;
            }
            const blob = await renderPackage(
                diagram.nodes,
                diagram.edges ?? [],
                fileType,
            );

            if (blob) {
                images.push({
                    sourceFilename: sourceFilenameOf(
                        packageUUID,
                        fileType.ending,
                    ),
                    filename: displayFilenameOf(
                        packageUUID,
                        label,
                        fileType.ending,
                    ),
                    blob,
                });
            }
        } catch (e) {
            console.error(
                `Failed to render package "${label}" as ${fileType.name}:`,
                e,
            );
        }
    }
    return images;
}
