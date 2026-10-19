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

package org.rdfarchitect.database.inmemory;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.models.changelog.ValueChange;
import org.rdfarchitect.rdf.graph.wrapper.SnapshotParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The namespace prefixes shared by all graphs of a workspace. */
class WorkspacePrefixes extends SnapshotParticipant<Map<String, String>> {

    private final PrefixMapping prefixes = new PrefixMappingImpl();

    WorkspacePrefixes(WorkspaceTransactionContext txnContext) {
        super(txnContext);
    }

    /** Returns a view callers cannot write through. */
    PrefixMappingReadOnly readOnlyView() {
        return new PrefixMappingReadOnly(prefixes);
    }

    String expandPrefix(String prefixed) {
        return prefixes.expandPrefix(prefixed);
    }

    /** Replaces all prefixes. */
    void setAll(PrefixMapping newPrefixes) {
        beginChange();
        prefixes.clearNsPrefixMap();
        prefixes.setNsPrefixes(newPrefixes);
    }

    /** Adds prefixes, keeping those that are not mentioned. */
    void addAll(PrefixMapping additionalPrefixes) {
        beginChange();
        prefixes.setNsPrefixes(additionalPrefixes);
    }

    @Override
    protected Map<String, String> snapshot() {
        return new HashMap<>(prefixes.getNsPrefixMap());
    }

    @Override
    protected void restore(Map<String, String> state) {
        prefixes.clearNsPrefixMap();
        prefixes.setNsPrefixes(state);
    }

    @Override
    protected List<ValueChange> describeValueChanges(
            Map<String, String> before, Map<String, String> after) {
        return ValueChange.between(before, after);
    }
}
