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

package org.rdfarchitect.services.update.dataset;

import lombok.RequiredArgsConstructor;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.rdfarchitect.models.cim.data.dto.CIMPrefixPair;
import org.rdfarchitect.database.DatabasePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UpdateDatasetService implements DeleteDatasetUseCase, ReplaceNamespacesUseCase {

    private final DatabasePort databasePort;

    @Override
    public void deleteDataset(String datasetName) {
        databasePort.deleteDataset(datasetName);
    }

    @Override
    public void replaceNamespaces(String datasetName, List<CIMPrefixPair> namespaces) {
        var prefixMapping = new PrefixMappingImpl();
        for (var namespace : namespaces) {
            var substitutedPrefix = Objects.requireNonNullElse(namespace.getSubstitutedPrefix(), "");
            if (substitutedPrefix.endsWith(":")) {
                substitutedPrefix = substitutedPrefix.substring(0, substitutedPrefix.length() - 1);
            }
            if(prefixMapping.getNsPrefixURI(substitutedPrefix) != null){
                throw new IllegalArgumentException("Duplicate namespace prefix detected: " + substitutedPrefix);
            }
            prefixMapping.setNsPrefix(substitutedPrefix, namespace.getPrefix());
        }
        databasePort.setPrefixMapping(datasetName, prefixMapping);
    }
}
