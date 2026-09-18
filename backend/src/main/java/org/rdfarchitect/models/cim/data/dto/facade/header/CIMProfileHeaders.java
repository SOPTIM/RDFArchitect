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
import de.soptim.opencgmes.cimxml.graph.CimProfile16;
import de.soptim.opencgmes.cimxml.graph.CimProfile17;

import lombok.experimental.UtilityClass;

import org.apache.jena.graph.Graph;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@UtilityClass
public class CIMProfileHeaders {

    private final List<Function<Graph, CimProfile>> HEADER_READERS =
            List.of(CimProfile17::new, CimProfile16::new);

    /**
     * Reads the header of a schema, trying the CGMES 3.0 format first and the CGMES 2.4.15 format
     * after it.
     *
     * @return the header, or a header that is not present when the graph holds none
     */
    public ICIMProfileHeader of(Graph graph) {
        return HEADER_READERS.stream()
                .map(reader -> read(reader, graph))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(MissingProfileHeader.INSTANCE);
    }

    private ICIMProfileHeader read(Function<Graph, CimProfile> reader, Graph graph) {
        try {
            return new CGMESProfileHeader(reader.apply(graph));
        } catch (RuntimeException _) {
            return null;
        }
    }
}
