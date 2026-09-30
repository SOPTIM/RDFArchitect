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

package org.rdfarchitect.models.cim.data.dto.facade.header;

import de.soptim.opencgmes.cimxml.graph.CimProfile;

import org.apache.jena.graph.Node;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

class CGMESProfileHeader implements ICIMProfileHeader {

    private final CimProfile cimProfile;

    CGMESProfileHeader(CimProfile cimProfile) {
        this.cimProfile = cimProfile;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public String getKeyword() {
        return readOrNull(cimProfile::getDcatKeyword);
    }

    @Override
    public Set<String> getVersionIris() {
        var nodes = readOrNull(cimProfile::getOwlVersionIris);
        if (nodes == null) {
            return Set.of();
        }
        var versionIris = new TreeSet<String>();
        for (var node : nodes) {
            versionIris.add(asString(node).trim());
        }
        return versionIris;
    }

    @Override
    public String getVersionInfo() {
        return readOrNull(cimProfile::getOwlVersionInfo);
    }

    /**
     * Reads one header field, or null when the graph holds a header that the field does not apply
     * to. A file header profile for instance carries no owl:Ontology, and reading its version then
     * fails inside the CIM library.
     */
    private static <T> T readOrNull(Supplier<T> field) {
        try {
            return field.get();
        } catch (RuntimeException _) {
            return null;
        }
    }

    private static String asString(Node node) {
        if (node.isURI()) {
            return node.getURI();
        }
        if (node.isLiteral()) {
            return node.getLiteralLexicalForm();
        }
        return node.toString();
    }
}
