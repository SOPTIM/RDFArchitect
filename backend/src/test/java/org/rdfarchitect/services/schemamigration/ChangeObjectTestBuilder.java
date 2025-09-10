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

package org.rdfarchitect.services.schemamigration;

import lombok.experimental.UtilityClass;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

@UtilityClass
public class ChangeObjectTestBuilder {
    public SemanticResourceChange resourceChange(String label, SemanticResourceChangeType type) {
        return SemanticResourceChange.builder()
                                     .label(label)
                                     .semanticResourceChangeType(type)
                                     .build();
    }

    public SemanticFieldChange fieldChange(SemanticFieldChangeType type, String from, String to) {
        return SemanticFieldChange.builder()
                                  .semanticFieldChangeType(type)
                                  .from(from)
                                  .to(to)
                                  .build();
    }

    public SemanticClassChange classChange(String label, SemanticResourceChangeType type) {
        return SemanticClassChange.builder()
                                  .label(label)
                                  .semanticResourceChangeType(type)
                                  .build();
    }
}
