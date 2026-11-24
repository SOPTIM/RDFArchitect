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

import java.util.List;

/**
 * Rules of one document that the comparison did not read, and why.
 *
 * <p>Reported so that silence is not mistaken for agreement: a constraint the schema implies that
 * only such a rule states reads as not covered, and what such a rule says is checked against
 * nothing.
 */
@Data
@Builder
public class ConformanceNotCompared {

    /** Why the rules were not compared. */
    public enum Reason {
        /** They constrain classes of a vocabulary the compared schema does not use. */
        OTHER_VOCABULARY,
        /** The shapes choose what they apply to other than by class. */
        OTHER_TARGET,
        /** The shapes combine rules with {@code sh:or}, {@code sh:xone} or {@code sh:not}. */
        COMBINATION
    }

    /** The document's name, as in a finding's {@code statedIn}. */
    private String document;

    private Reason reason;

    /**
     * How many: class and property constraints for {@link Reason#OTHER_VOCABULARY}, shapes
     * otherwise.
     */
    private int count;

    /** The namespaces left out, or the SHACL terms that keep the shapes from being read. */
    private List<String> terms;

    private String message;
}
