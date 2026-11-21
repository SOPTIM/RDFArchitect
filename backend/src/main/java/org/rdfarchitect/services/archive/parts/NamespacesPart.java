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

package org.rdfarchitect.services.archive.parts;

import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.util.Map;
import java.util.TreeMap;

/** The namespace prefixes of the workspace, {@code namespaces.json}. */
public final class NamespacesPart
        implements WorkspaceFilePart<WorkspaceTransaction, PrefixMapping> {

    @Override
    public void write(WorkspaceTransaction source, PartWriter out) {
        out.writeJson(
                WorkspaceArchiveFormat.NAMESPACES,
                new TreeMap<>(source.prefixes().getNsPrefixMap()));
    }

    @Override
    public PrefixMapping read(PartReader in) {
        var prefixes =
                in.readJson(
                        WorkspaceArchiveFormat.NAMESPACES,
                        new TypeReference<Map<String, String>>() {});
        if (prefixes == null) {
            return null;
        }
        var mapping = new PrefixMappingImpl();
        var valid = true;
        for (var prefix : prefixes.entrySet()) {
            try {
                mapping.setNsPrefix(prefix.getKey(), prefix.getValue());
            } catch (PrefixMapping.IllegalPrefixException _) {
                in.problem(
                        WorkspaceArchiveFormat.NAMESPACES,
                        "\"%s\" is not a valid namespace prefix.".formatted(prefix.getKey()));
                valid = false;
            }
        }
        return valid ? mapping : null;
    }

    @Override
    public void restore(WorkspaceTransaction target, PrefixMapping prefixes) {
        target.setPrefixes(prefixes);
    }
}
