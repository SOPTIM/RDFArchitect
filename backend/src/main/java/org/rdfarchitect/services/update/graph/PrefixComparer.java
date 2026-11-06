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

import static org.rdfarchitect.rdf.RDFUtils.withColon;

import org.rdfarchitect.services.update.graph.PrefixScanner.ScannedFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Puts the prefixes of a dataset and those of the files being imported side by side. Two files
 * bringing the same prefix for different namespaces contest it as much as a file colliding with the
 * dataset does; without a decision the outcome would depend on the order the files are imported in.
 */
final class PrefixComparer {

    private PrefixComparer() {}

    /**
     * @param workspacePrefixes the prefixes of the dataset, prefix (without colon) to namespace URI
     * @param scannedFiles the prefixes of the files to import, in the order they are imported
     * @return one entry per prefix of either side, ordered by prefix
     */
    static List<PrefixComparison> compare(
            Map<String, String> workspacePrefixes, List<ScannedFile> scannedFiles) {
        var importedByPrefix = collectImported(scannedFiles);

        var prefixes = new TreeSet<>(workspacePrefixes.keySet());
        prefixes.addAll(importedByPrefix.keySet());

        var comparisons = new ArrayList<PrefixComparison>();
        for (var prefix : prefixes) {
            var workspaceIri = workspacePrefixes.get(prefix);
            var imported = importedByPrefix.getOrDefault(prefix, Map.of());

            var workspaceBinding =
                    workspaceIri == null ? null : new PrefixBinding(workspaceIri, List.of());
            var importedBindings =
                    imported.entrySet().stream()
                            .map(
                                    binding ->
                                            new PrefixBinding(
                                                    binding.getKey(),
                                                    List.copyOf(binding.getValue())))
                            .toList();

            comparisons.add(
                    new PrefixComparison(
                            withColon(prefix),
                            workspaceBinding,
                            importedBindings,
                            isContested(workspaceIri, imported.keySet())));
        }
        return List.copyOf(comparisons);
    }

    private static boolean isContested(String workspaceIri, Set<String> importedIris) {
        var claims = new HashSet<>(importedIris);
        if (workspaceIri != null) {
            claims.add(workspaceIri);
        }
        return claims.size() > 1;
    }

    /** prefix to namespace URI to the files declaring it, both in first-seen order. */
    private static Map<String, Map<String, List<String>>> collectImported(
            List<ScannedFile> scannedFiles) {
        var importedByPrefix = new LinkedHashMap<String, Map<String, List<String>>>();
        for (var scannedFile : scannedFiles) {
            for (var prefix : scannedFile.prefixes().entrySet()) {
                importedByPrefix
                        .computeIfAbsent(prefix.getKey(), _ -> new LinkedHashMap<>())
                        .computeIfAbsent(prefix.getValue(), _ -> new ArrayList<>())
                        .add(scannedFile.fileName());
            }
        }
        return importedByPrefix;
    }
}
