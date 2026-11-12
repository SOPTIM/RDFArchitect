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

export interface MediaType {
    mimeType: string;
    name: string;
    fileExtension: string;
}

export const supportedRDFMediaTypes: MediaType[] = [
    { mimeType: "application/rdf+xml", name: "RDF/XML", fileExtension: ".rdf" },
    { mimeType: "text/turtle", name: "TURTLE", fileExtension: ".ttl" },
    {
        mimeType: "application/n-triples",
        name: "N-TRIPLES",
        fileExtension: ".nt",
    },
];

export const sparqlMediaType: MediaType = {
    mimeType: "application/sparql-query",
    name: "SPARQL",
    fileExtension: ".sparql",
};

/** Whether the file is a ZIP archive, which the import unpacks on its own. */
export function isZipFile(fileName: string): boolean {
    return (fileName ?? "").toLowerCase().endsWith(".zip");
}

/** Whether the file holds a graph in one of the RDF syntaxes the import reads. */
export function isSupportedGraphFile(fileName: string): boolean {
    const lowered = (fileName ?? "").toLowerCase();
    return supportedRDFMediaTypes.some(type =>
        lowered.endsWith(type.fileExtension),
    );
}

/** Whether the import takes the file at all, be it as a graph or as an archive of graphs. */
export function isImportableFile(fileName: string): boolean {
    return isZipFile(fileName) || isSupportedGraphFile(fileName);
}

export function saveFile(
    blob: Blob,
    suggestedFilename: string,
    mediaType: MediaType,
): void {
    try {
        const fallbackName = mediaType?.fileExtension
            ? `file${mediaType.fileExtension}`
            : "file";
        const filename = suggestedFilename || fallbackName;

        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
        console.error("Error saving file:", err);
        throw err;
    }
}
