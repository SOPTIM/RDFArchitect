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

package org.rdfarchitect.shacl.dto;

import lombok.Builder;
import lombok.Data;

/**
 * One way in which a constraints document and the schema it is meant to describe disagree.
 *
 * <p>Reported per class and property rather than per shape, because generated and official shapes
 * are named nothing alike and one property's rules are spread over several shapes on both sides.
 */
@Data
@Builder
public class ConformanceFinding {

    /** What kind of disagreement this is, worst first. */
    public enum Kind {
        /** The two cannot both be satisfied. Schema and document have genuinely drifted apart. */
        CONTRADICTED,
        /** Both can hold, but they do not say the same thing — one is stricter than the other. */
        DIFFERENT,
        /** The schema implies a constraint the document does not state. */
        MISSING_IN_DOCUMENT,
        /** The document constrains something the schema does not have. */
        NOT_IN_SCHEMA
    }

    private Kind kind;

    private String targetClass;

    /** The property the disagreement is about. */
    private String path;

    /** What the schema implies, in words. Absent when the schema says nothing about it. */
    private String schemaSays;

    /** What the document asserts, in words. Absent when the document says nothing about it. */
    private String documentSays;

    private String message;
}
