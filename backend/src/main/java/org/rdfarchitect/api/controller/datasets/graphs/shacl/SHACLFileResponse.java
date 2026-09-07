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

package org.rdfarchitect.api.controller.datasets.graphs.shacl;

import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * What a SHACL download is negotiated and named as.
 *
 * <p>Four endpoints answer with one — the graph's shapes, the generated ones, the custom ones, and
 * a chosen selection of documents — and they have to agree on the media types offered and on what
 * the file is called. Each held its own copy of both before this existed, so adding a media type
 * meant four edits and the copies were free to drift apart.
 */
public final class SHACLFileResponse {

    /**
     * The media types offered, in the order they are preferred.
     *
     * <p>A list rather than a map, because an Accept header may name several of them and the answer
     * has to be the same every time. An immutable map iterates in an order the JDK is free to vary
     * between runs, so picking from one made the chosen format depend on it.
     */
    private static final List<Map.Entry<String, RDFFormat>> SUPPORTED_FORMATS =
            List.of(
                    Map.entry("text/turtle", RDFFormat.TURTLE),
                    Map.entry("application/rdf+xml", RDFFormat.RDFXML),
                    Map.entry("application/rdf+json", RDFFormat.RDFJSON),
                    Map.entry("application/n-triples", RDFFormat.NTRIPLES));

    private SHACLFileResponse() {}

    /** The first supported type the header names, preferring the earliest listed above. */
    public static RDFFormat rdfFormat(String acceptHeader) {
        for (var entry : SUPPORTED_FORMATS) {
            if (acceptHeader.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("unsupported Media Type");
    }

    /**
     * The download, named after the graph it was exported from.
     *
     * <p>{@code Content-Disposition} carries the bare file name rather than {@code attachment;
     * filename="…"}. That is not what the header means, but every export dialog in the frontend
     * reads the header's whole value as the name, and the same shape is used by the graph and
     * documentation exports — so correcting it is a change to all of them at once, not to this.
     */
    public static ResponseEntity<byte[]> of(
            String extendedGraphURI, RDFFormat format, ByteArrayOutputStream outStream) {
        var fileName =
                "default".equals(extendedGraphURI)
                        ? "shacl"
                        : new URI(extendedGraphURI + "-shacl").getSuffix();
        fileName += "." + format.getLang().getFileExtensions().getFirst();

        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of("Content-Disposition"));
        return ResponseEntity.ok()
                .headers(headers)
                .header(HttpHeaders.CONTENT_DISPOSITION, fileName)
                .body(outStream.toByteArray());
    }
}
