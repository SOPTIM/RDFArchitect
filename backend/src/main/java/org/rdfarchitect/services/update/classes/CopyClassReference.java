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

package org.rdfarchitect.services.update.classes;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CopyClassReference(
        GraphIdentifier sourceGraph,
        UUID uuid,
        URI uri,
        String label,
        Set<URI> dataTypeUris,
        Set<URI> superClassUris,
        Set<Kind> kinds,
        Map<Kind, Set<Usage>> usedBy) {

    public CopyClassReference(
            GraphIdentifier sourceGraph,
            UUID uuid,
            URI uri,
            String label,
            Set<URI> dataTypeUris,
            Set<URI> superClassUris,
            Set<Kind> kinds) {
        this(
                sourceGraph,
                uuid,
                uri,
                label,
                dataTypeUris,
                superClassUris,
                kinds,
                new EnumMap<>(Kind.class));
    }

    public CopyClassSource toSource() {
        return new CopyClassSource(sourceGraph, uuid);
    }

    public record Usage(String className, String memberName) {}

    @Getter
    @RequiredArgsConstructor
    public enum Kind {
        DATA_TYPE("data type"),
        ASSOCIATION_TARGET("association target"),
        SUPER_CLASS("super class");

        private final String displayName;

        public boolean copiesMembers() {
            return this == DATA_TYPE;
        }
    }
}
