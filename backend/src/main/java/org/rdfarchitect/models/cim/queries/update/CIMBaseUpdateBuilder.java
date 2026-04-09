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

package org.rdfarchitect.models.cim.queries.update;

import org.apache.jena.arq.querybuilder.UpdateBuilder;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shared.PrefixMapping;

import java.util.Map;

/**
 * Class for creating an {@link UpdateBuilder} to be used in the context of other CIM methods.
 */
public class CIMBaseUpdateBuilder {

    private final UpdateBuilder baseUpdate;

    public CIMBaseUpdateBuilder() {
        baseUpdate = new UpdateBuilder();
    }

    public UpdateBuilder build() {
        return baseUpdate;
    }

    /**
     * Adds a map of prefixes substituted->extended.
     *
     * @param prefixes {@link Map} containing prefixes
     *
     * @return {@link CIMBaseUpdateBuilder this}
     */
    public CIMBaseUpdateBuilder addPrefixes(Map<String, String> prefixes) {
        baseUpdate.addPrefixes(prefixes);
        return this;
    }

    /**
     * Adds a map of prefixes substituted->extended.
     *
     * @param prefixes {@link PrefixMapping}
     *
     * @return {@link CIMBaseUpdateBuilder this}
     */
    public CIMBaseUpdateBuilder addPrefixes(PrefixMapping prefixes) {
        baseUpdate.addPrefixes(prefixes.getNsPrefixMap());
        return this;
    }

    /**
     * Sets the graph the query should be executed on.
     *
     * @param graphURI the GraphUri or "default"/null
     *
     * @return {@link CIMBaseUpdateBuilder this}
     */
    public CIMBaseUpdateBuilder setGraph(String graphURI) {
        if (graphURI != null && !graphURI.equals("default")) {
            baseUpdate.with(NodeFactory.createURI(graphURI));
        }
        return this;
    }
}
