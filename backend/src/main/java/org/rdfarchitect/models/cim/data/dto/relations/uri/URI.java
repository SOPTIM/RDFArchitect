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

package org.rdfarchitect.models.cim.data.dto.relations.uri;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.NoArgsConstructor;

import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.irix.IRIException;
import org.apache.jena.irix.IRIx;

@NoArgsConstructor
@JsonDeserialize(using = URIDeserializer.class)
@Schema(
        description =
                "When deserializing (parsing json to object), it is possible to alternatively only put a String containing the whole uri. For serializing (parsing object "
                        + "to json), the object structure ist always used.")
public class URI {

    @Getter private String prefix;
    @Getter private String suffix;

    public URI(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
        if (uri.isEmpty()) {
            throw new IllegalArgumentException("URI must not be empty");
        }
        if (uri.contains(" ")) {
            throw new IllegalArgumentException("URI must not contain spaces: " + uri);
        }
        try {
            IRIx iri = IRIx.create(StrUtils.encodeHex(uri, '%', new char[] {'%', ' '}));
            if (iri.scheme() == null) {
                throw new IllegalArgumentException("IRI must be absolute: " + uri);
            }
        } catch (IRIException e) {
            throw new IllegalArgumentException("Invalid IRI: " + uri, e);
        }
        int hash = uri.indexOf('#');
        int slash = uri.lastIndexOf('/');

        if (hash >= 0) {
            this.prefix = uri.substring(0, hash + 1);
            this.suffix = uri.substring(hash + 1);
        } else if (slash >= 0) {
            this.prefix = uri.substring(0, slash + 1);
            this.suffix = uri.substring(slash + 1);
        } else {
            this.prefix = uri;
            this.suffix = "";
        }
    }

    @Override
    public String toString() {
        return prefix + suffix;
    }

    public Node toNode() {
        return NodeFactory.createURI(this.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof URI other) {
            return this.toString().equals(other.toString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
}
