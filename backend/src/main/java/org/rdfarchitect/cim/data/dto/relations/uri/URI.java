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

package org.rdfarchitect.cim.data.dto.relations.uri;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

@Data
@NoArgsConstructor
@JsonDeserialize(using = URIDeserializer.class)
@Schema(description = "When deserializing (parsing json to object), it is possible to alternatively only put a String containing the whole uri. For serializing (parsing object " +
          "to json), the object structure ist always used.")
public class URI {

    private String prefix;
    private String suffix;

    public URI(String uri) {
        if (uri == null) {
            return;
        }
        int splitIndex = uri.indexOf('#');
        if (splitIndex < 0) {
            splitIndex = uri.lastIndexOf('/');
        }
        if (splitIndex < 0) {
            prefix = null;
            suffix = uri;
            return;
        }
        prefix = uri.substring(0, splitIndex + 1);
        suffix = uri.substring(splitIndex + 1);
    }

    @Override
    public String toString() {
        return (prefix == null ? "" : prefix) + (suffix == null ? "" : suffix);
    }

    public Node toNode() {
        return NodeFactory.createURI(this.toString());
    }

    public boolean equals(Object obj) {
        if (obj instanceof URI other) {
            return this.toString().equals(other.toString());
        }
        return false;
    }

    public int hashCode() {
        return this.toString().hashCode();
    }
}
