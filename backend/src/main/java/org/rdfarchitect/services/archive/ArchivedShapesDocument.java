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

package org.rdfarchitect.services.archive;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import org.rdfarchitect.database.ShapesDocument;

import java.util.List;
import java.util.UUID;

/**
 * A shapes document as {@code shacl/documents.json} lists it. Its text is in a file of its own,
 * named after the id.
 *
 * @param schemas names of the schemas it applies to, which need not be schemas of the file
 */
public record ArchivedShapesDocument(
        UUID id,
        String name,
        ShapesDocument.Origin origin,
        @JsonSetter(nulls = Nulls.SET) String sourceFileName,
        Boolean enabled,
        Integer order,
        List<String> schemas) {

    /** Describes {@code document}, from inside a transaction on its workspace. */
    public static ArchivedShapesDocument of(ShapesDocument document) {
        return new ArchivedShapesDocument(
                document.getId(),
                document.getName(),
                document.getOrigin(),
                document.getSourceFileName(),
                document.isEnabled(),
                document.getOrder(),
                document.getSchemas().stream().sorted().toList());
    }
}
