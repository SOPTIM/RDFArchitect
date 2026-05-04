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

package org.rdfarchitect.api.dto;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ChangeLogEntryMapper {

    ChangeLogEntryMapper INSTANCE = Mappers.getMapper(ChangeLogEntryMapper.class);

    ChangeLogEntryDTO toDTO(ChangeLogEntry entry);

    List<ChangeLogEntryDTO> toDTOList(List<ChangeLogEntry> entries);

    default List<TripleDTO> mapTriples(WeakReference<Graph> graphReference) {
        var graph = graphReference.get();
        if (graph == null) {
            return new ArrayList<>();
        }
        return graph.stream()
                .filter(triple -> !triple.getPredicate().equals(RDFA.uuid.asNode()))
                .map(this::mapTriple)
                .toList();
    }

    default TripleDTO mapTriple(Triple triple) {
        if (triple == null) {
            return null;
        }
        return new TripleDTO(
                triple.getSubject().toString(),
                triple.getPredicate().toString(),
                triple.getObject().toString());
    }
}
