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

package org.rdfarchitect.services.update.graph;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportWarning;
import org.rdfarchitect.services.update.graph.ImportXmiUseCase.ScannedDiagram;
import org.rdfarchitect.services.update.graph.ImportXmiUseCase.ScannedPackage;
import org.rdfarchitect.services.update.graph.ImportXmiUseCase.ScannedPrefix;
import org.rdfarchitect.services.update.graph.ImportXmiUseCase.SupplierGroup;

import java.util.List;

/**
 * What an import job produced. A job produces exactly one of these, decided by what it was started
 * to do, so that reading a result never means telling an empty one from an inapplicable one.
 *
 * <p>Each subtype is named after its class, because the generated OpenAPI contract names its
 * schemas that way and the client reads the discriminator against those names. A name chosen freely
 * here would leave the contract saying one thing and the wire carrying another.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ImportOutcome.GraphsImported.class, name = "GraphsImported"),
    @JsonSubTypes.Type(value = ImportOutcome.PackagesScanned.class, name = "PackagesScanned")
})
public sealed interface ImportOutcome {

    /**
     * What importing RDF files wrote.
     *
     * @param graphUris the uris of the graphs that were imported
     * @param failedFileNames the original filenames of the files that could not be imported
     * @param warnings non-fatal warnings about content that was imported but cannot be displayed
     */
    record GraphsImported(
            List<String> graphUris, List<String> failedFileNames, List<ImportWarning> warnings)
            implements ImportOutcome {

        /** What a graph import that never ran produced. */
        static GraphsImported nothing() {
            return new GraphsImported(List.of(), List.of(), List.of());
        }
    }

    /**
     * What reading Enterprise Architect exports found. Nothing was stored: which of the packages
     * become schemas is a later decision.
     *
     * @param packages the packages the exports hold, in the order the exports declare them
     * @param diagrams the class diagrams they hold, each saying what its availability depends on
     * @param supplierGroups the sets of packages the diagrams refer to, shared between them
     * @param classNames every class name the exports hold, which the packages refer to by position.
     *     Pooled rather than repeated per package: the CGMES 3.0 model holds 6425 classes and its
     *     packages name them 35863 times between them
     * @param prefixes the namespace prefix table of the exports, which the import carries over as
     *     the workspace's. Reported so that a caller can show what it would be creating: only the
     *     model and the extension namespace decide what a resource is called, the rest are
     *     declarations and can be changed any time afterwards
     */
    record PackagesScanned(
            List<ScannedPackage> packages,
            List<ScannedDiagram> diagrams,
            List<SupplierGroup> supplierGroups,
            List<String> classNames,
            List<ScannedPrefix> prefixes)
            implements ImportOutcome {

        /** What a read that never ran produced. */
        static PackagesScanned nothing() {
            return new PackagesScanned(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }
}
